package com.clipboost.core

import com.clipboost.core.enhance.CustomSettings
import com.clipboost.core.enhance.Level
import com.clipboost.core.enhance.Mode
import com.clipboost.core.enhance.PlanResult
import com.clipboost.core.enhance.Planner
import com.clipboost.core.enhance.StageKind
import com.clipboost.core.model.QualityMetrics
import com.clipboost.core.model.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private val planner = Planner()

    private fun video(w: Int = 1920, h: Int = 1080, rotation: Int = 0, fps: Float? = 30f) = VideoInfo(
        width = w, height = h, rotationDegrees = rotation, durationMs = 42_000, frameRate = fps,
        videoMime = "video/avc", audioMime = "audio/mp4a-latm", sizeBytes = 86L shl 20, bitrate = 16_000_000, isHdr = false,
    )

    private val clean = QualityMetrics(noiseSigma = 0.8f, sharpness = 400f)
    private val noisySoft = QualityMetrics(noiseSigma = 5f, sharpness = 60f)

    @Test
    fun autoLeavesCleanHdVideoAlone() {
        assertTrue(planner.plan(Mode.AUTO, video(), clean) is PlanResult.NothingToDo)
    }

    @Test
    fun autoDenoisesNoisyVideoAndOnlyLightlySharpens() {
        val result = planner.plan(Mode.AUTO, video(), noisySoft) as PlanResult.Ready
        assertEquals(Level.MEDIUM, result.plan.denoise)
        assertEquals(Level.LIGHT, result.plan.sharpen)
        assertEquals(null, result.plan.targetShortSide)
    }

    @Test
    fun autoWithoutMeasurementsDoesNotGuess() {
        assertTrue(planner.plan(Mode.AUTO, video(), null) is PlanResult.NothingToDo)
    }

    @Test
    fun aiModesAreUnavailableWithoutAModel() {
        for (mode in Mode.entries.filter { it.requiresAi }) {
            assertTrue(mode.name, planner.plan(mode, video(), noisySoft) is PlanResult.Unavailable)
        }
    }

    @Test
    fun customRefusesUpscaleWithoutAi() {
        val r = planner.plan(Mode.CUSTOM, video(1280, 720), clean, CustomSettings(targetShortSide = 1080))
        assertTrue(r is PlanResult.Unavailable)
    }

    @Test
    fun customAllowsDownscaleAsClassicStage() {
        val r = planner.plan(Mode.CUSTOM, video(), clean, CustomSettings(targetShortSide = 720)) as PlanResult.Ready
        assertEquals(720, r.plan.targetShortSide)
        assertTrue(r.plan.stages.all { it.kind == StageKind.CLASSIC })
    }

    @Test
    fun noPlanEverClaimsAiInThisBuild() {
        for (mode in Mode.entries) {
            val r = planner.plan(mode, video(640, 360), noisySoft, CustomSettings(denoise = Level.LIGHT))
            if (r is PlanResult.Ready) assertTrue(r.plan.stages.none { it.kind == StageKind.AI })
        }
    }
}
