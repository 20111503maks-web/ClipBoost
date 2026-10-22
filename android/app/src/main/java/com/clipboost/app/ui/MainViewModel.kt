package com.clipboost.app.ui

import android.app.Application
import android.media.MediaCodecList
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipboost.app.ClipBoostApp
import com.clipboost.app.analysis.AnalysisResult
import com.clipboost.app.export.GallerySaver
import com.clipboost.app.service.ProcessingService
import com.clipboost.core.enhance.CustomSettings
import com.clipboost.core.enhance.Finding
import com.clipboost.core.enhance.Mode
import com.clipboost.core.enhance.OutputCodec
import com.clipboost.core.enhance.PlanResult
import com.clipboost.core.job.Job
import com.clipboost.core.job.JobIds
import com.clipboost.core.job.JobState
import com.clipboost.core.model.QualityMetrics
import com.clipboost.core.model.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface Screen {
    data object Home : Screen
    data object Analyzing : Screen
    data object Analysis : Screen
    data class JobView(val jobId: String) : Screen
}

data class AnalysisUi(
    val uri: Uri,
    val displayName: String,
    val info: VideoInfo,
    val metrics: QualityMetrics?,
    val findings: List<Finding>,
    val mode: Mode,
    val custom: CustomSettings,
    val plan: PlanResult,
    val hevcEncoderAvailable: Boolean,
)

data class UiState(
    val screen: Screen = Screen.Home,
    val analysis: AnalysisUi? = null,
    val message: String? = null,
    val savingJobId: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ClipBoostApp).container
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val jobs: StateFlow<List<Job>> = container.jobs.jobs

    fun onVideoPicked(uri: Uri) {
        _state.update { it.copy(screen = Screen.Analyzing, analysis = null) }
        viewModelScope.launch {
            when (val r = container.analyzer.analyze(uri)) {
                is AnalysisResult.Success -> {
                    val mode = Mode.AUTO
                    val custom = CustomSettings()
                    val ui = AnalysisUi(
                        uri = uri,
                        displayName = r.displayName,
                        info = r.info,
                        metrics = r.metrics,
                        findings = container.planner.findings(r.info, r.metrics),
                        mode = mode,
                        custom = custom,
                        plan = container.planner.plan(mode, r.info, r.metrics, custom),
                        hevcEncoderAvailable = hasEncoder(OutputCodec.HEVC.mime),
                    )
                    _state.update { it.copy(screen = Screen.Analysis, analysis = ui) }
                }
                is AnalysisResult.Failure -> {
                    val detail = r.detail?.let { " ($it)" } ?: ""
                    _state.update { it.copy(screen = Screen.Home, message = r.error.userMessage + detail) }
                }
            }
        }
    }

    fun selectMode(mode: Mode) = replan { it.copy(mode = mode) }

    fun updateCustom(transform: (CustomSettings) -> CustomSettings) = replan { it.copy(custom = transform(it.custom)) }

    private fun replan(change: (AnalysisUi) -> AnalysisUi) {
        _state.update { s ->
            val a = s.analysis ?: return@update s
            val next = change(a)
            s.copy(analysis = next.copy(plan = container.planner.plan(next.mode, next.info, next.metrics, next.custom)))
        }
    }

    fun enhance() {
        val a = _state.value.analysis ?: return
        val plan = (a.plan as? PlanResult.Ready)?.plan ?: return
        val now = System.currentTimeMillis()
        val job = Job(
            id = JobIds.newId(),
            sourceUri = a.uri.toString(),
            displayName = a.displayName,
            info = a.info,
            plan = plan,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            container.jobs.add(job)
            ProcessingService.start(getApplication(), job.id)
            _state.update { it.copy(screen = Screen.JobView(job.id)) }
        }
    }

    fun openJob(id: String) {
        if (container.jobs.get(id) != null) _state.update { it.copy(screen = Screen.JobView(id)) }
    }

    fun cancelJob(id: String) = ProcessingService.cancel(getApplication(), id)

    fun restartJob(id: String) {
        viewModelScope.launch {
            val job = container.jobs.transition(id, JobState.CREATED) { it.copy(error = null, outputPath = null) }
            if (job?.state == JobState.CREATED) ProcessingService.start(getApplication(), id)
        }
    }

    fun save(id: String) {
        val job = container.jobs.get(id) ?: return
        val path = job.outputPath ?: return
        if (_state.value.savingJobId != null) return
        _state.update { it.copy(savingJobId = id) }
        viewModelScope.launch {
            val message = try {
                val uri = GallerySaver.save(getApplication(), File(path), job.displayName)
                container.jobs.update(id) { it.copy(savedUri = uri.toString()) }
                "Saved to Movies/ClipBoost"
            } catch (e: IOException) {
                if (e.message?.contains("ENOSPC") == true) "Not enough free storage to save" else "Couldn't save the video"
            } catch (e: SecurityException) {
                "Couldn't save the video"
            }
            _state.update { it.copy(savingJobId = null, message = message) }
        }
    }

    fun discard(id: String) {
        viewModelScope.launch {
            container.jobs.delete(id)
            _state.update { it.copy(screen = Screen.Home) }
        }
    }

    /** Returns false when there's nowhere to go back to (let the system close the app). */
    fun back(): Boolean {
        val screen = _state.value.screen
        if (screen == Screen.Home) return false
        _state.update { it.copy(screen = Screen.Home) }
        return true
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun hasEncoder(mime: String): Boolean = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .any { info -> info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
}
