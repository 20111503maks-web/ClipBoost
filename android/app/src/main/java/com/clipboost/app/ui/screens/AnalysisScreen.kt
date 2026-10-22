package com.clipboost.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.clipboost.app.ui.AnalysisUi
import com.clipboost.app.ui.components.CbCard
import com.clipboost.app.ui.components.FittedBox
import com.clipboost.app.ui.components.PrimaryButton
import com.clipboost.app.ui.components.SectionLabel
import com.clipboost.app.ui.components.SpecTile
import com.clipboost.app.ui.components.Tag
import com.clipboost.app.ui.components.VideoSurface
import com.clipboost.app.ui.components.rememberPlayer
import com.clipboost.app.ui.theme.Cb
import com.clipboost.core.enhance.CustomSettings
import com.clipboost.core.enhance.EnhancementPlan
import com.clipboost.core.enhance.Level
import com.clipboost.core.enhance.Mode
import com.clipboost.core.enhance.OutputCodec
import com.clipboost.core.enhance.PlanResult
import com.clipboost.core.enhance.StageKind
import com.clipboost.core.model.VideoInfo
import kotlin.math.roundToInt

@Composable
fun AnalyzingScreen() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(color = Cb.Accent)
        Text("Analyzing on device…", style = MaterialTheme.typography.bodyLarge, color = Cb.TextSoft)
    }
}

@Composable
fun AnalysisScreen(
    ui: AnalysisUi,
    onBack: () -> Unit,
    onSelectMode: (Mode) -> Unit,
    onCustomChange: ((CustomSettings) -> CustomSettings) -> Unit,
    onEnhance: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column {
                Text(ui.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("Analyzed on device", style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Preview(ui)
            Specs(ui.info)
            Findings(ui)
            ModeGrid(ui.mode, onSelectMode)
            if (ui.mode == Mode.CUSTOM) CustomControls(ui, onCustomChange)
            PlanCard(ui)
        }
        PrimaryButton(
            text = "Enhance",
            onClick = onEnhance,
            enabled = ui.plan is PlanResult.Ready,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 10.dp)) },
        )
    }
}

@Composable
private fun Preview(ui: AnalysisUi) {
    val player = rememberPlayer(ui.uri.toString(), muted = true)
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(24.dp))) {
        FittedBox(ui.info.aspectRatio, Modifier.fillMaxSize()) { m -> VideoSurface(player, m) }
        Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { Tag("${ui.info.aspectLabel()} · fit, no stretch", Color(0xCC0A0B0E), Cb.Text) }
        Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Tag(ui.info.durationLabel(), Color(0xCC0A0B0E), Cb.Text) }
        IconButton(
            onClick = { if (player.isPlaying) player.pause() else player.play() },
            modifier = Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color(0xEBF3F4F6)),
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause preview" else "Play preview",
                tint = Cb.OnAccent,
            )
        }
    }
}

@Composable
private fun Specs(info: VideoInfo) {
    val tiles = listOf(
        info.resolutionLabel() to "${info.displayWidth}×${info.displayHeight}",
        info.frameRateLabel() to "frame rate",
        info.durationLabel() to "duration",
        info.codecLabel() to (info.audioMime?.let { VideoInfo.codecName(it) + " audio" } ?: "no audio"),
        info.aspectLabel() to info.orientation.name.lowercase() + if (info.isHdr) " · HDR" else "",
        (info.sizeBytes?.let { VideoInfo.sizeLabel(it) } ?: "—") to "file size",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) -> SpecTile(value, label, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Findings(ui: AnalysisUi) {
    CbCard(border = Cb.Line) {
        SectionLabel("What this clip needs")
        ui.findings.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(f.observation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(12.dp))
                Text(f.action, style = MaterialTheme.typography.bodyMedium, color = if (f.actionable) Cb.Accent else Cb.TextMuted)
            }
        }
    }
}

private fun Mode.icon(): ImageVector = when (this) {
    Mode.AUTO -> Icons.Rounded.AutoAwesome
    Mode.UPSCALE -> Icons.Rounded.ZoomOutMap
    Mode.SMOOTH_FPS -> Icons.Rounded.Speed
    Mode.DENOISE -> Icons.Rounded.Grain
    Mode.SHARPEN -> Icons.Rounded.Details
    Mode.RESTORE -> Icons.Rounded.Restore
    Mode.ANIME -> Icons.Rounded.Brush
    Mode.GAMING -> Icons.Rounded.SportsEsports
    Mode.CUSTOM -> Icons.Rounded.Tune
}

