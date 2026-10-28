package io.github.jqssun.airplay.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.service.ReceiverStatusPolicy
import android.os.SystemClock
import io.github.jqssun.airplay.ui.gestures.BrightnessState
import io.github.jqssun.airplay.ui.gestures.DoubleTapIndicator
import io.github.jqssun.airplay.ui.gestures.GestureInfoText
import io.github.jqssun.airplay.ui.gestures.SeekGestureState
import io.github.jqssun.airplay.ui.gestures.TapGestureState
import io.github.jqssun.airplay.ui.gestures.VerticalGesture
import io.github.jqssun.airplay.ui.gestures.VideoContentScale
import io.github.jqssun.airplay.ui.gestures.VerticalProgressIndicator
import io.github.jqssun.airplay.ui.gestures.VideoPlayerGestures
import io.github.jqssun.airplay.ui.gestures.VolumeAndBrightnessGestureState
import io.github.jqssun.airplay.ui.gestures.VolumeState
import io.github.jqssun.airplay.ui.gestures.ZoomState
import io.github.jqssun.airplay.viewmodel.DebugInfo
import io.github.jqssun.airplay.viewmodel.MainViewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.ui.compose.material3.MiniController
import androidx.media3.ui.compose.material3.buttons.NextButton
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.PreviousButton
import androidx.media3.ui.compose.material3.indicator.DurationText
import androidx.media3.ui.compose.material3.indicator.PositionText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import kotlin.math.abs
import kotlinx.coroutines.delay

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Default.Cast),
    LOGS(R.string.tab_logs, Icons.AutoMirrored.Filled.Article),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isInPip: Boolean = false,
    onSurfaceAvailable: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: (android.view.Surface) -> Unit,
    onPip: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(Tab.OVERVIEW) }
    var fullscreen by remember { mutableStateOf(false) }
    val pin by viewModel.pinCode.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val audioOnly by viewModel.audioOnly.collectAsState()
    val videoPlaybackActive by viewModel.videoPlaybackActive.collectAsState()
    val videoSessionPending by viewModel.videoSessionPending.collectAsState()
    val mirroringActive by viewModel.mirroringActive.collectAsState()
    val autoFullscreen by viewModel.autoFullscreen.collectAsState()

    // don't use movableContentOf: moving AndroidView across subcomposition boundaries makes it crash on reparent
    val video: @Composable () -> Unit = {
        val aspect by viewModel.videoAspect.collectAsState()
        VideoSurfaceView(
            onSurfaceAvailable = onSurfaceAvailable,
            onSurfaceDestroyed = onSurfaceDestroyed,
            aspectRatio = aspect
        )
    }

    // fullscreen only once mirroring reports a size: connections rise before the kind is known
    var prevMirroringActive by remember { mutableStateOf(false) }
    LaunchedEffect(mirroringActive, audioOnly, videoPlaybackActive, pin) {
        val justStarted = !prevMirroringActive && mirroringActive
        val justStopped = prevMirroringActive && !mirroringActive
        prevMirroringActive = mirroringActive
        if (justStarted && !audioOnly && !videoPlaybackActive && autoFullscreen && pin == null) {
            fullscreen = true
        }
        // Phone stop clears mirroring while leftover TCP may keep connections > 0.
        if (justStopped) fullscreen = false
    }

    // manual fullscreen over the idle preview is deliberate; only undo on disconnect
    LaunchedEffect(connections) {
        if (connections == 0) fullscreen = false
    }

    // leaving video playback must not fall through to a stale mirroring fullscreen
    LaunchedEffect(videoPlaybackActive) {
        if (videoPlaybackActive) fullscreen = false
    }

    val activity = LocalContext.current as? Activity
    val videoScreen = videoPlaybackActive || videoSessionPending
    LaunchedEffect(fullscreen, videoScreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen || videoScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (videoScreen) {
        val videoPlaybackAspect by viewModel.videoPlaybackAspect.collectAsState()
        val playback: @Composable () -> Unit = {
            VideoSurfaceView(
                onSurfaceAvailable = { viewModel.onVideoPlaybackSurfaceAvailable(it) },
                onSurfaceDestroyed = { viewModel.onVideoPlaybackSurfaceDestroyed(it) },
                aspectRatio = videoPlaybackAspect
            )
        }
        if (isInPip) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                playback()
            }
            return
        }
        val overlayTick by viewModel.videoOverlayTick.collectAsState()
        val playing by viewModel.videoPlaying.collectAsState()
        val positionMs by viewModel.videoPositionMs.collectAsState()
        val durationMs by viewModel.videoDurationMs.collectAsState()
        val scrubPositionMs by viewModel.videoScrubPositionMs.collectAsState()
        val downloadProgress by viewModel.videoDownloadProgress.collectAsState()
        val scrubbing = scrubPositionMs != null
        val downloading = downloadProgress != null
        var overlayVisible by remember { mutableStateOf(false) }
        LaunchedEffect(overlayTick, playing, scrubbing, downloading, videoPlaybackActive) {
            // tick 0 = fresh session; a pending session or a running download pins the overlay
            if (!videoPlaybackActive) {
                overlayVisible = true
            } else if (overlayTick == 0L) {
                overlayVisible = false
            } else {
                overlayVisible = true
                if (playing && !scrubbing && !downloading) {
                    delay(VIDEO_OVERLAY_HIDE_MS)
                    overlayVisible = false
                }
            }
        }
        val gestureScope = rememberCoroutineScope()
        val tapGestureState = remember(viewModel) { TapGestureState(viewModel, gestureScope) }
        val seekGestureState = remember(viewModel) { SeekGestureState(viewModel) }
        val context = LocalContext.current
        val volumeState = remember { VolumeState(context) }
        val brightnessState = remember(activity) { activity?.window?.let { BrightnessState(it) } }
        val volumeAndBrightnessGestureState = remember(volumeState, brightnessState) {
            VolumeAndBrightnessGestureState(volumeState, brightnessState, gestureScope)
        }
        val zoomState = remember(viewModel) { ZoomState(viewModel, gestureScope) }
        var controlsLocked by remember { mutableStateOf(false) }
        DisposableEffect(volumeState) { volumeState.handleLifecycle(this) }
        LaunchedEffect(overlayTick) {
            if (overlayTick == 0L) {
                zoomState.reset()
                controlsLocked = false
            }
        }
        DisposableEffect(brightnessState) {
            onDispose { brightnessState?.clearOverride() }
        }
        // orientation follows the video aspect; manual rotate holds until the next video
        LaunchedEffect(videoPlaybackAspect) {
            activity?.requestedOrientation = if (videoPlaybackAspect < 1f) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        DisposableEffect(activity) {
            onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
        val videoPlaybackSize by viewModel.videoPlaybackSize.collectAsState()
        val buffering by viewModel.videoBuffering.collectAsState()
        val videoTitle by viewModel.videoTitle.collectAsState()
        val videoLocation by viewModel.videoLocation.collectAsState()
        val speed by viewModel.videoSpeed.collectAsState()
        val isPipSupported = remember {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }
        var showSpeedSelector by remember { mutableStateOf(false) }
        BackHandler { viewModel.stopVideoPlayback() }

        val rootFocusRequester = remember { FocusRequester() }
        val playPauseFocusRequester = remember { FocusRequester() }
        val unlockFocusRequester = remember { FocusRequester() }
        var isPlayPauseFocused by remember { mutableStateOf(false) }
        var isUnlockFocused by remember { mutableStateOf(false) }
        LaunchedEffect(overlayVisible, controlsLocked, showSpeedSelector) {
            if (showSpeedSelector) return@LaunchedEffect
            if (!overlayVisible) {
                runCatching { rootFocusRequester.requestFocus() }
                return@LaunchedEffect
            }
            val locked = controlsLocked
            val target = if (locked) unlockFocusRequester else playPauseFocusRequester
            target.requestFocusUntilLanded(attempts = 20) { if (locked) isUnlockFocused else isPlayPauseFocused }
        }

        // dpad seeking (controls hidden): accumulate the skipped amount and briefly show it
        var dpadSeekOffsetMs by remember { mutableLongStateOf(0L) }
        var dpadSeekTargetMs by remember { mutableLongStateOf(0L) }
        var dpadSeekActive by remember { mutableStateOf(false) }
        var dpadSeekTick by remember { mutableIntStateOf(0) }
        LaunchedEffect(dpadSeekTick) {
            if (!dpadSeekActive) return@LaunchedEffect
            delay(1000)
            dpadSeekActive = false
        }
        val showDpadSeekFeedback: (Long) -> Unit = { deltaMs ->
            if (!dpadSeekActive) dpadSeekOffsetMs = 0L
            dpadSeekOffsetMs += deltaMs
            dpadSeekTargetMs = (dpadSeekTargetMs.takeIf { dpadSeekActive } ?: positionMs).plus(deltaMs)
                .coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
            dpadSeekActive = true
            dpadSeekTick++
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (showSpeedSelector) {
                        false
                    } else {
                        handlePlayerKeyEvent(
                            keyEvent = keyEvent,
                            controlsVisible = overlayVisible,
                            controlsLocked = controlsLocked,
                            isPlayPauseFocused = isPlayPauseFocused,
                            seekIncrementMs = TapGestureState.SEEK_INCREMENT_MS,
                            viewModel = viewModel,
                            showControls = { viewModel.showVideoOverlay() },
                            unlockControls = {
                                controlsLocked = false
                                viewModel.showVideoOverlay()
                            },
                            onDpadSeek = showDpadSeekFeedback,
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            VideoContentFrame(
                aspect = videoPlaybackAspect,
                videoSizePx = videoPlaybackSize,
                contentScale = zoomState.contentScale,
                zoom = zoomState.zoom
            ) { sizeModifier ->
                VideoSurfaceView(
                    onSurfaceAvailable = { viewModel.onVideoPlaybackSurfaceAvailable(it) },
                    onSurfaceDestroyed = { viewModel.onVideoPlaybackSurfaceDestroyed(it) },
                    applyAspectRatio = false,
                    modifier = sizeModifier
                )
            }
            VideoPlayerGestures(
                enabled = videoPlaybackActive,
                locked = controlsLocked,
                onTap = {
                    // a pending session pins the overlay; taps must not unpin it
                    if (!videoPlaybackActive) return@VideoPlayerGestures
                    if (overlayVisible) overlayVisible = false else viewModel.showVideoOverlay()
                },
                tapGestureState = tapGestureState,
                seekGestureState = seekGestureState,
                volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
                zoomState = zoomState
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = overlayVisible && videoPlaybackActive && !controlsLocked,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.7f),
                            0.3f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        )
                    )
                )
            }
            if (buffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(72.dp))
            }
            DoubleTapIndicator(tapGestureState = tapGestureState)
            val seekAmountMs = seekGestureState.seekAmountMs
            when {
                seekAmountMs != null -> GestureInfoText(
                    info = "${if (seekAmountMs < 0) "-" else "+"}${formatVideoTime(abs(seekAmountMs))}\n" +
                        "[${formatVideoTime(seekGestureState.targetPositionMs ?: 0)}]",
                    modifier = Modifier.align(Alignment.Center)
                )
                zoomState.isZooming -> GestureInfoText(
                    info = "${(zoomState.zoom * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.Center)
                )
                zoomState.showContentScaleIndicator -> GestureInfoText(
                    info = stringResource(zoomState.contentScale.nameRes()),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> androidx.compose.animation.AnimatedVisibility(
                    visible = overlayVisible && videoPlaybackActive && !controlsLocked,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    PlayPauseButton(
                        playing = playing,
                        onClick = { viewModel.toggleVideoPlayPause() },
                        modifier = Modifier
                            .focusRequester(playPauseFocusRequester)
                            .onFocusChanged { isPlayPauseFocused = it.hasFocus }
                    )
                }
            }
            DpadSeekIndicator(
                visible = dpadSeekActive && dpadSeekOffsetMs != 0L,
                offsetMs = dpadSeekOffsetMs,
                positionMs = dpadSeekTargetMs
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(24.dp)
            ) {
                VerticalProgressIndicator(value = volumeState.percentage, icon = Icons.AutoMirrored.Rounded.VolumeUp)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp)
            ) {
                VerticalProgressIndicator(value = brightnessState?.percentage ?: 0, icon = Icons.Rounded.BrightnessHigh)
            }
            if (controlsLocked) {
                if (overlayVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(horizontal = if (isTv()) 48.dp else 0.dp)
                            .padding(top = if (isTv()) 27.dp else 24.dp)
                    ) {
                        UnlockButton(
                            onClick = {
                                controlsLocked = false
                                viewModel.showVideoOverlay()
                            },
                            modifier = Modifier
                                .focusRequester(unlockFocusRequester)
                                .onFocusChanged { isUnlockFocused = it.hasFocus }
                        )
                    }
                }
            } else {
                androidx.compose.animation.AnimatedVisibility(
                    visible = overlayVisible,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    VideoControlsTop(
                        title = videoTitle,
                        videoUrl = videoLocation,
                        downloadProgress = downloadProgress,
                        showDownload = durationMs > 0,
                        onBackClick = { viewModel.stopVideoPlayback() },
                        onSpeedClick = {
                            overlayVisible = false
                            showSpeedSelector = true
                        },
                        onDownloadClick = { viewModel.toggleVideoDownload() }
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = overlayVisible,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    VideoControlsBottom(
                        positionMs = scrubPositionMs ?: positionMs,
                        durationMs = durationMs,
                        contentScale = zoomState.contentScale,
                        isPipSupported = isPipSupported,
                        onRotateClick = {
                            activity?.let {
                                it.requestedOrientation =
                                    if (it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                            }
                        },
                        onLockClick = {
                            viewModel.showVideoOverlay()
                            controlsLocked = true
                        },
                        onContentScaleClick = {
                            viewModel.showVideoOverlay()
                            zoomState.switchToNextContentScale()
                        },
                        onPipClick = onPip,
                        onSeek = { seekGestureState.onSeek(it) },
                        onSeekEnd = { seekGestureState.onSeekEnd() },
                        seekBarModifier = Modifier
                            .focusProperties { up = playPauseFocusRequester }
                            .dpadAdjust(
                                onLeft = {
                                    viewModel.seekVideoBy(-TapGestureState.SEEK_INCREMENT_MS)
                                    viewModel.showVideoOverlay()
                                },
                                onRight = {
                                    viewModel.seekVideoBy(TapGestureState.SEEK_INCREMENT_MS)
                                    viewModel.showVideoOverlay()
                                }
                            )
                    )
                }
            }
            val skipSilence by viewModel.videoSkipSilence.collectAsState()
            PlaybackSpeedSelector(
                show = showSpeedSelector,
                speed = speed,
                skipSilence = skipSilence,
                onSpeedChange = { viewModel.setVideoSpeed(it) },
                onSkipSilenceChange = { viewModel.setVideoSkipSilence(it) },
                onDismiss = { showSpeedSelector = false }
            )
        }
        return
    }

    // pip mode: show only the video surface
    if (isInPip) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            video()
        }
        return
    }

    // restore system bars when exiting pip back to non-fullscreen
    LaunchedEffect(isInPip) {
        if (!isInPip && !fullscreen) {
            val window = activity?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // exit fullscreen while a pin is being shown so the dialog isn't covered
    LaunchedEffect(pin) {
        if (pin != null) fullscreen = false
    }

    if (fullscreen) {
        BackHandler { fullscreen = false }
        FullscreenVideo(
            viewModel = viewModel,
            video = video,
            onExitFullscreen = { fullscreen = false },
            onPip = onPip
        )
    } else {
        val tv = isTv()
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!tv) {
                    Column {
                        AudioMiniController(
                            viewModel,
                            visible = audioOnly && connections > 0 && tab != Tab.OVERVIEW,
                            onClick = { tab = Tab.OVERVIEW }
                        )
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(stringResource(t.labelRes)) },
                                    modifier = Modifier.dpadFocus()
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = if (tv) 48.dp else 0.dp, vertical = if (tv) 27.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(if (tv) 24.dp else 0.dp),
            ) {
                if (tv) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight().width(104.dp).clip(MaterialTheme.shapes.large),
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        header = {
                            Icon(
                                Icons.Default.Cast, stringResource(R.string.app_name),
                                Modifier.padding(vertical = 20.dp).size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.labelRes)) },
                                modifier = Modifier.padding(vertical = 6.dp).dpadFocus(),
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.weight(1f)) {
                        TabContent(
                            tab, viewModel, video,
                            onFullscreen = { fullscreen = true }, onPip = onPip, showAudioMode = audioOnly
                        )
                    }
                    if (tv) {
                        AudioMiniController(
                            viewModel,
                            visible = audioOnly && connections > 0 && tab != Tab.OVERVIEW,
                            onClick = { tab = Tab.OVERVIEW }
                        )
                    }
                }
            }
        }
    }

    // pin dialog
    if (pin != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPin() },
            title = { Text(stringResource(R.string.dialog_pin_title)) },
            text = {
                Text(
                    text = pin!!,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPin() }) { Text(stringResource(R.string.btn_ok)) }
            }
        )
    }

}

