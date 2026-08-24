package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    private fun cleanVersionString(version: String): String {
        return version
            .replace(Regex("(?i)wavify"), "")
            .trim()
            .replace(Regex("^[vV]"), "")
            .trim()
    }

    private fun normalizeVersionString(version: String): List<Int> {
        val cleaned = cleanVersionString(version)
        val parts = cleaned.split(".").mapNotNull { segment ->
            val trimmedSegment = segment.trim().dropWhile { !it.isDigit() }
            val digits = trimmedSegment.takeWhile { it.isDigit() }
            digits.toIntOrNull()
        }
        return if (parts.isEmpty()) listOf(0) else parts
    }

    private fun isNewerVersion(
        remoteVersion: String,
        localVersion: String,
        remoteVersionCode: Int? = null,
        localVersionCode: Long? = null
    ): Boolean {
        val cleanLocal = cleanVersionString(localVersion)
        val cleanRemote = cleanVersionString(remoteVersion)

        if (cleanRemote.equals(cleanLocal, ignoreCase = true)) {
            if (remoteVersionCode != null && localVersionCode != null && localVersionCode > 0) {
                return remoteVersionCode > localVersionCode
            }
            return false
        }

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

        if (remoteVersionCode != null && localVersionCode != null && localVersionCode > 0) {
            return remoteVersionCode > localVersionCode
        }

        return false
    }

    @Test
    fun sameVersionReturnsFalse() {
        assertFalse(isNewerVersion("v1.0.1", "1.0.1"))
        assertFalse(isNewerVersion("1.0.1", "1.0.1"))
        assertFalse(isNewerVersion("v1.0.1", "v1.0.1"))
        assertFalse(isNewerVersion("Wavify v1.0.1", "1.0.1"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.1", "Wavify 1.0.1"))
    }

    @Test
    fun olderRemoteVersionReturnsFalse() {
        assertFalse(isNewerVersion("v1.0.0", "1.0.1"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(isNewerVersion("v0.9.9", "1.0.1"))
    }

    @Test
    fun newerRemoteVersionReturnsTrue() {
        assertTrue(isNewerVersion("v1.0.2", "1.0.1"))
        assertTrue(isNewerVersion("1.0.2", "1.0.1"))
        assertTrue(isNewerVersion("v1.1.0", "1.0.1"))
        assertTrue(isNewerVersion("v2.0.0", "1.0.1"))
        assertTrue(isNewerVersion("Wavify v1.0.2", "1.0.1"))
        assertTrue(isNewerVersion("Wavify 1.0.2", "v1.0.1"))
    }

    @Test
    fun devLocalVersionHandlesGracefully() {
        assertTrue(isNewerVersion("v1.0.1", "dev"))
        assertFalse(isNewerVersion("dev", "dev"))
    }

    @Test
    fun versionCodeComparisonWhenVersionNamesAreEqual() {
        assertTrue(isNewerVersion("1.0.1", "1.0.1", remoteVersionCode = 3, localVersionCode = 2))
        assertFalse(isNewerVersion("1.0.1", "1.0.1", remoteVersionCode = 2, localVersionCode = 2))
        assertFalse(isNewerVersion("1.0.1", "1.0.1", remoteVersionCode = 1, localVersionCode = 2))
    }
}
