package com.clipboost.core.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

enum class Orientation { PORTRAIT, LANDSCAPE, SQUARE }

/**
 * Facts about a source video, read from the container/stream — never guessed.
 * [width]/[height] are the *encoded* frame size; [rotationDegrees] is the display rotation tag.
 */
@Serializable
data class VideoInfo(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val durationMs: Long,
    /** Null when the container doesn't declare it and it can't be derived. */
    val frameRate: Float?,
    val videoMime: String,
    val audioMime: String?,
    val sizeBytes: Long?,
    val bitrate: Int?,
    val isHdr: Boolean,
) {
    private val rotated: Boolean get() = rotationDegrees % 180 != 0

    /** Size as the viewer sees it, after applying the rotation tag. */
    val displayWidth: Int get() = if (rotated) height else width
    val displayHeight: Int get() = if (rotated) width else height

    /** Display aspect ratio (width / height), used to size players without stretching. */
    val aspectRatio: Float get() = displayWidth.toFloat() / displayHeight.toFloat()

    val orientation: Orientation
        get() = when {
            abs(displayWidth - displayHeight) <= min(displayWidth, displayHeight) / 50 -> Orientation.SQUARE
            displayHeight > displayWidth -> Orientation.PORTRAIT
            else -> Orientation.LANDSCAPE
        }

    /** The "p" number people know (720p, 1080p…) — always taken from the short side. */
    val shortSide: Int get() = min(displayWidth, displayHeight)

    fun resolutionLabel(): String = when {
        shortSide >= 2100 -> "4K"
        shortSide >= 1400 -> "1440p"
        else -> "${shortSide}p"
    }

    fun frameRateLabel(): String = frameRate?.let { fps ->
        val rounded = fps.roundToInt()
        if (abs(fps - rounded) < 0.05f) "$rounded FPS" else "%.2f FPS".format(fps)
    } ?: "? FPS"

    fun durationLabel(): String {
        val totalSec = (durationMs + 500) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun aspectLabel(): String {
        val known = listOf(16 to 9, 9 to 16, 4 to 3, 3 to 4, 1 to 1, 21 to 9, 9 to 21, 3 to 2, 2 to 3, 4 to 5, 5 to 4)
        val match = known.firstOrNull { (w, h) -> abs(aspectRatio - w.toFloat() / h) < 0.02f }
        return match?.let { "${it.first}:${it.second}" } ?: "%.2f:1".format(aspectRatio)
    }

    fun codecLabel(): String = codecName(videoMime)

    companion object {
        fun codecName(mime: String): String = when (mime.lowercase()) {
            "video/avc" -> "H.264"
            "video/hevc" -> "HEVC"
            "video/x-vnd.on2.vp8" -> "VP8"
            "video/x-vnd.on2.vp9" -> "VP9"
            "video/av01" -> "AV1"
            "video/mp4v-es" -> "MPEG-4"
            "video/3gpp" -> "H.263"
            "video/mpeg2" -> "MPEG-2"
            "audio/mp4a-latm" -> "AAC"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/mpeg" -> "MP3"
            "audio/3gpp" -> "AMR"
            "audio/flac" -> "FLAC"
            "audio/ac3" -> "AC-3"
            else -> mime.substringAfter('/').uppercase()
        }

        fun sizeLabel(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "${(bytes / (1L shl 20).toDouble()).roundToInt()} MB"
            else -> "${(bytes / 1024.0).roundToInt()} KB"
        }
    }
}

/** Measured picture quality, from a few decoded sample frames. */
@Serializable
data class QualityMetrics(
    /** Estimated noise standard deviation on 0..255 luma (Immerkær). */
    val noiseSigma: Float,
    /** Variance of the Laplacian on 0..255 luma, at analysis scale. Higher = sharper. */
    val sharpness: Float,
)
