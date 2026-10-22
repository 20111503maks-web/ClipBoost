package com.clipboost.app.data

import android.util.Log
import com.clipboost.core.job.Job
import com.clipboost.core.job.JobState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Jobs persisted as one small JSON file, so a job ID survives app restarts.
 * Writes are atomic (temp file + rename); progress ticks stay in memory only.
 */
class JobRepository(
    private val file: File,
    private val outputDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _jobs = MutableStateFlow(loadAndRecover())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    fun get(id: String): Job? = _jobs.value.firstOrNull { it.id == id }

    suspend fun add(job: Job) = mutate { list -> pruned(listOf(job) + list.filterNot { it.id == job.id }) }

    suspend fun update(id: String, transform: (Job) -> Job): Job? {
        var result: Job? = null
        mutate { list ->
            list.map { if (it.id == id) transform(it).also { j -> result = j } else it }
        }
        return result
    }

    /** Moves a job to [next] if the state machine allows it; ignores stale/illegal moves. */
    suspend fun transition(id: String, next: JobState, extra: (Job) -> Job = { it }): Job? =
        update(id) { job -> if (job.state.canMoveTo(next)) extra(job.moveTo(next, clock())) else job }

    /** Encoder progress: frequent, so memory only — it's re-derived on restart anyway. */
    fun setProgress(id: String, progress: Int?) {
        _jobs.update { list -> list.map { if (it.id == id && it.state.isActive) it.copy(progress = progress) else it } }
    }

    suspend fun delete(id: String) {
        mutate { list ->
            list.firstOrNull { it.id == id }?.outputPath?.let { File(it).delete() }
            list.filterNot { it.id == id }
        }
    }

    private suspend fun mutate(block: (List<Job>) -> List<Job>) = mutex.withLock {
        val next = block(_jobs.value)
        _jobs.value = next
        withContext(Dispatchers.IO) { persist(next) }
    }

    private fun pruned(list: List<Job>): List<Job> {
        if (list.size <= MAX_JOBS) return list
        list.drop(MAX_JOBS).forEach { old -> old.outputPath?.let { File(it).delete() } }
        return list.take(MAX_JOBS)
    }

    private fun persist(list: List<Job>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    /**
     * A job that was active when the process died can't still be running — the service lives in
     * this process. Mark it INTERRUPTED so the UI can offer a restart instead of a frozen progress bar.
     */
    private fun loadAndRecover(): List<Job> {
        val loaded = try {
            if (file.exists()) json.decodeFromString<List<Job>>(file.readText()) else emptyList()
        } catch (e: Exception) {
            // Never crash on a bad store; keep the file aside for debugging. No video content is in it.
            Log.w(TAG, "Job store unreadable, starting empty", e)
            file.renameTo(File(file.parentFile, file.name + ".broken"))
            emptyList()
        }
        val now = clock()
        val recovered = loaded.map { job ->
            if (job.state.isActive) {
                job.outputPath?.let { File(it).delete() }
                job.moveTo(JobState.INTERRUPTED, now).copy(outputPath = null, progress = null)
            } else {
                job
            }
        }
        // Remove stray outputs no job points at (e.g. a half-written file from a crash).
        val referenced = recovered.mapNotNull { it.outputPath }.toSet()
        outputDir.listFiles()?.filter { it.absolutePath !in referenced }?.forEach { it.delete() }
        if (recovered != loaded) persist(recovered)
        return recovered
    }

    private companion object {
        const val TAG = "JobRepository"
        const val MAX_JOBS = 20
    }
}
