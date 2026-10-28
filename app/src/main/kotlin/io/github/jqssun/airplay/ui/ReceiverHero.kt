package io.github.jqssun.airplay.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.service.ReceiverStatusPolicy

@Composable
internal fun ReceiverStatusLights(
    lights: ReceiverStatusPolicy.Lights,
    modifier: Modifier = Modifier,
) {
    val onColor = MaterialTheme.colorScheme.tertiary
    val offColor = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    val dimLabel = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusLight(
                label = stringResource(R.string.status_receiver),
                on = lights.receiverOn,
                onColor = onColor,
                offColor = offColor,
                labelColor = dimLabel,
            )
            StatusLight(
                label = stringResource(R.string.status_sender),
                on = lights.senderOn,
                onColor = onColor,
                offColor = offColor,
                labelColor = dimLabel,
            )
            StatusLight(
                label = stringResource(R.string.status_media),
                on = lights.mediaOn,
                onColor = onColor,
                offColor = offColor,
                labelColor = dimLabel,
            )
        }
    }
}

@Composable
private fun StatusLight(
    label: String,
    on: Boolean,
    onColor: Color,
    offColor: Color,
    labelColor: Color,
) {
    val color = if (on) onColor else offColor
    val stateWord = if (on) "on" else "off"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label $stateWord"
        },
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor)
    }
}

/** Legacy single-pill status kept for non-overview call sites that only know ServerState. */
@Composable
internal fun ReceiverStatus(state: ServerState, connections: Int = 0) {
    // connections ignored — UI no longer trusts the raw TCP counter.
    val lights = ReceiverStatusPolicy.deriveLights(
        serverRunning = state == ServerState.RUNNING,
        mirroringActive = false,
        videoPlaybackActive = false,
        audioOnly = false,
        videoSessionPending = false,
        videoBuffering = false,
        handshakeUntilMs = null,
        nowMs = 0L,
    )
    ReceiverStatusLights(lights)
}

@Composable
internal fun ReceiverHero(name: String, state: ServerState) {
    val tv = isTv()
    // Scroll only on compact/tall-text layouts; no focusable decoration competes with Start.
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(if (tv) 24.dp else 20.dp)
    ) {
        val wide = maxWidth >= 540.dp
        val glyph: @Composable () -> Unit = {
            ReceiverGlyph(running = state == ServerState.RUNNING, modifier = Modifier.size(if (wide) 148.dp else 88.dp))
        }
        val details: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReceiverStatus(state)
                Text(
                    name,
                    style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(when (state) {
                        ServerState.RUNNING -> R.string.receiver_ready_title
                        ServerState.STOPPED -> R.string.server_stopped
                        ServerState.ERROR -> R.string.error_starting_server
                    }),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.receiver_cast_help, name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.receiver_network_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (wide) {
            Row(
                Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                glyph()
                Box(Modifier.weight(1f)) { details() }
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                glyph()
                details()
            }
        }
    }
}

@Composable
private fun ReceiverGlyph(running: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        // Only composed while running and idle. Works on API 30 without blur/render effects.
        if (running) {
            val transition = rememberInfiniteTransition(label = "receiverPulse")
            val pulse by transition.animateFloat(
                initialValue = 0.88f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
                label = "receiverRing",
            )
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.35f }
                    .border(1.dp, primary, CircleShape)
            )
        }
        Box(
            Modifier.fillMaxSize(0.75f).background(primary.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Cast, null, Modifier.fillMaxSize(0.55f), tint = primary)
        }
    }
}
