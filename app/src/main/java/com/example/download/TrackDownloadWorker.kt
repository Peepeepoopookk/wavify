package com.example.download

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.DownloadedTrackEntity
import com.example.data.local.WavifyDatabase
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest

class TrackDownloadWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID).orEmpty()
        val driveFileId = inputData.getString(KEY_DRIVE_FILE_ID).orEmpty()
        if (trackId.isBlank() || driveFileId.isBlank()) {
            return Result.failure()
        }

        val downloadManager = DownloadManager(applicationContext)

        return try {
            var lastProgress = -1
            setProgress(workDataOf(KEY_PROGRESS to 0))

            downloadManager.downloadTrack(driveFileId).collectLatest { progress ->
                val progressPercent = (progress * 100f).roundToInt().coerceIn(0, 100)
                if (progressPercent != lastProgress) {
                    lastProgress = progressPercent
                    setProgress(workDataOf(KEY_PROGRESS to progressPercent))
                }
            }

            val downloadedFile = downloadManager.downloadedFile(driveFileId)
            val fileSize = downloadedFile.length()
            if (!downloadedFile.exists() || fileSize <= 0L) {
                return Result.retry()
            }

            WavifyDatabase.getDatabase(applicationContext).downloadedTrackDao().insert(
                DownloadedTrackEntity(
                    driveFileId = driveFileId,
                    localFilePath = downloadedFile.absolutePath,
                    downloadedAt = System.currentTimeMillis(),
                    fileSizeBytes = fileSize
                )
            )

            Result.success(
                workDataOf(
                    KEY_TRACK_ID to trackId,
                    KEY_DRIVE_FILE_ID to driveFileId,
                    KEY_LOCAL_FILE_PATH to downloadedFile.absolutePath,
                    KEY_FILE_SIZE_BYTES to fileSize,
                    KEY_PROGRESS to 100
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Track download failed on attempt $runAttemptCount", error)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_TRACK_ID to trackId,
                        KEY_DRIVE_FILE_ID to driveFileId
                    )
                )
            }
        }
    }

    companion object {
        const val TAG = "WavifyTrackDownload"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_DRIVE_FILE_ID = "drive_file_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_LOCAL_FILE_PATH = "local_file_path"
        const val KEY_FILE_SIZE_BYTES = "file_size_bytes"

        private const val TRACK_TAG_PREFIX = "wavify-track-download-track-"
        private const val MAX_RETRY_ATTEMPTS = 2

        fun uniqueWorkName(driveFileId: String): String = "wavify-track-download-$driveFileId"

        fun trackTag(trackId: String): String = "$TRACK_TAG_PREFIX$trackId"

        fun trackIdFromTags(tags: Set<String>): String? {
            return tags.firstOrNull { it.startsWith(TRACK_TAG_PREFIX) }?.removePrefix(TRACK_TAG_PREFIX)
        }

        fun buildRequest(trackId: String, driveFileId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<TrackDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TRACK_ID to trackId,
                        KEY_DRIVE_FILE_ID to driveFileId
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .addTag(trackTag(trackId))
                .build()
        }
    }
}
