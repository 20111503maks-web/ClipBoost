package com.clipboost.core.analysis

import com.clipboost.core.model.QualityMetrics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Picture-quality measurements on a luma plane (values 0..255, row-major).
 * Pure math, so it runs in unit tests and on any thread.
 */
object FrameMetrics {

    /**
     * Fast noise variance estimation (J. Immerkær, 1996): convolve with a mask that cancels
     * image structure to first order and average the absolute response.
     * Returns the estimated noise standard deviation.
     */
    fun estimateNoiseSigma(luma: FloatArray, width: Int, height: Int): Float {
        require(luma.size == width * height) { "luma size mismatch" }
        if (width < 3 || height < 3) return 0f
        var sum = 0.0
        for (y in 1 until height - 1) {
            val r0 = (y - 1) * width
            val r1 = y * width
            val r2 = (y + 1) * width
            for (x in 1 until width - 1) {
                val v = luma[r0 + x - 1] - 2 * luma[r0 + x] + luma[r0 + x + 1] +
                    -2 * luma[r1 + x - 1] + 4 * luma[r1 + x] - 2 * luma[r1 + x + 1] +
                    luma[r2 + x - 1] - 2 * luma[r2 + x] + luma[r2 + x + 1]
                sum += abs(v)
            }
        }
        val n = (width - 2).toDouble() * (height - 2).toDouble()
        return (sqrt(PI / 2) * sum / (6.0 * n)).toFloat()
    }

    /** Variance of the 4-neighbour Laplacian — a standard focus/sharpness measure. */
    fun laplacianVariance(luma: FloatArray, width: Int, height: Int): Float {
        require(luma.size == width * height) { "luma size mismatch" }
        if (width < 3 || height < 3) return 0f
        var sum = 0.0
        var sumSq = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val v = (luma[i - width] + luma[i + width] + luma[i - 1] + luma[i + 1] - 4 * luma[i]).toDouble()
                sum += v
                sumSq += v * v
            }
        }
        val n = (width - 2).toDouble() * (height - 2).toDouble()
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }

    /** Converts packed ARGB pixels (Android Bitmap.getPixels) to BT.601 luma. */
    fun lumaFromArgb(pixels: IntArray): FloatArray = FloatArray(pixels.size) { i ->
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Combines per-frame measurements with the median, so one odd frame doesn't decide. */
    fun combine(perFrame: List<QualityMetrics>): QualityMetrics? {
        if (perFrame.isEmpty()) return null
        fun median(values: List<Float>): Float {
            val s = values.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
        }
        return QualityMetrics(
            noiseSigma = median(perFrame.map { it.noiseSigma }),
            sharpness = median(perFrame.map { it.sharpness }),
        )
    }
}
