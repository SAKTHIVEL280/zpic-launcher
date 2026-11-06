package com.zpic.launcher.ui

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zpic.launcher.system.DotsService
import kotlinx.coroutines.delay

private data class Now(val controller: MediaController, val title: String, val artist: String, val playing: Boolean)

/** A small player, shown only while something is playing. Needs notification access. */
@Composable
fun MediaCell() {
    val context = LocalContext.current
    val editing = LocalHomeEditing.current
    val now by produceState<Now?>(null) {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(context, DotsService::class.java)
        while (true) {
            value = runCatching {
                manager.getActiveSessions(me).firstOrNull { it.playbackState?.state.let { st -> st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED } }?.let { c ->
                    Now(c, c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(), c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(), c.playbackState?.state == PlaybackState.STATE_PLAYING)
                }
            }.getOrNull()
            delay(1500)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val n = now
    if (n == null && !editing) return
    Row(Modifier.clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerHigh.copy(alpha = .9f)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(150.dp)) {
            Text(n?.title?.ifEmpty { "Playing" } ?: "Music player", color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n?.artist ?: "Shows while something plays", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        fun tap(run: MediaController.TransportControls.() -> Unit): () -> Unit = { n?.controller?.transportControls?.run() }
        Button(Icons.Outlined.SkipPrevious, "Previous", tap { skipToPrevious() })
        Button(if (n?.playing == true) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "Play or pause", tap { if (n?.playing == true) pause() else play() })
        Button(Icons.Outlined.SkipNext, "Next", tap { skipToNext() })
    }
}

@Composable
private fun Button(icon: ImageVector, label: String, onClick: () -> Unit) {
    Icon(icon, label, Modifier.padding(start = 4.dp).size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = !LocalHomeEditing.current, onClick = onClick).padding(4.dp), tint = MaterialTheme.colorScheme.onSurface)
}
