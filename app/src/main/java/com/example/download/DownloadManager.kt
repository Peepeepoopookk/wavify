package com.example.download

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadManager(private val context: Context) {
    private val client = OkHttpClient()

    fun downloadedFile(driveFileId: String): File {
        return File(downloadsDirectory(), "$driveFileId.opus")
    }

    private fun downloadsDirectory(): File {
        return File(context.filesDir, "downloads").also { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Failed to create downloads directory")
            }
        }
    }

    fun downloadTrack(driveFileId: String): Flow<Float> = flow {
        val downloadsDir = downloadsDirectory()

        val destinationFile = downloadedFile(driveFileId)
        val tempFile = File(downloadsDir, "$driveFileId.opus.part")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        
        val url = "${BuildConfig.WAVIFY_PROXY_BASE_URL}/stream/$driveFileId"
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed to download file: ${response.code}")
                }

                val body = response.body ?: throw Exception("Response body is null")
                val contentLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied = 0L
                        var bytesRead: Int

                        emit(0.0f)

                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead

                            if (contentLength > 0) {
                                val progress = (bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                emit(progress)
                            }
                        }
                    }
                }
            }
            if (destinationFile.exists() && !destinationFile.delete()) {
                throw Exception("Failed to replace existing download")
            }
            if (!tempFile.renameTo(destinationFile)) {
                throw Exception("Failed to finalize downloaded file")
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
        emit(1.0f)
    }.flowOn(Dispatchers.IO)

    fun deleteTrack(driveFileId: String): Boolean {
        val file = downloadedFile(driveFileId)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