@Composable
private fun TabContent(
    tab: Tab,
    viewModel: MainViewModel,
    video: @Composable () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
    showAudioMode: Boolean
) {
    when (tab) {
        Tab.OVERVIEW -> OverviewContent(
            viewModel, video,
            onFullscreen = onFullscreen, onPip = onPip, showAudioMode = showAudioMode
        )
        Tab.LOGS -> LogsScreen(viewModel)
        Tab.SETTINGS -> SettingsScreen(viewModel)
    }
}

@Composable
private fun OverviewContent(
    viewModel: MainViewModel,
    video: @Composable () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
    showAudioMode: Boolean = false
) {
    val state by viewModel.serverState.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val serverName by viewModel.serverName.collectAsState()
    val videoResolution by viewModel.videoResolution.collectAsState()
    val idlePreview by viewModel.idlePreview.collectAsState()
    val mirroringActive by viewModel.mirroringActive.collectAsState()
    val videoPlaybackActive by viewModel.videoPlaybackActive.collectAsState()
    val audioOnlyOverview by viewModel.audioOnly.collectAsState()
    val videoSessionPending by viewModel.videoSessionPending.collectAsState()
    val videoBuffering by viewModel.videoBuffering.collectAsState()
    val senderHandshakeUntilMs by viewModel.senderHandshakeUntilMs.collectAsState()
    val debugEnabled by viewModel.debugEnabled.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(senderHandshakeUntilMs, mirroringActive, videoPlaybackActive, audioOnlyOverview, videoSessionPending) {
        while (true) {
            nowMs = SystemClock.elapsedRealtime()
            val until = senderHandshakeUntilMs
            if (until <= 0L || nowMs >= until) break
            delay(minOf(500L, (until - nowMs).coerceAtLeast(50L)))
        }
        nowMs = SystemClock.elapsedRealtime()
    }
    val statusLights = ReceiverStatusPolicy.deriveLights(
        serverRunning = state == ServerState.RUNNING,
        mirroringActive = mirroringActive,
        videoPlaybackActive = videoPlaybackActive,
        audioOnly = audioOnlyOverview,
        videoSessionPending = videoSessionPending,
        videoBuffering = videoBuffering,
        handshakeUntilMs = senderHandshakeUntilMs.takeIf { it > 0L },
        nowMs = nowMs,
    )
    val centreCopy = ReceiverStatusPolicy.deriveCentreCopy(
        serverRunning = state == ServerState.RUNNING,
        mirroringActive = mirroringActive,
        videoPlaybackActive = videoPlaybackActive,
        audioOnly = audioOnlyOverview,
        videoSessionPending = videoSessionPending,
        videoBuffering = videoBuffering,
        handshakeUntilMs = senderHandshakeUntilMs.takeIf { it > 0L },
        nowMs = nowMs,
    )
    val tv = isTv()
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (tv) startFocus.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(if (tv) 0.dp else 16.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (showAudioMode && state == ServerState.RUNNING &&
                (audioOnlyOverview || statusLights.senderOn)
            ) {
                NowPlayingContent(viewModel)
            } else {
                if (state == ServerState.RUNNING && (mirroringActive || idlePreview)) {
                    video()
                }
                when (centreCopy) {
                    ReceiverStatusPolicy.CentreCopy.HERO ->
                        ReceiverHero(name = serverName, state = state)
                    ReceiverStatusPolicy.CentreCopy.CONNECTING ->
                        Text(
                            text = stringResource(
                                if (videoBuffering || videoSessionPending) R.string.status_waiting_for_video
                                else R.string.status_connecting
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    ReceiverStatusPolicy.CentreCopy.MEDIA -> Unit
                }
                if (state == ServerState.RUNNING && mirroringActive) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape).padding(4.dp)
                    ) {
                        IconButton(onClick = onPip, modifier = Modifier.dpadFocus()) {
                            Icon(
                                painterResource(R.drawable.ic_pip), contentDescription = stringResource(R.string.cd_pip),
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onFullscreen, modifier = Modifier.dpadFocus()) {
                            Icon(
                                Icons.Default.Fullscreen, contentDescription = stringResource(R.string.cd_fullscreen),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            if (debugEnabled && connections > 0) {
                DebugOverlay(debugInfo, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            var showRes by remember { mutableStateOf(false) }
            LaunchedEffect(videoResolution) {
                if (videoResolution.isNotEmpty() && !showAudioMode) {
                    showRes = true
                    delay(5000)
                    showRes = false
                }
            }
            if (!showAudioMode) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showRes && connections > 0,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = videoResolution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Keep the server action outside the scrolling content and its initial TV focus.
        Card(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = if (tv) 0.dp else 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = serverName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ReceiverStatusLights(statusLights)
                }
                Button(
                    onClick = {
                        if (state == ServerState.RUNNING) viewModel.stopServer()
                        else viewModel.startServer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state == ServerState.RUNNING) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primary,
                        contentColor = if (state == ServerState.RUNNING) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.heightIn(min = 52.dp).dpadFocus().focusRequester(startFocus)
                ) {
                    Icon(
                        imageVector = if (state == ServerState.RUNNING) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state == ServerState.RUNNING) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start))
                }
            }
        }
    }
}

@Composable
private fun FullscreenVideo(
    viewModel: MainViewModel,
    video: @Composable () -> Unit,
    onExitFullscreen: () -> Unit,
    onPip: () -> Unit
) {
    val videoResolution by viewModel.videoResolution.collectAsState()
    val debugEnabled by viewModel.debugEnabled.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()

    val tv = isTv()
    val rootFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }
    var isRootFocused by remember { mutableStateOf(false) }
    var isExitFocused by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var tapTick by remember { mutableStateOf(0) }
    LaunchedEffect(tapTick) {
        controlsVisible = true
        delay(8000)
        controlsVisible = false
    }
    LaunchedEffect(tv, controlsVisible) {
        if (!tv) return@LaunchedEffect
        if (controlsVisible) {
            exitFocusRequester.requestFocusUntilLanded { isExitFocused }
        } else {
            rootFocusRequester.requestFocusUntilLanded { isRootFocused }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onFocusChanged { isRootFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val navigationKey = when (event.key) {
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> true
                    else -> false
                }
                if (!tv || !navigationKey) {
                    false
                } else if (!controlsVisible) {
                    // Consume the wake-up press through release so it cannot activate Exit/PIP.
                    if (event.type == KeyEventType.KeyUp) tapTick++
                    true
                } else {
                    if (event.type == KeyEventType.KeyDown) tapTick++
                    false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { tapTick++ } },
        contentAlignment = Alignment.Center
    ) {
        video()
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = if (isTv()) 48.dp else 8.dp, vertical = if (isTv()) 27.dp else 8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape).padding(4.dp)
            ) {
                IconButton(onClick = onPip, modifier = Modifier.dpadFocus()) {
                    Icon(
                        painterResource(R.drawable.ic_pip), contentDescription = stringResource(R.string.cd_pip),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
                IconButton(
                    onClick = onExitFullscreen,
                    modifier = Modifier
                        .focusRequester(exitFocusRequester)
                        .onFocusChanged { isExitFocused = it.isFocused }
                        .dpadFocus(),
                ) {
                    Icon(
                        Icons.Default.FullscreenExit, contentDescription = stringResource(R.string.cd_exit_fullscreen),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        if (debugEnabled) {
            DebugOverlay(
                debugInfo,
                Modifier.align(Alignment.TopStart)
                    .padding(horizontal = if (isTv()) 48.dp else 8.dp, vertical = if (isTv()) 27.dp else 8.dp),
            )
        }
        var showRes by remember { mutableStateOf(false) }
        LaunchedEffect(videoResolution) {
            if (videoResolution.isNotEmpty()) {
                showRes = true
                delay(5000)
                showRes = false
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showRes,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(horizontal = if (isTv()) 48.dp else 8.dp, vertical = if (isTv()) 27.dp else 8.dp)
        ) {
            Text(
                text = videoResolution,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// dacp scanning is press-and-hold: beginff/beginrew while held, playresume on release
@Composable
private fun HoldScanButton(
    icon: ImageVector,
    contentDescription: String,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remoteHeld by remember { mutableStateOf(false) }
    val endScan by rememberUpdatedState(onEnd)
    DisposableEffect(Unit) {
        onDispose { if (remoteHeld) endScan() }
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .dpadFocus(CircleShape)
            .onFocusChanged {
                if (!it.isFocused && remoteHeld) {
                    remoteHeld = false
                    endScan()
                }
            }
            .onKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown && !remoteHeld) {
                        remoteHeld = true
                        onBegin()
                    } else if (event.type == KeyEventType.KeyUp && remoteHeld) {
                        remoteHeld = false
                        endScan()
                    }
                    true
                }
            }
            .focusable()
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onBegin()
                    try {
                        awaitRelease()
                    } finally {
                        onEnd()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription)
    }
}

@Composable
@AndroidxOptIn(UnstableApi::class)
private fun AudioMiniController(viewModel: MainViewModel, visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    val player = viewModel.dacpPlayer ?: return
    val context = LocalContext.current
    MiniController(
        player,
        bitmapLoader = remember { DataSourceBitmapLoader(context) },
        onClick = onClick
    )
}

@Composable
@AndroidxOptIn(UnstableApi::class)
private fun NowPlayingContent(viewModel: MainViewModel) {
    val track by viewModel.trackInfo.collectAsState()

    val tv = isTv()
    val surface = MaterialTheme.colorScheme.surface
    val artworkColor = remember(track.coverArt) { track.coverArt?.let(::artworkAverageColor) }
    val backdrop = artworkColor?.let { androidx.compose.ui.graphics.lerp(surface, it, 0.22f) } ?: surface
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(backdrop, surface)))
            .padding(if (tv) 24.dp else 20.dp)
    ) {
        val wide = maxWidth >= 560.dp
        val artSize = if (wide) minOf(maxWidth * 0.36f, maxHeight, 300.dp)
            else minOf(maxWidth * 0.65f, maxHeight * 0.38f, 240.dp)
        val artwork: @Composable () -> Unit = {
            Box(
                Modifier.size(artSize).clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (track.coverArt != null) {
                    Image(
                        bitmap = track.coverArt!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_cover_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote, null, Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        val details: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.now_playing_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = track.title.ifEmpty { stringResource(R.string.unknown_track) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (wide) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.artist.isNotEmpty()) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track.album.isNotEmpty()) {
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val player = viewModel.dacpPlayer
                if (player != null) {
                    // Keep Media3 progress and transport callbacks intact.
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PositionText(player, Modifier.padding(end = 8.dp))
                        Box(modifier = Modifier.weight(1f)) { ProgressSlider(player, modifier = Modifier.dpadFocus()) }
                        DurationText(player, Modifier.padding(start = 8.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        HoldScanButton(
                            icon = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.cd_rewind),
                            onBegin = { viewModel.audioScanBegin(false) },
                            onEnd = { viewModel.audioScanEnd() }
                        )
                        PreviousButton(player, modifier = Modifier.dpadFocus())
                        PlayPauseButton(
                            player,
                            modifier = Modifier.size(64.dp).dpadFocus(CircleShape),
                            iconSize = 40.dp
                        )
                        NextButton(player, modifier = Modifier.dpadFocus())
                        HoldScanButton(
                            icon = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.cd_fast_forward),
                            onBegin = { viewModel.audioScanBegin(true) },
                            onEnd = { viewModel.audioScanEnd() }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { viewModel.audioVolumeDown() }, modifier = Modifier.dpadFocus()) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeDown, stringResource(R.string.cd_volume_down))
                        }
                        IconButton(onClick = { viewModel.audioMuteToggle() }, modifier = Modifier.dpadFocus()) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.cd_mute))
                        }
                        IconButton(onClick = { viewModel.audioVolumeUp() }, modifier = Modifier.dpadFocus()) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.cd_volume_up))
                        }
                    }
                }
            }
        }
        if (wide) {
            Row(
                Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork()
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(4.dp)) { details() }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                artwork()
                details()
            }
        }
    }
}

