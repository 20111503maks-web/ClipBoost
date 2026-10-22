package com.clipboost.app.pipeline

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.clipboost.core.enhance.EnhancementPlan
import com.clipboost.core.enhance.Level
import com.clipboost.core.job.Job
import com.clipboost.core.job.JobError
import com.clipboost.core.model.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

sealed interface EngineResult {
    data object Success : EngineResult
    data class Failure(val error: JobError) : EngineResult
}

/**
 * Runs one job through Media3 Transformer: hardware decode → GPU effects → hardware encode.
 * Audio is passed through untouched whenever the output container accepts it.
 * Progress is the Transformer's own measurement — nothing is simulated.
 */
@OptIn(UnstableApi::class)
class ProcessingEngine(private val context: Context) {

    /** Must be called from the main thread's dispatcher scope; Transformer needs a Looper thread. */
    suspend fun run(job: Job, output: File, onProgress: (Int?) -> Unit): EngineResult = withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            val done = CompletableDeferred<EngineResult>()
            val transformer = buildTransformer(job, done)
            output.parentFile?.mkdirs()
            output.delete()
            transformer.start(buildItem(job), output.absolutePath)

            val poller = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    val state = transformer.getProgress(holder)
                    onProgress(if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null)
                    delay(PROGRESS_INTERVAL_MS)
                }
            }
            try {
                done.await()
            } catch (e: CancellationException) {
                transformer.cancel()
                output.delete()
                throw e
            } finally {
                poller.cancel()
            }
        }
    }

    private fun buildItem(job: Job): EditedMediaItem {
        val plan = job.plan
        val videoEffects = buildList<Effect> {
            if (plan.denoise != Level.OFF) add(DenoiseEffect(plan.denoise.denoiseRangeSigma))
            if (plan.sharpen != Level.OFF) add(SharpenEffect(plan.sharpen.sharpenAmount))
            // Short-side scaling keeps the aspect ratio exactly and is independent of the rotation tag.
            plan.targetShortSide?.let { add(Presentation.createForShortSide(it)) }
        }
        return EditedMediaItem.Builder(MediaItem.fromUri(job.sourceUri))
            .setEffects(Effects(/* audioProcessors= */ emptyList(), videoEffects))
            .build()
    }

    private fun buildTransformer(job: Job, done: CompletableDeferred<EngineResult>): Transformer {
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .apply { targetBitrate(job.info, job.plan)?.let { setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(it).build()) } }
            .build()
        return Transformer.Builder(context)
            .setVideoMimeType(job.plan.codec.mime)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(EngineResult.Success)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done.complete(EngineResult.Failure(mapError(exportException)))
                }
            })
            .build()
    }

    /**
     * Keep at least the source's bitrate (scaled by output area) so enhancing never means
     * "re-encoded at lower quality". Null lets the encoder pick when the source bitrate is unknown.
     */
    private fun targetBitrate(info: VideoInfo, plan: EnhancementPlan): Int? {
        val source = info.bitrate ?: return null
        val areaScale = plan.targetShortSide?.let { (it.toFloat() / info.shortSide).let { r -> r * r } } ?: 1f
        return (source * areaScale * 1.15f).roundToInt().coerceIn(MIN_BITRATE, MAX_BITRATE)
    }

    private fun mapError(e: ExportException): JobError = when (e.errorCode) {
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ExportException.ERROR_CODE_DECODER_INIT_FAILED -> JobError.UNSUPPORTED_CODEC
        ExportException.ERROR_CODE_DECODING_FAILED -> JobError.DAMAGED_FILE
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODING_FAILED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> JobError.ENCODER_FAILED
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
        ExportException.ERROR_CODE_IO_NO_PERMISSION -> JobError.SOURCE_UNAVAILABLE
        else -> if (isOutOfSpace(e)) JobError.NO_SPACE else JobError.UNKNOWN
    }

    private fun isOutOfSpace(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is IOException && t.message?.contains("ENOSPC") == true) return true
            t = t.cause
        }
        return false
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
        const val MIN_BITRATE = 2_000_000
        const val MAX_BITRATE = 80_000_000
    }
}
