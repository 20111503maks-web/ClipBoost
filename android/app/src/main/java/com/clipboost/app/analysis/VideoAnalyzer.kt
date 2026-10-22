package com.clipboost.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.clipboost.core.analysis.FrameMetrics
import com.clipboost.core.job.JobError
import com.clipboost.core.model.QualityMetrics
import com.clipboost.core.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AnalysisResult {
    data class Success(val displayName: String, val info: VideoInfo, val metrics: QualityMetrics?) : AnalysisResult
    data class Failure(val error: JobError, val detail: String? = null) : AnalysisResult
}

/** Reads real stream facts and measures picture quality on a few sample frames. Never throws. */
class VideoAnalyzer(private val context: Context) {

    suspend fun analyze(uri: Uri): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            analyzeOrThrow(uri)
        } catch (e: SecurityException) {
            AnalysisResult.Failure(JobError.SOURCE_UNAVAILABLE)
        } catch (e: Exception) {
            // Only the exception type is logged — never the file name or content.
            Log.w(TAG, "Analysis failed: ${e.javaClass.simpleName}")
            AnalysisResult.Failure(JobError.DAMAGED_FILE)
        }
    }

    private fun analyzeOrThrow(uri: Uri): AnalysisResult {
        val (name, size) = queryNameAndSize(uri)

        val (found, audioMime) = readTracks(uri)
        val videoFormat = found ?: return AnalysisResult.Failure(JobError.DAMAGED_FILE, "No video track")

        val mime = videoFormat.getString(MediaFormat.KEY_MIME)!!
        if (!hasDecoder(videoFormat, mime)) {
            return AnalysisResult.Failure(JobError.UNSUPPORTED_CODEC, VideoInfo.codecName(mime))
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)

            val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) videoFormat.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
            if (width <= 0 || height <= 0 || durationMs <= 0) {
                return AnalysisResult.Failure(JobError.DAMAGED_FILE, "Missing size or duration")
            }
            val frameCount = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
            val declaredFps = videoFormat.getNumber(MediaFormat.KEY_FRAME_RATE)?.toFloat()?.takeIf { it in 1f..480f }
            val fps = declaredFps ?: frameCount?.let { (it * 1000f / durationMs).takeIf { f -> f in 1f..480f } }

            val transfer = if (videoFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) videoFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else -1
            val info = VideoInfo(
                width = width,
                height = height,
                rotationDegrees = ((rotation % 360) + 360) % 360,
                durationMs = durationMs,
                frameRate = fps,
                videoMime = mime,
                audioMime = audioMime,
                sizeBytes = size,
                bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                isHdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG,
            )
            return AnalysisResult.Success(name ?: "video", info, measureQuality(retriever, durationMs))
        } finally {
            retriever.release()
        }
    }

    /** First video track format and first audio mime, straight from the container. */
    private fun readTracks(uri: Uri): Pair<MediaFormat?, String?> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var video: MediaFormat? = null
            var audioMime: String? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && video == null) video = f
                else if (mime.startsWith("audio/") && audioMime == null) audioMime = mime
            }
            return video to audioMime
        } finally {
            extractor.release()
        }
    }

    private fun hasDecoder(format: MediaFormat, mime: String): Boolean {
        // Dolby Vision streams carry an HEVC/AVC base layer that Media3 falls back to.
        if (mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) return true
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val exact = MediaFormat(format).apply { removeKey(MediaFormat.KEY_FRAME_RATE) }
        if (list.findDecoderForFormat(exact) != null) return true
        // Some containers declare odd profile/level values; retry with just the essentials.
        val basic = MediaFormat.createVideoFormat(mime, format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT))
        return list.findDecoderForFormat(basic) != null
    }

    /** Median noise/sharpness over three frames, at ~640 px. Null if frames can't be decoded. */
    private fun measureQuality(retriever: MediaMetadataRetriever, durationMs: Long): QualityMetrics? = try {
        val samples = listOf(0.2, 0.5, 0.8).mapNotNull { fraction ->
            val tUs = (durationMs * fraction * 1000).toLong()
            retriever.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, ANALYSIS_SIZE, ANALYSIS_SIZE)
                ?.let { bmp -> measure(bmp).also { bmp.recycle() } }
        }
        FrameMetrics.combine(samples)
    } catch (e: Exception) {
        Log.w(TAG, "Frame sampling failed: ${e.javaClass.simpleName}")
        null
    }

    private fun measure(bitmap: Bitmap): QualityMetrics {
        val bmp = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        if (bmp !== bitmap) bmp.recycle()
        val luma = FrameMetrics.lumaFromArgb(pixels)
        return QualityMetrics(
            noiseSigma = FrameMetrics.estimateNoiseSigma(luma, w, h),
            sharpness = FrameMetrics.laplacianVariance(luma, w, h),
        )
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> {
        if (uri.scheme != "content") return uri.lastPathSegment to null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0 && !c.isNull(nameIdx)) c.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else null
                return name to size
            }
        }
        return null to null
    }

    private companion object {
        const val TAG = "VideoAnalyzer"
        const val ANALYSIS_SIZE = 640
    }
}
