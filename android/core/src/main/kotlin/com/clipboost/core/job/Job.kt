package com.clipboost.core.job

import com.clipboost.core.enhance.EnhancementPlan
import com.clipboost.core.model.VideoInfo
import kotlinx.serialization.Serializable
import java.security.SecureRandom

enum class JobState {
    CREATED, PREPARING, PROCESSING, FINALIZING, DONE, FAILED, CANCELED,
    /** The process died while the job ran (app killed, phone restarted). The user can restart it. */
    INTERRUPTED;

    val isActive: Boolean get() = this == CREATED || this == PREPARING || this == PROCESSING || this == FINALIZING
    val isTerminal: Boolean get() = !isActive

    fun canMoveTo(next: JobState): Boolean = next in allowed.getValue(this)

    private companion object {
        val allowed: Map<JobState, Set<JobState>> = mapOf(
            CREATED to setOf(PREPARING, FAILED, CANCELED, INTERRUPTED),
            PREPARING to setOf(PROCESSING, FAILED, CANCELED, INTERRUPTED),
            PROCESSING to setOf(FINALIZING, FAILED, CANCELED, INTERRUPTED),
            FINALIZING to setOf(DONE, FAILED, CANCELED, INTERRUPTED),
            DONE to emptySet(),
            FAILED to setOf(CREATED),
            CANCELED to setOf(CREATED),
            INTERRUPTED to setOf(CREATED),
        )
    }
}

enum class JobError {
    DAMAGED_FILE, UNSUPPORTED_CODEC, NO_SPACE, ENCODER_FAILED, SOURCE_UNAVAILABLE, UNKNOWN;

    val userMessage: String
        get() = when (this) {
            DAMAGED_FILE -> "The file looks damaged — no playable video stream was found."
            UNSUPPORTED_CODEC -> "This phone has no decoder for this video's codec."
            NO_SPACE -> "Not enough free storage for the result."
            ENCODER_FAILED -> "The phone's video encoder refused these settings. Try H.264 or a smaller size."
            SOURCE_UNAVAILABLE -> "ClipBoost can no longer open the original video. Pick it again."
            UNKNOWN -> "Something went wrong while processing."
        }
}

@Serializable
data class Job(
    val id: String,
    val sourceUri: String,
    val displayName: String,
    val info: VideoInfo,
    val plan: EnhancementPlan,
    val state: JobState = JobState.CREATED,
    /** 0..100 as reported by the encoder pipeline, or null when it can't report one. */
    val progress: Int? = null,
    val error: JobError? = null,
    /** Private app file holding the result until the user saves it. */
    val outputPath: String? = null,
    /** content:// URI in the gallery once saved. */
    val savedUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun moveTo(next: JobState, now: Long): Job {
        check(state.canMoveTo(next)) { "Illegal job transition $state -> $next" }
        return copy(state = next, updatedAt = now, progress = if (next == JobState.CREATED) null else progress)
    }
}

object JobIds {
    private val random = SecureRandom()
    private const val HEX = "0123456789abcdef"

    fun newId(): String = buildString {
        append("cb_")
        repeat(10) { append(HEX[random.nextInt(16)]) }
    }
}

/** Rough upper bound of the output size, used to refuse a job before storage runs out mid-way. */
object OutputSizeEstimator {
    fun estimateBytes(info: VideoInfo, plan: EnhancementPlan): Long {
        val sourceBitrate = info.bitrate?.toLong()
            ?: info.sizeBytes?.let { if (info.durationMs > 0) it * 8_000 / info.durationMs else null }
            ?: 20_000_000L
        val scale = plan.targetShortSide?.let { (it.toDouble() / info.shortSide).let { r -> r * r } } ?: 1.0
        val bits = sourceBitrate * scale * (info.durationMs / 1000.0)
        // ×1.5 headroom: encoders overshoot, and we keep the original audio.
        return (bits / 8 * 1.5).toLong() + 4L * 1024 * 1024
    }
}
