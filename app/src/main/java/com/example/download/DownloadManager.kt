package com.example.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class DownloadManager(private val context: Context) {
    private companion object {
        const val ARTWORK_MAX_DIMENSION = 512
        const val ARTWORK_JPEG_QUALITY = 82
        const val MAX_ARTWORK_DOWNLOAD_BYTES = 3L * 1024L * 1024L
        const val MIN_FREE_SPACE_AFTER_DOWNLOAD_BYTES = 50L * 1024L * 1024L
    }

    private val client = OkHttpClient()

    fun downloadedFile(driveFileId: String): File {
        return File(downloadsDirectory(), "$driveFileId.opus")
    }

    fun artworkFile(driveFileId: String): File {
        return File(downloadsDirectory(), "$driveFileId.jpg")
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
        if (destinationFile.exists() && destinationFile.length() > 0L) {
            emit(1.0f)
            return@flow
        }
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
                if (contentLength > 0 && downloadsDir.usableSpace < contentLength + MIN_FREE_SPACE_AFTER_DOWNLOAD_BYTES) {
                    throw Exception("Not enough free space to download this track")
                }

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

    fun downloadAlbumArt(driveFileId: String, albumArtUrl: String?): File? {
        val normalizedUrl = albumArtUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (
            normalizedUrl.equals("null", ignoreCase = true) ||
            normalizedUrl.equals("none", ignoreCase = true) ||
            normalizedUrl.equals("undefined", ignoreCase = true) ||
            normalizedUrl.equals("n/a", ignoreCase = true) ||
            normalizedUrl.startsWith("android.resource://")
        ) {
            return null
        }

        val destinationFile = artworkFile(driveFileId)
        if (destinationFile.exists() && destinationFile.length() > 0L) {
            return destinationFile
        }

        val request = Request.Builder().url(normalizedUrl).build()
        val tempFile = File(downloadsDirectory(), "$driveFileId.jpg.part")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val contentLength = body.contentLength()
                if (contentLength > MAX_ARTWORK_DOWNLOAD_BYTES) return null
                val bytes = body.bytes()
                if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_DOWNLOAD_BYTES) return null
                val bitmap = decodeScaledBitmap(bytes) ?: return null
                FileOutputStream(tempFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, output)
                }
                bitmap.recycle()
            }
            if (destinationFile.exists() && !destinationFile.delete()) {
                tempFile.delete()
                return null
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.delete()
                return null
            }
            destinationFile
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    fun deleteTrack(driveFileId: String): Boolean {
        val file = downloadedFile(driveFileId)
        val artwork = artworkFile(driveFileId)
        val deletedAudio = file.exists() && file.delete()
        val deletedArtwork = artwork.exists() && artwork.delete()
        return deletedAudio || deletedArtwork
    }

    private fun decodeScaledBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxDimension = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDimension / sampleSize > ARTWORK_MAX_DIMENSION) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