private const val VIDEO_OVERLAY_HIDE_MS = 4000L

@Composable
private fun DebugOverlay(info: DebugInfo, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val style = MaterialTheme.typography.labelSmall
        val headingStyle = style.copy(fontWeight = FontWeight.Bold)
        val color = Color.White.copy(alpha = 0.9f)
        val headingColor = Color(0xFF80D8FF) // cyan
        val context = LocalContext.current
        debugOverlaySections(context, info).forEachIndexed { i, section ->
            if (i > 0) Spacer(Modifier.height(6.dp))
            Text(section.title.uppercase(), style = headingStyle, color = headingColor)
            section.lines.forEach { Text(it, style = style, color = color) }
        }
    }
}

private data class DebugSection(val title: String, val lines: List<String>)

private fun debugOverlaySections(context: Context, info: DebugInfo): List<DebugSection> = buildList {
    buildList {
        if (info.videoCodec.isNotEmpty()) {
            add(context.getString(R.string.debug_video, info.videoCodec, info.videoRes))
            add(context.getString(R.string.debug_fps_bitrate, info.videoFps, info.bitrateStr))
            add(context.getString(R.string.debug_frames_drops, info.videoFrames, info.droppedFrames))
            add(context.getString(R.string.debug_jitter, info.jitterStr))
        }
    }.takeIf { it.isNotEmpty() }?.let { add(DebugSection(context.getString(R.string.debug_section_video), it)) }

    buildList {
        if (info.audioCodec.isNotEmpty()) add(context.getString(R.string.debug_audio, info.audioCodec, info.audioVolume))
        info.audio?.let { a ->
            add(context.getString(R.string.debug_audio_buffer, a.backlogMs, a.tunedCushionMs))
            add(context.getString(R.string.debug_audio_glitch, a.trims, a.drops, a.silences, a.underruns, a.xrun))
            add(context.getString(R.string.debug_audio_decode, formatDecode(a.decodeMeanUs, a.decodeMaxUs, a.decodeHeld, a.decodeErrors)))
        }
    }.takeIf { it.isNotEmpty() }?.let { add(DebugSection(context.getString(R.string.debug_section_audio), it)) }

    add(DebugSection(context.getString(R.string.debug_section_network), listOf(context.getString(R.string.debug_clients, info.connections))))
}

// "mean/max ms held=N", or "held=N" before first latency window lands
private fun formatDecode(meanUs: Int, maxUs: Int, held: Int, errors: Int): String =
    (if (meanUs == 0) "held=$held"
    else "%.1f/%.1f ms held=%d".format(meanUs / 1000.0, maxUs / 1000.0, held)) + " errs=$errors"