@Composable
private fun ModeGrid(selected: Mode, onSelect: (Mode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Mode")
        Mode.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode -> ModeTile(mode, mode == selected, { onSelect(mode) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ModeTile(mode: Mode, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val fg = if (isSelected) Cb.OnAccent else Cb.Text
    Box(
        modifier
            .height(84.dp)
            .clip(shape)
            .background(if (isSelected) Cb.Accent else Cb.Surface)
            .border(1.5.dp, if (isSelected) Cb.Accent else Cb.Line, shape)
            .semantics { this.selected = isSelected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(mode.icon(), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp).align(Alignment.TopStart))
        Text(mode.title, style = MaterialTheme.typography.bodySmall, color = fg, modifier = Modifier.align(Alignment.BottomStart))
        if (mode.requiresAi) {
            Box(Modifier.align(Alignment.TopEnd)) {
                Tag("soon", if (isSelected) Cb.OnAccent else Cb.SurfaceHigh, if (isSelected) Cb.Accent else Cb.TextMuted)
            }
        }
    }
}

@Composable
private fun CustomControls(ui: AnalysisUi, onChange: ((CustomSettings) -> CustomSettings) -> Unit) {
    val c = ui.custom
    CbCard {
        SectionLabel("Resolution")
        val smaller = listOf(1080, 720, 480).filter { it < ui.info.shortSide }
        ChipRow {
            Chip("Original", c.targetShortSide == null) { onChange { it.copy(targetShortSide = null) } }
            smaller.forEach { side -> Chip("${side}p", c.targetShortSide == side) { onChange { it.copy(targetShortSide = side) } } }
        }
        Text("Higher resolutions need AI Upscale, which comes in a later version.", style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted)

        SectionLabel("Denoise")
        ChipRow { Level.entries.forEach { l -> Chip(l.label, c.denoise == l) { onChange { it.copy(denoise = l) } } } }

        SectionLabel("Sharpen")
        ChipRow { Level.entries.forEach { l -> Chip(l.label, c.sharpen == l) { onChange { it.copy(sharpen = l) } } } }
        Text("Capped per pixel to avoid halos around edges.", style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted)

        SectionLabel("Export")
        ChipRow {
            Chip(OutputCodec.H264.label, c.codec == OutputCodec.H264) { onChange { it.copy(codec = OutputCodec.H264) } }
            if (ui.hevcEncoderAvailable) Chip(OutputCodec.HEVC.label, c.codec == OutputCodec.HEVC) { onChange { it.copy(codec = OutputCodec.HEVC) } }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun Chip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (isSelected) Cb.Accent else Cb.SurfaceHigh)
            .semantics { this.selected = isSelected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (isSelected) Cb.OnAccent else Cb.Text)
    }
}

@Composable
private fun PlanCard(ui: AnalysisUi) {
    CbCard {
        Text(ui.mode.title, style = MaterialTheme.typography.titleMedium)
        when (val p = ui.plan) {
            is PlanResult.Ready -> {
                p.plan.stages.forEach { stage ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stage.name, style = MaterialTheme.typography.bodyMedium, color = Cb.TextSoft)
                        Tag(if (stage.kind == StageKind.AI) "AI" else "Classic")
                    }
                }
                Text(outputLine(ui.info, p.plan), style = MaterialTheme.typography.labelSmall, color = Cb.TextMuted)
            }
            is PlanResult.NothingToDo -> Text(p.reason, style = MaterialTheme.typography.bodyMedium, color = Cb.TextSoft)
            is PlanResult.Unavailable -> Text(p.reason, style = MaterialTheme.typography.bodyMedium, color = Cb.TextSoft)
        }
    }
}

fun outputLine(info: VideoInfo, plan: EnhancementPlan): String {
    val scale = plan.targetShortSide?.let { it.toFloat() / info.shortSide } ?: 1f
    val w = (info.displayWidth * scale).roundToInt()
    val h = (info.displayHeight * scale).roundToInt()
    val audio = if (info.audioMime != null) "audio kept" else "no audio"
    return "Out ${w}×${h} · ${info.frameRateLabel()} · ${plan.codec.label.substringAfter("· ")} · $audio"
}
