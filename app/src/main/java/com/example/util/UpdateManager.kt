package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "UpdateManager"

data class AppReleaseInfo(
    val versionName: String,
    val tagName: String,
    val versionCode: Int? = null,
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val apkUrl: String,
    val apkName: String = "wavify-update.apk",
    val apkSize: Long = 0L,
    val publishedAt: String? = null
)

data class AppInstalledVersion(
    val versionName: String,
    val versionCode: Long
)

sealed class UpdateCheckResult {
    data class Available(val release: AppReleaseInfo, val currentVersion: String) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val exception: Throwable, val message: String) : UpdateCheckResult()
}

class UpdateManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Dynamically resolves the current installed package's version name and version code.
     */
    fun getCurrentAppVersion(): AppInstalledVersion {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val name = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
            val code = PackageInfoCompat.getLongVersionCode(packageInfo)
            AppInstalledVersion(name, code)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load package info, falling back to BuildConfig", e)
            AppInstalledVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
        }
    }

    /**
     * Checks for updates by querying dashboard API first, then falling back to GitHub Releases API.
     */
    suspend fun checkForUpdates(
        customEndpoint: String? = null,
        forceCheck: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installed = getCurrentAppVersion()
        val currentVersion = installed.versionName
        val currentVersionCode = installed.versionCode

        val endpoints = listOfNotNull(
            customEndpoint,
            BuildConfig.GITHUB_RELEASES_URL.takeIf { it.isNotBlank() },
            "https://api.github.com/repos/Peepeepoopookk/wavify/releases/latest",
            BuildConfig.DASHBOARD_RELEASE_URL.takeIf { it.isNotBlank() }
        ).distinct()

        var lastException: Throwable? = null
        var lastErrorMessage = "Failed to check for updates"
        var hasSuccessfulCheck = false

        for (endpoint in endpoints) {
            try {
                Log.d(TAG, "Checking for update at: $endpoint (current: $currentVersion, code: $currentVersionCode, forceCheck: $forceCheck)")
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/vnd.github.v3+json, application/json")
                    .header("User-Agent", "Wavify-Android-App/$currentVersion")
                    .build()

                val releaseInfo = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Endpoint $endpoint responded with code ${response.code}")
                        lastErrorMessage = "HTTP ${response.code}: ${response.message}"
                        return@use null
                    }

                    val bodyString = response.body?.string() ?: return@use null
                    parseReleaseJson(bodyString)
                } ?: continue

                hasSuccessfulCheck = true

                val isNewer = isNewerVersion(
                    remoteVersion = releaseInfo.versionName,
                    localVersion = currentVersion,
                    remoteVersionCode = releaseInfo.versionCode,
                    localVersionCode = currentVersionCode
                )
                Log.d(TAG, "Found release: ${releaseInfo.versionName} (code: ${releaseInfo.versionCode}), current: $currentVersion (code: $currentVersionCode), isNewer: $isNewer")

                if (isNewer && releaseInfo.apkUrl.isNotBlank()) {
                    return@withContext UpdateCheckResult.Available(releaseInfo, currentVersion)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking update from $endpoint: ${e.message}")
                lastException = e
                lastErrorMessage = e.localizedMessage ?: "Network error"
            }
        }

        if (hasSuccessfulCheck) {
            UpdateCheckResult.UpToDate(currentVersion)
        } else {
            UpdateCheckResult.Error(lastException ?: IllegalStateException(lastErrorMessage), lastErrorMessage)
        }
    }

    /**
     * Parses release JSON from GitHub Releases or custom dashboard formats.
     */
    private fun parseReleaseJson(jsonString: String): AppReleaseInfo? {
        return try {
            val json = JSONObject(jsonString)

            val tagName = json.optString("tag_name", json.optString("tag", "v1.0.0"))
            val rawVersionName = json.optString(
                "version_name",
                json.optString("version", tagName)
            ).ifBlank { tagName }

            // Clean version name (strip leading "Wavify", "v", etc.)
            val versionName = rawVersionName
                .replace(Regex("(?i)wavify"), "")
                .replace(Regex("^[vV]"), "")
                .trim()
                .ifBlank { rawVersionName.removePrefix("v").trim() }

            val title = json.optString("name", json.optString("title", "Wavify $tagName"))
            val notes = json.optString("body", json.optString("release_notes", json.optString("changelog", "")))
            val versionCode = if (json.has("version_code")) json.optInt("version_code") else null
            val publishedAt = when {
                json.has("published_at") -> json.optString("published_at")
                json.has("created_at") -> json.optString("created_at")
                else -> null
            }

            var apkUrl = json.optString("apk_download_url", json.optString("download_url", json.optString("apk_url", json.optString("url", ""))))
            var apkName = json.optString("apk_name", "Wavify-$versionName.apk")
            var apkSize = 0L

            if (json.has("assets")) {
                val assets = json.optJSONArray("assets") ?: JSONArray()
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        apkUrl = asset.optString("browser_download_url", apkUrl)
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                Log.w(TAG, "No APK asset found in release payload")
                return null
            }

            AppReleaseInfo(
                versionName = versionName,
                tagName = tagName,
                versionCode = versionCode,
                releaseTitle = title,
                releaseNotes = notes,
                apkUrl = apkUrl,
                apkName = apkName,
                apkSize = apkSize,
                publishedAt = publishedAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse release JSON", e)
            null
        }
    }

    /**
     * Cleans extraneous prefixes such as "Wavify", "v", "V", and leading/trailing whitespace.
     */
    fun cleanVersionString(version: String): String {
        return version
            .replace(Regex("(?i)wavify"), "")
            .trim()
            .replace(Regex("^[vV]"), "")
            .trim()
    }

    /**
     * Parses a version string into a list of integers, e.g. "1.0.1" -> [1, 0, 1].
     */
    fun normalizeVersionString(version: String): List<Int> {
        val cleaned = cleanVersionString(version)
        val parts = cleaned.split(".").mapNotNull { segment ->
            val trimmedSegment = segment.trim().dropWhile { !it.isDigit() }
            val digits = trimmedSegment.takeWhile { it.isDigit() }
            digits.toIntOrNull()
        }
        return if (parts.isEmpty()) listOf(0) else parts
    }

    /**
     * Compares semantic versions (e.g., "1.0.1" vs "1.0.0").
     * Returns true ONLY if remoteVersion is strictly greater than localVersion.
     */
    fun isNewerVersion(
        remoteVersion: String,
        localVersion: String,
        remoteVersionCode: Int? = null,
        localVersionCode: Long? = null
    ): Boolean {
        val cleanLocal = cleanVersionString(localVersion)
        val cleanRemote = cleanVersionString(remoteVersion)

        // Strict equality check on clean version strings
        if (cleanRemote.equals(cleanLocal, ignoreCase = true)) {
            if (remoteVersionCode != null && localVersionCode != null && localVersionCode > 0) {
                return remoteVersionCode > localVersionCode
            }
            return false
        }

        // Semantic integer segment comparison
        val remoteParts = normalizeVersionString(remoteVersion)
        val localParts = if (cleanLocal.equals("dev", ignoreCase = true)) {
            listOf(0, 0, 0)
        } else {
            normalizeVersionString(localVersion)
        }

        val maxParts = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxParts) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        // If numeric segments are identical, check versionCode if present
        if (remoteVersionCode != null && localVersionCode != null && localVersionCode > 0) {
            return remoteVersionCode > localVersionCode
        }

        return false
    }

    /**
     * Downloads the APK file to the app's internal updates cache directory with streaming progress.
     */
    suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, release.apkName.ifBlank { "wavify-${release.versionName}.apk" })

            if (targetFile.exists()) {
                targetFile.delete()
            }

            Log.d(TAG, "Starting APK download from: ${release.apkUrl} to: ${targetFile.absolutePath}")

            val request = Request.Builder()
                .url(release.apkUrl)
                .header("User-Agent", "Wavify-Android-App/${getCurrentAppVersion().versionName}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Download failed with HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                IllegalStateException("Empty response body from APK download URL")
            )

            val contentLength = if (body.contentLength() > 0) body.contentLength() else release.apkSize
            var bytesCopied = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val progress = if (contentLength > 0) {
                            (bytesCopied.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        onProgress(progress, bytesCopied, contentLength)
                    }
                    output.flush()
                }
            }

            Log.d(TAG, "APK download complete: ${targetFile.length()} bytes")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            Result.failure(e)
        }
    }

    /**
     * Launches the Android Package Installer intent using FileProvider content URI.
     */
    fun installApk(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(IllegalStateException("APK file does not exist or is empty"))
            }

            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            Log.d(TAG, "Launching APK install for URI: $apkUri (authority: $authority)")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start APK installation", e)
            Result.failure(e)
        }
    }
}
