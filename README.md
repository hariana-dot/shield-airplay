# Shield AirPlay

[![Latest release](https://img.shields.io/github/v/release/ebreen/shield-airplay)](https://github.com/ebreen/shield-airplay/releases/latest)
[![GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

A free, open-source AirPlay receiver for **NVIDIA Shield and Android TV**. No ads, subscriptions, analytics, or paid feature gates.

## Download

**[Download the latest signed APK →](https://github.com/ebreen/shield-airplay/releases/latest)**

Release downloads include the APK, corresponding source, and SHA-256 checksums. Previous releases remain available for rollback.

## What it does

- **Cast video:** AirPlay URL/HLS playback with TV-remote pause, resume, and seeking.
- **Mirror your screen:** iPhone, iPad, and Mac screen mirroring with H.264/HEVC decoding.
- **Play music:** AAC and ALAC audio, artwork, metadata, and media-session controls.
- **Fit your TV:** D-pad navigation, clear focus indicators, Picture-in-Picture, and persistent light/dark themes.
- **Stay available:** optional PIN protection, boot startup, and background receiver operation.

### Latest update: 2.0.1

The receiver now sends playback-state changes back to the AirPlay sender instead of relying only on sender polling. TV-remote long-pause/resume was manually confirmed on NVIDIA Shield after installing this update. See the [release notes](https://github.com/ebreen/shield-airplay/releases/tag/v2.0.1-shield) for scope and verification details.

## Install

1. Download `Shield-AirPlay-2.0.1-shield.apk` from Releases and sideload it with a file manager, or use ADB:

   ```bash
   adb install -r Shield-AirPlay-2.0.1-shield.apk
   ```

2. Open **Shield AirPlay**. Allow **Display over other apps** if you want incoming casts to open automatically.
3. Keep the sender and Shield on the same LAN. Use the sender app's **AirPlay** button for video/audio, or **Control Center → Screen Mirroring → Shield AirPlay** for mirroring.

The package is `no.gior.shieldairplay`. Updates signed with the same release key preserve app data. Tailscale can carry ADB installation traffic, but it does not provide ordinary AirPlay multicast discovery.

## Limits

DRM-protected video, including protected Apple TV content, and AirPlay 2 multi-room audio are not supported. Network and sender compatibility can vary.

UxPlay includes a reverse-engineered FairPlay compatibility component whose upstream project describes its legal status as unclear. Review the laws that apply where you use or distribute it.

## Build and contribute

```bash
git clone --recurse-submodules https://github.com/ebreen/shield-airplay.git
cd shield-airplay
```

- **[Build guide](SHIELD_BUILD.md):** pinned toolchain, signing, and separate build/validation commands.
- **[Development guide](AGENTS.md):** architecture, privacy rules, lifecycle behaviour, and device acceptance.
- **[CI status](https://github.com/ebreen/shield-airplay/actions/workflows/ci.yml):** automation results, separate from release and manual-device verification.

Keep APKs, build caches, signing keys, local configuration, and device captures out of Git. Distributable binaries belong in Releases. Native builds apply the tracked UxPlay patch stack inside its submodule; preserve unrelated edits when cleaning that generated state.

## Licence and credits

Licensed under [GPLv3](LICENSE). Corresponding source and upstream attribution must remain available when distributing modified builds.

Based on [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server), with work from [UxPlay](https://github.com/FDH2/UxPlay), [FFmpeg](https://ffmpeg.org), [libplist](https://github.com/libimobiledevice/libplist), and [Next Player](https://github.com/anilbeesetti/nextplayer).

Not affiliated with Apple Inc. Product names and trademarks belong to their respective owners.
