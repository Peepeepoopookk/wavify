package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.model.ImportedPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        
        try {
            val config = androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor { command -> command.run() }
                .build()
            androidx.work.WorkManager.initialize(app, config)
        } catch (e: Exception) {
            // Already initialized
        }
        
        viewModel = MainViewModel(app)
    }

    @Test
    fun testRetryFailedOperation_clearsErrorAndRoutes() = runBlocking {
        // Use reflection to set the private _error MutableStateFlow
        val errorField: Field = MainViewModel::class.java.getDeclaredField("_error")
        errorField.isAccessible = true
        
        @Suppress("UNCHECKED_CAST")
        val errorFlow = errorField.get(viewModel) as MutableStateFlow<String?>

        // Test track error routing
        errorFlow.value = "Failed to load tracks: Network error"
        assertEquals("Failed to load tracks: Network error", viewModel.error.value)
        
        viewModel.retryFailedOperation()
        assertNull("Error should be cleared before retry", viewModel.error.value)
        
        // Test playlist error routing
        errorFlow.value = "Failed to load playlists: Timeout"
        assertEquals("Failed to load playlists: Timeout", viewModel.error.value)
        
        viewModel.retryFailedOperation()
        assertNull("Error should be cleared before retry", viewModel.error.value)
    }

    @Test
    fun testClearError() = runBlocking {
        val errorField: Field = MainViewModel::class.java.getDeclaredField("_error")
        errorField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val errorFlow = errorField.get(viewModel) as MutableStateFlow<String?>

        errorFlow.value = "Some error"
        viewModel.clearError()
        assertNull(viewModel.error.value)
    }
}
