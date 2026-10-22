package com.clipboost.core

import com.clipboost.core.analysis.FrameMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class FrameMetricsTest {
    private val w = 320
    private val h = 240

    private fun gradient() = FloatArray(w * h) { i -> ((i % w) * 255f / w) }

    private fun withNoise(base: FloatArray, sigma: Double, seed: Long = 7): FloatArray {
        val r = Random(seed)
        return FloatArray(base.size) { i -> (base[i] + r.nextGaussian() * sigma).toFloat() }
    }

    @Test
    fun flatAndGradientImagesHaveNoNoise() {
        assertEquals(0f, FrameMetrics.estimateNoiseSigma(FloatArray(w * h) { 128f }, w, h), 1e-3f)
        assertEquals(0f, FrameMetrics.estimateNoiseSigma(gradient(), w, h), 1e-2f)
    }

    @Test
    fun estimatesGaussianNoiseSigma() {
        for (sigma in listOf(2.0, 5.0, 10.0)) {
            val est = FrameMetrics.estimateNoiseSigma(withNoise(gradient(), sigma), w, h)
            assertEquals("sigma=$sigma", sigma.toFloat(), est, (sigma * 0.1).toFloat())
        }
    }

    @Test
    fun blurReducesLaplacianVariance() {
        val r = Random(1)
        val sharp = FloatArray(w * h) { if (r.nextBoolean()) 200f else 40f }
        val blurred = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var s = 0f
            for (dy in -1..1) for (dx in -1..1) s += sharp[(y + dy) * w + x + dx]
            blurred[y * w + x] = s / 9f
        }
        assertTrue(FrameMetrics.laplacianVariance(sharp, w, h) > 10 * FrameMetrics.laplacianVariance(blurred, w, h))
    }

    @Test
    fun lumaFromArgbUsesBt601() {
        val luma = FrameMetrics.lumaFromArgb(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt()))
        assertEquals(255f, luma[0], 0.01f)
        assertEquals(0f, luma[1], 0.01f)
        assertEquals(0.299f * 255, luma[2], 0.01f)
    }
}
