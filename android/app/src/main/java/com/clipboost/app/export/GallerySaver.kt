package com.clipboost.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Copies a finished result into the shared gallery (Movies/ClipBoost). No storage permission needed on API 29+. */
object GallerySaver {

    suspend fun save(context: Context, file: File, sourceName: String): Uri = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) throw IOException("Result file is missing")
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outputName(sourceName))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipBoost")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Gallery refused the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Can't open gallery file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    fun outputName(sourceName: String): String {
        val base = sourceName.substringBeforeLast('.').ifBlank { "video" }
        return "${base}_clipboost.mp4"
    }
}
