package com.clipboost.app.ui.components

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.clipboost.app.ui.theme.Cb

@Composable
fun CbCard(
    modifier: Modifier = Modifier,
    border: Color? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Cb.Surface)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Cb.TextMuted)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable RowScope.() -> Unit)? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Cb.Accent, contentColor = Cb.OnAccent,
            disabledContainerColor = Cb.SurfaceHigh, disabledContentColor = Cb.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        icon?.invoke(this)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: (@Composable RowScope.() -> Unit)? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Cb.LineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cb.Text),
    ) {
        icon?.invoke(this)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Tag(text: String, background: Color = Cb.SurfaceHigh, color: Color = Cb.TextSoft) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** A looping, optionally muted player bound to [uri]; released with the composable. */
@Composable
fun rememberPlayer(uri: String?, muted: Boolean = true, loop: Boolean = true): ExoPlayer {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
        }
    }
    LaunchedEffect(uri) {
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}

/**
 * TextureView-backed video (not SurfaceView) so it can be clipped for the before/after split.
 * The caller sizes it to the video's display aspect ratio, so nothing is stretched.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
        modifier = modifier,
        onRelease = { player.clearVideoTextureView(it) },
    )
}

/** Letterboxes content of [aspectRatio] (w/h) inside the available box: fit, never stretch. */
@Composable
fun FittedBox(aspectRatio: Float, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val boxRatio = maxWidth.value / maxHeight.value
        val (w, h) = if (boxRatio > aspectRatio) (maxHeight * aspectRatio) to maxHeight else maxWidth to (maxWidth / aspectRatio)
        Box(Modifier.size(w, h)) { content(Modifier.size(w, h)) }
    }
}

@Composable
fun SpecTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Cb.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Cb.Text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted, maxLines = 1)
    }
}

@Composable
fun FullWidthColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
