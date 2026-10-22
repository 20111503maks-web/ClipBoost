package com.clipboost.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.clipboost.app.ui.components.CbCard
import com.clipboost.app.ui.components.FittedBox
import com.clipboost.app.ui.components.PrimaryButton
import com.clipboost.app.ui.components.SecondaryButton
import com.clipboost.app.ui.components.Tag
import com.clipboost.app.ui.components.VideoSurface
import com.clipboost.app.ui.components.rememberPlayer
import com.clipboost.app.ui.theme.Cb
import com.clipboost.core.job.Job
import com.clipboost.core.job.JobState
import com.clipboost.core.model.VideoInfo
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun JobScreen(
    job: Job,
    saving: Boolean,
    onHome: () -> Unit,
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (job.state == JobState.DONE && job.outputPath != null) {
        ResultScreen(job, saving, onHome, onSave, onDiscard)
    } else {
        ProcessingScreen(job, onHome, onCancel, onRestart)
    }
}

private data class StageRow(val title: String, val state: JobState)

@Composable
private fun ProcessingScreen(job: Job, onHome: () -> Unit, onCancel: () -> Unit, onRestart: () -> Unit) {
    // The pipeline really has these three phases; filters and encoding run in one GPU pass.
    val stages = listOf(
        StageRow("Preparing", JobState.PREPARING),
        StageRow("Enhancing & encoding", JobState.PROCESSING),
        StageRow("Finalizing", JobState.FINALIZING),
    )
    val activeIndex = stages.indexOfFirst { it.state == job.state }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (job.state.isActive) "Enhancing" else "Stopped", style = MaterialTheme.typography.titleLarge)
            Tag("job ${job.id}")
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(job.plan.stages.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted)
            Text(outputLine(job.info, job.plan), style = MaterialTheme.typography.labelSmall, color = Cb.TextMuted)
        }

        if (job.state.isActive) {
            CbCard(border = Cb.Line, padding = 20.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(stages.getOrNull(activeIndex)?.title ?: "Waiting", style = MaterialTheme.typography.headlineSmall)
                    job.progress?.let { Text("$it%", style = MaterialTheme.typography.displaySmall, color = Cb.Accent) }
                }
                val progress = job.progress
                if (job.state == JobState.PROCESSING && progress != null) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Cb.Accent, trackColor = Cb.Line)
                } else {
                    // No number available from the pipeline yet — show activity, not a made-up percentage.
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp), color = Cb.Accent, trackColor = Cb.Line)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stages.forEachIndexed { i, s ->
                    StageLine(s.title, done = activeIndex > i, active = activeIndex == i)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.NotificationsNone, contentDescription = null, tint = Cb.TextMuted, modifier = Modifier.size(16.dp))
                Text(
                    "Runs on this phone. You can leave the app — it keeps going in the background and notifies you. Long clips: keep the phone charging.",
                    style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted,
                )
            }
            SecondaryButton("Cancel job", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        } else {
            CbCard(border = Cb.Line) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = if (job.state == JobState.FAILED) Cb.Error else Cb.Warn)
                    Text(stoppedTitle(job), style = MaterialTheme.typography.titleMedium)
                }
                Text(stoppedDetail(job), style = MaterialTheme.typography.bodyMedium, color = Cb.TextSoft)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("Start again", onClick = onRestart, modifier = Modifier.fillMaxWidth(), icon = {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 10.dp))
            })
            SecondaryButton("Back to home", onClick = onHome, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun stoppedTitle(job: Job) = when (job.state) {
    JobState.CANCELED -> "Canceled"
    JobState.INTERRUPTED -> "Processing stopped"
    else -> "Couldn't finish"
}

private fun stoppedDetail(job: Job) = when (job.state) {
    JobState.CANCELED -> "Nothing was saved. Temporary files were deleted."
    JobState.INTERRUPTED -> "Android closed ClipBoost while it worked. Start again to redo it from the beginning."
    else -> job.error?.userMessage ?: "Something went wrong while processing."
}

@Composable
private fun StageLine(title: String, done: Boolean, active: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (done) Cb.Accent else if (active) Cb.Surface else Cb.Ground),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Cb.OnAccent, modifier = Modifier.size(16.dp))
                active -> Box(Modifier.size(10.dp).clip(CircleShape).background(Cb.Accent))
                else -> Box(Modifier.size(22.dp).clip(CircleShape).background(Cb.LineStrong).padding(2.dp).clip(CircleShape).background(Cb.Ground))
            }
        }
        Text(
            title,
            style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (done || active) Cb.Text else Cb.TextMuted,
        )
    }
}

