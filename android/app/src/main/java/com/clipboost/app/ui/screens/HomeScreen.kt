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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clipboost.app.ui.components.SectionLabel
import com.clipboost.app.ui.theme.Cb
import com.clipboost.core.job.Job
import com.clipboost.core.job.JobState

@Composable
fun HomeScreen(
    jobs: List<Job>,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenJob: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Cb.Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Movie, contentDescription = null, tint = Cb.OnAccent, modifier = Modifier.size(18.dp)) }
            Text("ClipBoost", style = MaterialTheme.typography.titleLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make your clip\nlook its best.", style = MaterialTheme.typography.displaySmall)
            Text(
                "Pick a video. ClipBoost checks what it actually needs before touching a single frame.",
                style = MaterialTheme.typography.bodyLarge, color = Cb.TextMuted,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Cb.SurfaceLow)
                .border(1.5.dp, Cb.LineStrong, RoundedCornerShape(28.dp))
                .clickable(role = Role.Button, onClickLabel = "Select video", onClick = onPickGallery)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(Cb.Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Cb.OnAccent, modifier = Modifier.size(36.dp)) }
            Text("Select Video", style = MaterialTheme.typography.headlineSmall)
            Text("From your gallery", style = MaterialTheme.typography.bodyMedium, color = Cb.TextMuted)
            TextButton(onClick = onPickFiles) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Browse files — MKV, WebM, MOV…")
            }
        }

        val recent = jobs.take(5)
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Recent")
                recent.forEach { job -> JobRow(job, onClick = { onOpenJob(job.id) }) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Cb.TextMuted, modifier = Modifier.size(14.dp))
            Text(
                "Free · works offline · your video never leaves the phone",
                style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted, textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun JobRow(job: Job, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Cb.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Cb.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Movie, contentDescription = null, tint = Cb.TextMuted, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(job.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(statusLine(job), style = MaterialTheme.typography.bodySmall, color = Cb.TextMuted)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Cb.TextMuted)
    }
}

fun statusLine(job: Job): String = when (job.state) {
    JobState.CREATED -> "Waiting"
    JobState.PREPARING -> "Preparing"
    JobState.PROCESSING -> "Enhancing" + (job.progress?.let { " · $it%" } ?: "")
    JobState.FINALIZING -> "Finalizing"
    JobState.DONE -> if (job.savedUri != null) "Saved" else "Ready to compare"
    JobState.FAILED -> "Failed"
    JobState.CANCELED -> "Canceled"
    JobState.INTERRUPTED -> "Interrupted — tap to restart"
}
