package com.clipboost.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.clipboost.app.ClipBoostApp
import com.clipboost.app.MainActivity
import com.clipboost.app.R
import com.clipboost.app.pipeline.EngineResult
import com.clipboost.core.job.JobError
import com.clipboost.core.job.JobState
import com.clipboost.core.job.OutputSizeEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.Job as CoroutineJob

/**
 * Foreground service that processes queued jobs one at a time, so work continues when the
 * user leaves the app. If Android kills the process anyway, JobRepository marks the job
 * INTERRUPTED on next launch and the user can restart it.
 */
class ProcessingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private var current: CoroutineJob? = null
    private var currentId: String? = null
    private var lastStartId = 0

    private val container get() = (application as ClipBoostApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (id in queue) {
                val run = scope.launch { process(id) }
                current = run
                currentId = id
                run.join()
                current = null
                currentId = null
                if (queue.isEmpty) {
                    ServiceCompat.stopForeground(this@ProcessingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf(lastStartId)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val id = intent?.getStringExtra(EXTRA_JOB_ID)
        when (intent?.action) {
            ACTION_START -> if (id != null) {
                startInForeground(progressNotification(id, null))
                queue.trySend(id)
            }
            ACTION_CANCEL -> if (id != null) {
                cancel(id)
                if (current == null && queue.isEmpty) stopSelf(startId)
            }
        }
        if (id == null) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 time limit for this service type: stop cleanly; the job is marked INTERRUPTED.
        currentId?.let { id -> scope.launch { container.jobs.transition(id, JobState.INTERRUPTED) } }
        current?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun cancel(id: String) {
        if (currentId == id) {
            current?.cancel()
        } else {
            scope.launch { container.jobs.transition(id, JobState.CANCELED) }
        }
    }

    private suspend fun process(id: String) {
        val jobs = container.jobs
        val job = jobs.get(id)?.takeIf { it.state == JobState.CREATED } ?: return
        val output = File(container.outputDir, "$id.mp4")
        try {
            jobs.transition(id, JobState.PREPARING)

            val needed = OutputSizeEstimator.estimateBytes(job.info, job.plan)
            if (container.outputDir.usableSpace < needed) return fail(id, JobError.NO_SPACE)
            if (!canOpen(job.sourceUri)) return fail(id, JobError.SOURCE_UNAVAILABLE)

            jobs.transition(id, JobState.PROCESSING)
            val result = container.engine.run(job, output) { progress ->
                jobs.setProgress(id, progress)
                notify(NOTIFICATION_ID, progressNotification(id, progress))
            }
            when (result) {
                is EngineResult.Failure -> {
                    output.delete()
                    fail(id, result.error)
                }
                EngineResult.Success -> {
                    jobs.transition(id, JobState.FINALIZING)
                    if (!output.exists() || output.length() == 0L) return fail(id, JobError.UNKNOWN)
                    jobs.transition(id, JobState.DONE) { it.copy(outputPath = output.absolutePath, progress = 100) }
                    notifyDone(id, job.displayName)
                }
            }
        } catch (e: CancellationException) {
            output.delete()
            withContext(NonCancellable) {
                if (jobs.get(id)?.state?.isActive == true) jobs.transition(id, JobState.CANCELED)
            }
            throw e
        }
    }

    private suspend fun fail(id: String, error: JobError) {
        container.jobs.transition(id, JobState.FAILED) { it.copy(error = error) }
    }

    private fun canOpen(uri: String): Boolean = try {
        contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun progressNotification(id: String, progress: Int?): Notification {
        val cancelIntent = PendingIntent.getService(
            this, id.hashCode(),
            Intent(this, ProcessingService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ClipBoostApp.CHANNEL_PROCESSING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Enhancing video")
            .setContentText(progress?.let { "$it%" } ?: "Preparing…")
            .setProgress(100, progress ?: 0, progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(id))
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }

    private fun notifyDone(id: String, name: String) {
        val n = NotificationCompat.Builder(this, ClipBoostApp.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Video ready")
            .setContentText("Tap to compare and save $name")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(id))
            .build()
        notify(DONE_NOTIFICATION_ID, n)
    }

    private fun notify(notificationId: Int, n: Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.areNotificationsEnabled()) nm.notify(notificationId, n)
    }

    private fun openAppIntent(id: String): PendingIntent = PendingIntent.getActivity(
        this, id.hashCode(),
        Intent(this, MainActivity::class.java).putExtra(EXTRA_JOB_ID, id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_START = "com.clipboost.action.START"
        const val ACTION_CANCEL = "com.clipboost.action.CANCEL"
        const val EXTRA_JOB_ID = "job_id"
        private const val NOTIFICATION_ID = 1
        private const val DONE_NOTIFICATION_ID = 2

        fun start(context: Context, jobId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProcessingService::class.java).setAction(ACTION_START).putExtra(EXTRA_JOB_ID, jobId),
            )
        }

        fun cancel(context: Context, jobId: String) {
            context.startService(Intent(context, ProcessingService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB_ID, jobId))
        }
    }
}