/** Visible part of the "before" layer: the left [fraction] of the frame. */
private class LeftFractionShape(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, size.width * fraction, size.height))
}

@Composable
private fun ResultScreen(job: Job, saving: Boolean, onHome: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    val outputFile = File(job.outputPath!!)
    val after = rememberPlayer(outputFile.toUri().toString(), muted = false)
    val before = rememberPlayer(job.sourceUri, muted = true)
    var playing by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(after) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        after.addListener(listener)
        onDispose { after.removeListener(listener) }
    }
    // Keep the two players on the same frame while playing.
    LaunchedEffect(playing) {
        while (playing) {
            if (abs(before.currentPosition - after.currentPosition) > 120) before.seekTo(after.currentPosition)
            delay(1000)
        }
    }

    if (fullscreen) {
        BackHandler { fullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = after; useController = true } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.player = null },
            )
            IconButton(onClick = { fullscreen = false }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close full screen", tint = Color.White)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onHome) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            Text("Result ready", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { fullscreen = true }) { Icon(Icons.Rounded.Fullscreen, contentDescription = "Play result full screen") }
        }

        CompareViewer(
            info = job.info,
            before = before,
            after = after,
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                if (playing) "Pause" else "Play",
                onClick = {
                    if (playing) {
                        after.pause(); before.pause()
                    } else {
                        before.seekTo(after.currentPosition)
                        after.play(); before.play()
                    }
                },
                modifier = Modifier.weight(1f),
                icon = { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp)) },
            )
            PrimaryButton(
                text = when {
                    job.savedUri != null -> "Saved"
                    saving -> "Saving…"
                    else -> "Save video"
                },
                onClick = onSave,
                enabled = job.savedUri == null && !saving,
                modifier = Modifier.weight(1f).height(56.dp),
                icon = {
                    Icon(if (job.savedUri != null) Icons.Rounded.Check else Icons.Rounded.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                },
            )
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                outputLine(job.info, job.plan).removePrefix("Out ") + " · " + VideoInfo.sizeLabel(outputFile.length()),
                style = MaterialTheme.typography.labelSmall, color = Cb.TextMuted,
            )
            Text("Processed on this phone · nothing uploaded", style = MaterialTheme.typography.labelSmall, color = Cb.TextMuted)
            TextButton(onClick = onDiscard) { Text("Discard result", color = Cb.TextMuted) }
        }
    }
}

@Composable
private fun CompareViewer(info: VideoInfo, before: Player, after: Player, modifier: Modifier) {
    var split by remember { mutableFloatStateOf(0.5f) }
    var widthPx by remember { mutableIntStateOf(1) }
    FittedBox(info.aspectRatio, modifier) { m ->
        Box(
            m
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        split = (split + dx / size.width).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) } }
                .semantics { contentDescription = "Before and after comparison. Drag to move the divider." },
        ) {
            VideoSurface(after, Modifier.fillMaxSize())
            VideoSurface(
                before,
                Modifier.fillMaxSize().graphicsLayer {
                    clip = true
                    shape = LeftFractionShape(split)
                },
            )
            val x = (split * widthPx).roundToInt()
            Box(Modifier.fillMaxHeight().width(2.dp).offset { IntOffset(x - 1, 0) }.background(Cb.Text))
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x - 22.dp.roundToPx(), 0) }
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Cb.Text),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Cb.OnAccent) }
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { Tag("BEFORE", Color(0xCC0A0B0E), Cb.Text) }
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { Tag("AFTER", Cb.Accent, Cb.OnAccent) }
        }
    }
}
