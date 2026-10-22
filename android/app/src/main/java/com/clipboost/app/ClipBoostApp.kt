package com.clipboost.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.clipboost.app.analysis.VideoAnalyzer
import com.clipboost.app.data.JobRepository
import com.clipboost.app.pipeline.ProcessingEngine
import com.clipboost.core.enhance.Planner
import java.io.File

class ClipBoostApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROCESSING, getString(R.string.channel_processing), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL_PROCESSING = "processing"
        const val CHANNEL_DONE = "done"
    }
}

/** Manual dependency wiring — small enough that a DI framework would only add weight. */
class AppContainer(app: Application) {
    val outputDir: File = File(app.filesDir, "outputs").apply { mkdirs() }
    val jobs = JobRepository(File(app.filesDir, "jobs.json"), outputDir)
    val analyzer = VideoAnalyzer(app)
    val engine = ProcessingEngine(app)
    val planner = Planner()
}
