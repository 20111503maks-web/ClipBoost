package com.clipboost.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.clipboost.app.service.ProcessingService
import com.clipboost.app.ui.MainViewModel
import com.clipboost.app.ui.Screen
import com.clipboost.app.ui.screens.AnalysisScreen
import com.clipboost.app.ui.screens.AnalyzingScreen
import com.clipboost.app.ui.screens.HomeScreen
import com.clipboost.app.ui.screens.JobScreen
import com.clipboost.app.ui.theme.Cb
import com.clipboost.app.ui.theme.ClipBoostTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ClipBoostTheme { ClipBoostRoot(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opens a job from a notification, or a video shared / opened with ClipBoost. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(ProcessingService.EXTRA_JOB_ID)?.let { viewModel.openJob(it); return }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        uri?.let { viewModel.onVideoPicked(it) }
    }
}

@Composable
private fun ClipBoostRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { keepAccess(context, it); viewModel.onVideoPicked(it) }
    }
    // The system file picker reaches containers the gallery doesn't index (MKV, WebM…).
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { keepAccess(context, it); viewModel.onVideoPicked(it) }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    BackHandler(enabled = state.screen != Screen.Home) { viewModel.back() }

    Scaffold(containerColor = Cb.Ground, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val screen = state.screen) {
                Screen.Home -> HomeScreen(
                    jobs = jobs,
                    onPickGallery = { pickGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    onPickFiles = { pickFile.launch(arrayOf("video/*")) },
                    onOpenJob = viewModel::openJob,
                )
                Screen.Analyzing -> AnalyzingScreen()
                Screen.Analysis -> state.analysis?.let { ui ->
                    AnalysisScreen(
                        ui = ui,
                        onBack = { viewModel.back() },
                        onSelectMode = viewModel::selectMode,
                        onCustomChange = viewModel::updateCustom,
                        onEnhance = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.enhance()
                        },
                    )
                }
                is Screen.JobView -> jobs.firstOrNull { it.id == screen.jobId }?.let { job ->
                    JobScreen(
                        job = job,
                        saving = state.savingJobId == job.id,
                        onHome = { viewModel.back() },
                        onCancel = { viewModel.cancelJob(job.id) },
                        onRestart = { viewModel.restartJob(job.id) },
                        onSave = { viewModel.save(job.id) },
                        onDiscard = { viewModel.discard(job.id) },
                    )
                }
            }
        }
    }
}

/** Keep read access across restarts when the provider allows it, so a job can be restarted later. */
private fun keepAccess(context: android.content.Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Not persistable for this provider; access lasts for this session, which covers normal use.
    }
}
