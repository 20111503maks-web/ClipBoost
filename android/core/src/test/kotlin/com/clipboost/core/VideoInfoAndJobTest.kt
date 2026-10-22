package com.clipboost.core

import com.clipboost.core.enhance.EnhancementPlan
import com.clipboost.core.enhance.Level
import com.clipboost.core.enhance.Mode
import com.clipboost.core.enhance.OutputCodec
import com.clipboost.core.job.Job
import com.clipboost.core.job.JobIds
import com.clipboost.core.job.JobState
import com.clipboost.core.model.Orientation
import com.clipboost.core.model.VideoInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoInfoAndJobTest {
    private fun info(w: Int, h: Int, rot: Int = 0, fps: Float? = 30f, ms: Long = 42_000) = VideoInfo(
        w, h, rot, ms, fps, "video/hevc", null, null, null, false,
    )

    @Test
    fun rotationTagSwapsDisplaySize() {
        val phonePortrait = info(1920, 1080, rot = 90)
        assertEquals(1080, phonePortrait.displayWidth)
        assertEquals(1920, phonePortrait.displayHeight)
        assertEquals(Orientation.PORTRAIT, phonePortrait.orientation)
        assertEquals("9:16", phonePortrait.aspectLabel())
        assertEquals("1080p", phonePortrait.resolutionLabel())
    }

    @Test
    fun labels() {
        assertEquals(Orientation.SQUARE, info(720, 720).orientation)
        assertEquals("4K", info(3840, 2160).resolutionLabel())
        assertEquals("00:42", info(1, 1).durationLabel())
        assertEquals("1:01:05", info(1, 1, ms = 3_665_000).durationLabel())
        assertEquals("30 FPS", info(1, 1, fps = 29.99f).frameRateLabel())
        assertEquals("? FPS", info(1, 1, fps = null).frameRateLabel())
        assertEquals("HEVC", info(1, 1).codecLabel())
    }

    @Test
    fun jobStateMachine() {
        assertTrue(JobState.CREATED.canMoveTo(JobState.PREPARING))
        assertFalse(JobState.CREATED.canMoveTo(JobState.DONE))
        assertFalse(JobState.DONE.canMoveTo(JobState.PROCESSING))
        assertTrue(JobState.INTERRUPTED.canMoveTo(JobState.CREATED))
    }

    @Test
    fun jobRoundTripsThroughJson() {
        val job = Job(
            id = JobIds.newId(), sourceUri = "content://x/1", displayName = "a.mp4", info = info(1920, 1080),
            plan = EnhancementPlan(Mode.DENOISE, Level.LIGHT, Level.OFF, null, OutputCodec.H264),
            createdAt = 1, updatedAt = 1,
        )
        val json = Json.encodeToString(listOf(job))
        assertEquals(listOf(job), Json.decodeFromString<List<Job>>(json))
        assertTrue(job.id.matches(Regex("cb_[0-9a-f]{10}")))
    }
}
