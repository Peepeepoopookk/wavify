package com.example.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.viewmodel.ProfileViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val file = File(context.filesDir, "profile_picture.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        viewModel.updateProfilePicturePath(file.absolutePath)
                    }
                }
            }
        }
    )

    var nameInput by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }
    
    // Sync local state with DataStore value only once on start
    LaunchedEffect(userPrefs.displayName) {
        if (!isInitialized && userPrefs.displayName.isNotEmpty()) {
            nameInput = userPrefs.displayName
            isInitialized = true
        }
    }

    val avatarColors = listOf(
        Color(0xFFEF5350), // Red
        Color(0xFFEC407A), // Pink
        Color(0xFFAB47BC), // Purple
        Color(0xFF7E57C2), // Deep Purple
        Color(0xFF5C6BC0), // Indigo
        Color(0xFF42A5F5), // Blue
        Color(0xFF26A69A), // Teal
        Color(0xFF66BB6A), // Green
        Color(0xFFFFA726), // Orange
        Color(0xFF8D6E63)  // Brown
    )
    val avatarColor = Color(userPrefs.avatarColor)
    val avatarContentColor = if (avatarColor.luminance() > 0.5f) Color.Black else Color.White
    val cacheLimitLabel = when (userPrefs.musicCacheLimitMb) {
        250 -> "250MB"
        1024 -> "1GB"
        else -> "500MB"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            var showMenu by remember { mutableStateOf(false) }
            
            var profileImageExists by remember { mutableStateOf(false) }
            LaunchedEffect(userPrefs.profilePicturePath) {
                if (userPrefs.profilePicturePath.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val exists = File(userPrefs.profilePicturePath).exists()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            profileImageExists = exists
                        }
                    }
                } else {
                    profileImageExists = false
                }
            }

            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(avatarColor)
                        .combinedClickable(
                            onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onLongClick = { showMenu = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (userPrefs.profilePicturePath.isNotEmpty() && profileImageExists) {
                        AsyncImage(
                            model = userPrefs.profilePicturePath,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = userPrefs.displayName.take(1).uppercase(),
                            color = avatarContentColor,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Camera Icon Button
                IconButton(
                    onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Change Photo",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Change Photo") },
                        onClick = {
                            showMenu = false
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    if (userPrefs.profilePicturePath.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Remove Photo") },
                            onClick = {
                                showMenu = false
                                viewModel.removeProfilePicture()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { 
                    nameInput = it 
                    // Optional: real-time update if preferred, but user requested on done/confirm
                    // viewModel.updateDisplayName(it) 
                },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Words
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        viewModel.updateDisplayName(nameInput)
                        focusManager.clearFocus()
                    }
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Avatar Color",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                avatarColors.take(5).forEach { color ->
                    ColorOption(
                        color = color,
                        isSelected = Color(userPrefs.avatarColor) == color,
                        onClick = { viewModel.updateAvatarColor(color.toArgb()) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                avatarColors.drop(5).forEach { color ->
                    ColorOption(
                        color = color,
                        isSelected = Color(userPrefs.avatarColor) == color,
                        onClick = { viewModel.updateAvatarColor(color.toArgb()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Settings Sections
            SettingsSection(title = "Appearance") {
                SettingsDropdownItem(
                    label = "Theme",
                    value = userPrefs.theme,
                    options = listOf("Dark", "Light", "System Default"),
                    onSelected = { viewModel.updateTheme(it) }
                )
            }

            SettingsSection(title = "Preferences") {
                SettingsSwitchItem(
                    label = "Offline mode",
                    value = userPrefs.offlineMode,
                    onValueChange = { viewModel.updateOfflineMode(it) }
                )
                SettingsDropdownItem(
                    label = "Language Filter",
                    value = userPrefs.languageFilter,
                    options = listOf("All", "English", "Malayalam", "Tamil", "Hindi"),
                    onSelected = { viewModel.updateLanguageFilter(it) }
                )
                SettingsDropdownItem(
                    label = "Audio Quality",
                    value = userPrefs.audioQuality,
                    options = listOf("Normal", "High"),
                    onSelected = { viewModel.updateAudioQuality(it) }
                )
                SettingsActionItem(
                    label = "Equalizer",
                    value = "Custom",
                    onClick = onEqualizerClick
                )
            }

            SettingsSection(title = "Storage") {
                SettingsDropdownItem(
                    label = "Music cache limit",
                    value = cacheLimitLabel,
                    options = listOf("250MB", "500MB", "1GB"),
                    onSelected = { viewModel.updateMusicCacheLimit(it) }
                )
                SettingsSwitchItem(
                    label = "Pre-fetch on cellular",
                    value = userPrefs.prefetchOnCellular,
                    onValueChange = { viewModel.updatePrefetchOnCellular(it) }
                )
                SettingsButtonItem(
                    label = "Clear app cache",
                    onClick = { viewModel.clearAppCaches() }
                )
                SettingsButtonItem(
                    label = "Clear downloads",
                    onClick = { viewModel.clearDownloads() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "App Version: 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(45.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsDropdownItem(
    label: String,
    value: String,
    options: List<String> = emptyList(),
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(option)
                                    expanded = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == value, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun SettingsActionItem(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingsSwitchItem(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(!value) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = value,
            onCheckedChange = onValueChange
        )
    }
}

@Composable
fun SettingsButtonItem(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
