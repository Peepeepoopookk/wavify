package com.example.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

class AppHapticFeedback(
    private val composeHaptics: HapticFeedback,
    private val vibrator: Vibrator?
) {
    /**
     * Tactile click for primary actions: Play/Pause, Favorite, Expand player.
     */
    fun click() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                return
            } catch (_: Exception) {
                // Fallback to Compose haptics
            }
        }
        composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Crisp, ultra-light tick for secondary controls: Next/Prev skip, seek release, shuffle, repeat, download.
     */
    fun tick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            } catch (_: Exception) {
                // Fallback to Compose haptics
            }
        }
        composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * Stronger confirmation for destructive or major state changes (e.g. deleting a track, clearing queue).
     */
    fun heavyClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                return
            } catch (_: Exception) {
                // Fallback to Compose haptics
            }
        }
        composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberAppHapticFeedback(): AppHapticFeedback {
    val composeHaptics = LocalHapticFeedback.current
    val context = LocalContext.current
    return remember(composeHaptics, context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        AppHapticFeedback(composeHaptics, vibrator)
    }
}
