package com.example.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val label: String,
    val frequency: String,
    val value: Float, // dB value from -12 to 12
    val color: androidx.compose.ui.graphics.Color
)

class EqualizerViewModel : ViewModel() {
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bands = MutableStateFlow(
        listOf(
            EqualizerBand("Bass", "60Hz", 0f, androidx.compose.ui.graphics.Color(0xFFFF5252)),
            EqualizerBand("Mid-Bass", "230Hz", 0f, androidx.compose.ui.graphics.Color(0xFFFFAB40)),
            EqualizerBand("Mid", "910Hz", 0f, androidx.compose.ui.graphics.Color(0xFFFFD740)),
            EqualizerBand("Mid-High", "4kHz", 0f, androidx.compose.ui.graphics.Color(0xFF69F0AE)),
            EqualizerBand("Treble", "14kHz", 0f, androidx.compose.ui.graphics.Color(0xFF40C4FF))
        )
    )
    val bands: StateFlow<List<EqualizerBand>> = _bands.asStateFlow()

    fun toggleEnabled() {
        _isEnabled.value = !_isEnabled.value
    }

    fun updateBandValue(index: Int, newValue: Float) {
        val currentList = _bands.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(value = newValue)
            _bands.value = currentList
        }
    }
}
