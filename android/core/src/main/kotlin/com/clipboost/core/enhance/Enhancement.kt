package com.clipboost.core.enhance

import kotlinx.serialization.Serializable

/** Processing modes shown to the user. [requiresAi] modes stay disabled until a real model ships. */
enum class Mode(val title: String, val requiresAi: Boolean) {
    AUTO("Auto", false),
    UPSCALE("AI Upscale", true),
    SMOOTH_FPS("Smooth FPS", true),
    DENOISE("Denoise", false),
    SHARPEN("Sharpen", false),
    RESTORE("Restore", true),
    ANIME("Anime", true),
    GAMING("Gaming", true),
    CUSTOM("Custom", false),
}

enum class Level(val label: String) {
    OFF("Off"), LIGHT("Light"), MEDIUM("Medium"), STRONG("Strong");

    /** Edge-preserving denoise: range sigma on 0..1 colour scale. */
    val denoiseRangeSigma: Float
        get() = when (this) {
            OFF -> 0f
            LIGHT -> 0.03f
            MEDIUM -> 0.06f
            STRONG -> 0.10f
        }

    /** Unsharp-mask amount; the shader also caps each pixel's change to avoid halos. */
    val sharpenAmount: Float
        get() = when (this) {
            OFF -> 0f
            LIGHT -> 0.35f
            MEDIUM -> 0.6f
            STRONG -> 0.9f
        }
}

enum class OutputCodec(val label: String, val mime: String) {
    H264("MP4 · H.264", "video/avc"),
    HEVC("MP4 · HEVC", "video/hevc"),
}

/** Whether a stage really runs a neural network ([AI]) or a classic filter ([CLASSIC]). Shown to the user. */
enum class StageKind { AI, CLASSIC }

@Serializable
data class Stage(val name: String, val kind: StageKind)

/** Exactly what the pipeline will do. Built only by [Planner]. */
@Serializable
data class EnhancementPlan(
    val mode: Mode,
    val denoise: Level,
    val sharpen: Level,
    /** Short side of the output in px; null keeps the source size. Only ever <= source without AI. */
    val targetShortSide: Int?,
    val codec: OutputCodec,
) {
    val stages: List<Stage>
        get() = buildList {
            if (denoise != Level.OFF) add(Stage("Denoise · ${denoise.label}", StageKind.CLASSIC))
            if (sharpen != Level.OFF) add(Stage("Sharpen · ${sharpen.label}", StageKind.CLASSIC))
            if (targetShortSide != null) add(Stage("Resize to ${targetShortSide}p", StageKind.CLASSIC))
            add(Stage("Encode ${codec.label}", StageKind.CLASSIC))
        }

    /** True if the result differs from a plain re-encode. */
    val changesPicture: Boolean get() = denoise != Level.OFF || sharpen != Level.OFF || targetShortSide != null
}

@Serializable
data class CustomSettings(
    val denoise: Level = Level.OFF,
    val sharpen: Level = Level.OFF,
    /** Null = keep original. */
    val targetShortSide: Int? = null,
    val codec: OutputCodec = OutputCodec.H264,
)

/** One line of "What this clip needs". */
data class Finding(val observation: String, val action: String, val actionable: Boolean)

sealed interface PlanResult {
    data class Ready(val plan: EnhancementPlan) : PlanResult
    data class NothingToDo(val reason: String) : PlanResult
    data class Unavailable(val reason: String) : PlanResult
}
