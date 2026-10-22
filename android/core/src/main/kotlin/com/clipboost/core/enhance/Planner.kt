package com.clipboost.core.enhance

import com.clipboost.core.model.QualityMetrics
import com.clipboost.core.model.VideoInfo

/** What the running build can actually do. AI stays false until a model is integrated (roadmap M3+). */
data class Capabilities(val aiAvailable: Boolean = false)

/**
 * Turns measurements into a plan. The rule of thumb: change only what the measurements say is wrong.
 *
 * Thresholds are heuristics tuned for the analysis scale used by the app (frames scaled to ~640 px
 * on the long side). They are deliberately conservative — leaving a clip alone beats damaging it.
 */
class Planner(private val capabilities: Capabilities = Capabilities()) {

    fun noiseLevel(metrics: QualityMetrics): Level = when {
        metrics.noiseSigma < NOISE_LIGHT -> Level.OFF
        metrics.noiseSigma < NOISE_MEDIUM -> Level.LIGHT
        metrics.noiseSigma < NOISE_STRONG -> Level.MEDIUM
        else -> Level.STRONG
    }

    fun isSoft(metrics: QualityMetrics): Boolean = metrics.sharpness < SOFT_SHARPNESS

    fun findings(info: VideoInfo, metrics: QualityMetrics?): List<Finding> = buildList {
        if (metrics != null) {
            when (noiseLevel(metrics)) {
                Level.OFF -> add(Finding("Low noise", "No denoise", false))
                Level.LIGHT -> add(Finding("Some grain", "Light denoise", true))
                Level.MEDIUM -> add(Finding("Noticeable noise", "Denoise", true))
                Level.STRONG -> add(Finding("Heavy noise", "Strong denoise", true))
            }
            when {
                !isSoft(metrics) -> add(Finding("Sharp enough", "No sharpen", false))
                metrics.noiseSigma >= NOISE_MEDIUM -> add(Finding("Soft, but noisy", "Sharpen after denoise", true))
                else -> add(Finding("Slightly soft", "Light sharpen", true))
            }
        } else {
            add(Finding("Couldn't sample frames", "Quality not measured", false))
        }
        val upscaleAction = if (capabilities.aiAvailable) "AI Upscale" else "AI Upscale — coming later"
        when {
            info.shortSide >= 1080 -> add(Finding("Already ${info.resolutionLabel()}", "No upscale", false))
            info.shortSide < 720 -> add(Finding("Low resolution · ${info.resolutionLabel()}", upscaleAction, capabilities.aiAvailable))
            else -> add(Finding("${info.resolutionLabel()}", "Upscale optional", false))
        }
        val fps = info.frameRate
        if (fps != null && fps < 40f) {
            val action = if (capabilities.aiAvailable) "60 FPS optional" else "60 FPS — coming later"
            add(Finding("${info.frameRateLabel()}", action, false))
        }
    }

    fun plan(mode: Mode, info: VideoInfo, metrics: QualityMetrics?, custom: CustomSettings = CustomSettings()): PlanResult {
        if (mode.requiresAi && !capabilities.aiAvailable) {
            return PlanResult.Unavailable("${mode.title} needs an on-device AI model. It arrives in a later version — ClipBoost won't fake it with a plain filter.")
        }
        return when (mode) {
            Mode.AUTO -> auto(info, metrics)
            Mode.DENOISE -> {
                val level = metrics?.let { noiseLevel(it) }?.takeIf { it != Level.OFF } ?: Level.LIGHT
                ready(mode, denoise = level)
            }
            Mode.SHARPEN -> {
                val level = if (metrics != null && !isSoft(metrics)) Level.LIGHT else Level.MEDIUM
                ready(mode, sharpen = level)
            }
            Mode.CUSTOM -> custom(info, custom)
            // AI modes, reachable only when capabilities.aiAvailable — wired in M3+.
            else -> PlanResult.Unavailable("${mode.title} isn't wired to a model in this build.")
        }
    }

    private fun auto(info: VideoInfo, metrics: QualityMetrics?): PlanResult {
        if (metrics == null) {
            return PlanResult.NothingToDo("Couldn't measure this clip's quality, so Auto won't guess. Try Denoise, Sharpen or Custom.")
        }
        val denoise = noiseLevel(metrics)
        val sharpen = when {
            !isSoft(metrics) -> Level.OFF
            denoise >= Level.MEDIUM -> Level.LIGHT // denoise softens; give a little back, but no more
            else -> Level.LIGHT
        }
        if (denoise == Level.OFF && sharpen == Level.OFF) {
            val note = if (info.shortSide < 720 && !capabilities.aiAvailable) " Only an AI upscale would help, and that comes in a later version." else ""
            return PlanResult.NothingToDo("This clip is already clean and sharp — re-encoding it would only cost quality.$note")
        }
        return ready(Mode.AUTO, denoise = denoise, sharpen = sharpen)
    }

    private fun custom(info: VideoInfo, s: CustomSettings): PlanResult {
        val target = s.targetShortSide?.takeIf { it != info.shortSide }
        if (target != null && target > info.shortSide && !capabilities.aiAvailable) {
            return PlanResult.Unavailable("Raising resolution needs AI Upscale, which comes in a later version. Keep the original size or pick a smaller one.")
        }
        val plan = EnhancementPlan(Mode.CUSTOM, s.denoise, s.sharpen, target, s.codec)
        if (!plan.changesPicture && s.codec == OutputCodec.H264 && info.videoMime == OutputCodec.H264.mime) {
            return PlanResult.NothingToDo("These settings wouldn't change anything.")
        }
        return PlanResult.Ready(plan)
    }

    private fun ready(mode: Mode, denoise: Level = Level.OFF, sharpen: Level = Level.OFF) =
        PlanResult.Ready(EnhancementPlan(mode, denoise, sharpen, targetShortSide = null, codec = OutputCodec.H264))

    companion object {
        const val NOISE_LIGHT = 2.0f
        const val NOISE_MEDIUM = 4.0f
        const val NOISE_STRONG = 7.0f
        const val SOFT_SHARPNESS = 120f
    }
}
