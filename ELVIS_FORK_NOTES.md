# Elvis status-UI fork (2.0.1-elvis3)

## Version
- applicationId: `no.gior.shieldairplay` (unchanged)
- versionName: `2.0.1-elvis3`
- versionCode: `204`

## Signing
Release APK signed with a **private local keystore** created for this fork:
- Keystore (box): `/home/box/.android-signing/elvis-shield.jks`
- Alias: `elvis` / store+key password: `elvis-local` (dev-only; not for public distribution)
- `local.properties` (gitignored) points `storeFile=/signing/elvis-shield.jks` for the Docker builder mount

elvis3 is signed with the **same** keystore as elvis1/elvis2, so `adb install -r` should update in place without uninstall.

## APK paths
- Box: `/workspace/shield-airplay/Shield-AirPlay-2.0.1-elvis3.apk`
- Box (gradle): `/workspace/shield-airplay/app/build/outputs/apk/release/app-release.apk`
- PC: `C:\Users\Elvis Cheung\bravia-airplay\Shield-AirPlay-2.0.1-elvis3.apk`
- ABIs: `arm64-v8a` + `armeabi-v7a`

## Fix: connect-then-drop from 2s stale TCP watchdog (elvis2)
ADB showed `Client connected (1)` then ~2.003s later `Resetting stale connection count (1 → 0)` and a reconnect. Root cause: `ReceiverStatusPolicy.STALE_CONNECTION_MS = 2000` cleared `_connectionCount` while the sender handshake window (`SENDER_HANDSHAKE_MS = 8000`) was still open — watchdog ignored `handshakeUntilMs`.

Policy change:
- `shouldResetStaleConnectionCount` now refuses to reset while handshake is still open, while media/pending-video is active, or while buffering.
- Watchdog schedule uses `staleCheckDelayMs` so the first check waits until the handshake window ends (not a blind 2s).
- After a real destroy/idle with no open handshake, sticky "connected" still clears within ~2s (`STALE_CONNECTION_MS` unchanged).
- `TV_STOP_DISCONNECTS` default left unchanged.

## Fix: phone stop left last mirror frame frozen (elvis3)
When iPhone stops Screen Mirroring, native fires `video_flush` / `Client disconnected` (often with leftover TCP so count stays > 0). TV Stop already forced `connectionCount=0` so fullscreen exited; phone-side stop did not.

Code path that clears the frame:
1. `onMirrorRunning(false)` → `_endMirrorPresentation()` → `VideoRenderer.stopSession()` → `VideoPipeline.clearDisplay()` (black GL clear + swap, drops `hasFrame`)
2. `_onConnectionDestroy` when count hits 0 also calls `_endMirrorPresentation()` (covers teardown without a clean mirror-running callback)
3. `_disconnectSenderAfterLocalStop` (TV Stop) uses the same blanking helper
4. UI: fullscreen exits as soon as mirroring becomes inactive (not only when `connections == 0`), so Ready hero returns even with sticky TCP
5. Mirror stop no longer forces `audioOnly=true` (that kept "media" UI until force-stop); pure audio still sets audio-only via `onAudioFormat(usingScreen=false)`

## BRAVIA (192.168.133.29:5555)
- elvis1: uninstalled old package (signature mismatch), installed with `-g`; port 7000 + mDNS OK
- elvis2: `adb install -r` over elvis1 (same signature)
- elvis3: `adb install -r` over elvis2 (same signature)
