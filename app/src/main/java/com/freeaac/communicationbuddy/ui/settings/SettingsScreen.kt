/*
 * Copyright (c) 2024 Robert Fillingame
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.freeaac.communicationbuddy.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeaac.communicationbuddy.data.VocabularyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import androidx.navigation.NavController
import android.util.Log

// Helper function to open TTS settings safely
private fun openTTSSettings(context: Context) {
    try {
        val intent = android.content.Intent()
        intent.action = "com.android.settings.TTS_SETTINGS"
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            // Fallback to speech recognition settings if TTS settings not available
            val fallbackIntent = android.content.Intent()
            fallbackIntent.action = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            fallbackIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(fallbackIntent)
        } catch (e: Exception) {
            // Couldn't open settings - silently fail
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWordEditor: (String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize VocabularyManager
    val vocabularyManager = remember { VocabularyManager(context) }
    
    // Get shared preferences
    val sharedPrefs = remember {
        context.getSharedPreferences("communication_buddy_settings", Context.MODE_PRIVATE)
    }
    
    // Check if there's a word ID to navigate to
    val navController = LocalContext.current as? NavController
    navController?.currentBackStackEntry?.savedStateHandle?.get<String>("navigate_to_word")?.let { wordId ->
        LaunchedEffect(wordId) {
            // Remove the flag to prevent repeat navigation
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("navigate_to_word")
            // Navigate to the word editor
            onNavigateToWordEditor(wordId)
        }
    }
    
    // State to track TTS initialization
    var ttsInitialized by remember { mutableStateOf(false) }
    
    // TTS instance for voice selection
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    
    // Initialize TTS only once
    DisposableEffect(Unit) {
        val newTts = try {
            TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // Now that TTS is initialized, we can mark it as ready
                    ttsInitialized = true
                }
            }
        } catch (e: Exception) {
            // Handle initialization error
            Log.e("SettingsScreen", "Failed to initialize TTS: ${e.message}")
            null
        }
        
        // Set the TTS instance
        tts.value = newTts
        
        onDispose {
            tts.value?.shutdown()
            tts.value = null
        }
    }
    
    // Get available voices - but only after TTS is initialized
    val allAvailableVoices by remember(ttsInitialized) {
        mutableStateOf(
            if (ttsInitialized && tts.value != null) {
                tts.value?.voices ?: emptySet()
            } else {
                emptySet()
            }
        )
    }
    
    // Group voices by language
    val voicesByLanguage = remember(allAvailableVoices) {
        allAvailableVoices.groupBy { it.locale }.toSortedMap(compareBy { it.displayLanguage })
    }
    
    // Available languages
    val availableLanguages = remember(voicesByLanguage) {
        voicesByLanguage.keys.toList()
    }
    
    // Settings state initialized from SharedPreferences
    // Load saved voice, if it exists
    val savedVoiceName = sharedPrefs.getString("voice_name", null)
    val initialVoice = if (savedVoiceName != null) {
        allAvailableVoices.find { it.name == savedVoiceName }
    } else null
    
    var selectedVoice by remember { mutableStateOf(initialVoice) }
    var speechRate by remember { mutableStateOf(sharedPrefs.getFloat("speech_rate", 1.0f)) }
    var speechPitch by remember { mutableStateOf(sharedPrefs.getFloat("speech_pitch", 1.0f)) }
    var gridSize by remember { mutableStateOf(sharedPrefs.getInt("grid_size", 5)) }
    var textSize by remember { mutableStateOf(sharedPrefs.getString("text_size", "Medium") ?: "Medium") }
    var highContrastMode by remember { mutableStateOf(sharedPrefs.getBoolean("high_contrast_mode", false)) }
    var autoSpeakWords by remember { mutableStateOf(sharedPrefs.getBoolean("auto_speak_words", true)) }
    var darkMode by remember { mutableStateOf(sharedPrefs.getBoolean("dark_mode", false)) }
    
    // Track whether settings have been changed
    var settingsChanged by remember { mutableStateOf(false) }
    
    // Create grid size options
    val gridSizeOptions = listOf(3, 4, 5, 6, 7)
    var showGridSizeDialog by remember { mutableStateOf(false) }
    
    // Create text size options
    val textSizeOptions = listOf("Small", "Medium", "Large", "Extra Large")
    var showTextSizeDialog by remember { mutableStateOf(false) }
    
    // Voice selection dialogs
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf<Locale?>(selectedVoice?.locale) }
    
    // Show About dialog
    var showAboutDialog by remember { mutableStateOf(false) }
    
    // State for backup/restore operation status
    var isBackupInProgress by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf<String?>(null) }
    var showBackupPrompt by remember { mutableStateOf(false) }
    var showRestorePrompt by remember { mutableStateOf(false) }
    
    // State for reset confirmation
    var showResetPrompt by remember { mutableStateOf(false) }
    var resetStatus by remember { mutableStateOf<String?>(null) }
    var isResetInProgress by remember { mutableStateOf(false) }
    
    // Activity result launcher for restore
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            isRestoreInProgress = true
            restoreStatus = "Restoring from backup..."
            
            coroutineScope.launch(Dispatchers.IO) {
                val success = vocabularyManager.restoreFromBackup(it)
                
                withContext(Dispatchers.Main) {
                    isRestoreInProgress = false
                    restoreStatus = if (success) {
                        "Restore successful!"
                    } else {
                        "Restore failed. Please try a different file."
                    }
                }
            }
        }
    }
    
    // Save settings function
    val saveSettings = {
        sharedPrefs.edit().apply {
            putFloat("speech_rate", speechRate)
            putFloat("speech_pitch", speechPitch)
            putInt("grid_size", gridSize)
            putString("text_size", textSize)
            putBoolean("high_contrast_mode", highContrastMode)
            putBoolean("auto_speak_words", autoSpeakWords)
            putBoolean("dark_mode", darkMode)
            // Save selected voice name if available
            selectedVoice?.let { voice ->
                putString("voice_name", voice.name)
            }
            apply()
        }
        settingsChanged = false
    }
    
    // Play sample text with current voice settings
    fun speakSample() {
        tts.value?.let { ttsEngine ->
            selectedVoice?.let { voice ->
                ttsEngine.voice = voice
            }
            ttsEngine.setSpeechRate(speechRate)
            ttsEngine.setPitch(speechPitch)
            ttsEngine.speak("This is a sample of how text will sound.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    // Voice information string to help user understand TTS status
    val voiceInfoString = if (!ttsInitialized) {
        "Initializing Text-to-Speech engine..."
    } else if (allAvailableVoices.isEmpty()) {
        "No voices found. Please install a Text-to-Speech engine from the Play Store."
    } else {
        selectedVoice?.name?.substringAfterLast('.')?.replace('_', ' ') ?: "Default Voice"
    }
    
    // Backup function
    fun performBackup() {
        isBackupInProgress = true
        backupStatus = "Creating backup..."
        
        coroutineScope.launch(Dispatchers.IO) {
            val backupUri = vocabularyManager.createBackup()
            
            withContext(Dispatchers.Main) {
                isBackupInProgress = false
                
                if (backupUri != null) {
                    backupStatus = "Backup created successfully!"
                    
                    // Share the backup file
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, backupUri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Backup"))
                } else {
                    backupStatus = "Backup failed. Please try again."
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (settingsChanged) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var showSaveHelp by remember { mutableStateOf(false) }
                            
                            IconButton(
                                onClick = { showSaveHelp = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Save Help",
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                            
                            Button(
                                onClick = saveSettings,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Save")
                            }
                            
                            if (showSaveHelp) {
                                AlertDialog(
                                    onDismissRequest = { showSaveHelp = false },
                                    title = { Text("Save Settings") },
                                    text = { 
                                        Text(
                                            "Important: You must tap Save to keep your changes!\n\n" +
                                            "Any changes you make to settings will only be temporary until you tap the Save button. " +
                                            "After saving, your settings will be remembered even when you close the app.\n\n" +
                                            "The Save button only appears when you've made changes that need to be saved."
                                        ) 
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showSaveHelp = false }) {
                                            Text("Got it")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header sections
            SettingsSectionHeaderWithHelp(
                title = "Speech Settings",
                helpText = "Speech Settings control how the app speaks words and sentences.\n\n" +
                    "In this section, you can:\n" +
                    "• Choose a voice that sounds natural and clear\n" +
                    "• Adjust how fast or slow the voice speaks\n" +
                    "• Change how high or low the voice sounds\n\n" +
                    "These settings are especially important for users who rely on the app for communication. Finding the right voice and speed can make a big difference in how well others understand the app's speech."
            )
            
            // Voice Selection
            SettingsItemWithHelp(
                title = "Voice",
                subtitle = voiceInfoString,
                helpText = "The voice is what you hear when the app speaks words and sentences. Different voices can sound like different people (male, female, child, etc.) and speak different languages.\n\nYou currently have " + (if (allAvailableVoices.isEmpty()) "no voices" else "${allAvailableVoices.size} voices") + " installed on your device.",
                icon = Icons.Default.Info,
                onClick = {
                    // Show language selection dialog first
                    showLanguageDialog = true
                }
            )
            
            // Test Voice button
            if (selectedVoice != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showTestVoiceHelp by remember { mutableStateOf(false) }
                    
                    IconButton(
                        onClick = { showTestVoiceHelp = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Test Voice Help",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    
                    TextButton(
                        onClick = { speakSample() },
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text("Test Voice")
                    }
                    
                    if (showTestVoiceHelp) {
                        AlertDialog(
                            onDismissRequest = { showTestVoiceHelp = false },
                            title = { Text("Test Voice") },
                            text = { 
                                Text(
                                    "Tap this button to hear a sample of the currently selected voice using your current speech rate and pitch settings.\n\n" +
                                    "This helps you check if the voice sounds good before saving your settings.\n\n" +
                                    "If you don't like how it sounds, you can:\n" +
                                    "• Select a different voice\n" +
                                    "• Adjust the speech rate\n" +
                                    "• Adjust the pitch\n\n" +
                                    "Remember to tap Save when you're happy with your selection!"
                                ) 
                            },
                            confirmButton = {
                                TextButton(onClick = { showTestVoiceHelp = false }) {
                                    Text("Got it")
                                }
                            }
                        )
                    }
                }
            }
            
            // Diagnostic status for TTS (only show when there are issues)
            if (!ttsInitialized || allAvailableVoices.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Text-to-Speech Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (!ttsInitialized) {
                                "Initializing Text-to-Speech engine. Please wait..."
                            } else if (allAvailableVoices.isEmpty()) {
                                "No voices found. You may need to install or update your Text-to-Speech engine from the Play Store."
                            } else {
                                "" // This should never happen, but Kotlin requires an else branch
                            },
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    // Open Android TTS settings
                                    openTTSSettings(context)
                                }
                            ) {
                                Text(
                                    "Open System Settings",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
            
            // Speech Rate
            SettingsSliderItemWithHelp(
                title = "Speech Rate",
                helpText = "Speech Rate controls how fast or slow the voice speaks.\n\n• Lower values (0.1-0.5): Very slow speech, good for beginners\n• Middle values (0.5-1.5): Normal conversational speed\n• Higher values (1.5-2.0): Fast speech for advanced users\n\nTry different speeds to find what works best for you or the person using the app.",
                value = speechRate,
                valueRange = 0.1f..2.0f,
                steps = 19,
                icon = Icons.Default.Info,
                onValueChange = { 
                    speechRate = it
                    tts.value?.setSpeechRate(it)
                    settingsChanged = true
                }
            )
            
            // Speech Pitch
            SettingsSliderItemWithHelp(
                title = "Speech Pitch",
                helpText = "Speech Pitch controls how high or low the voice sounds.\n\n• Lower values (0.5-0.8): Deeper voice (more masculine)\n• Middle values (0.8-1.2): Natural voice pitch\n• Higher values (1.2-2.0): Higher voice (more feminine or child-like)\n\nAdjusting the pitch can make the voice easier to understand or more pleasant to listen to.",
                value = speechPitch,
                valueRange = 0.5f..2.0f,
                steps = 15,
                icon = Icons.Default.Info,
                onValueChange = { 
                    speechPitch = it
                    tts.value?.setPitch(it)
                    settingsChanged = true
                }
            )
            
            HorizontalDivider()
            
            // Appearance Settings
            SettingsSectionHeader(title = "Appearance")
            
            // Grid Size
            SettingsItem(
                title = "Grid Size",
                subtitle = "$gridSize × $gridSize",
                icon = Icons.Default.Info
            ) {
                showGridSizeDialog = true
            }
            
            // Text Size
            SettingsItem(
                title = "Text Size",
                subtitle = textSize,
                icon = Icons.Default.Info
            ) {
                showTextSizeDialog = true
            }
            
            // High Contrast Mode
            SettingsSwitchItemWithHelp(
                title = "High Contrast Mode",
                subtitle = "Enhanced visibility with stronger color contrast",
                helpText = "High Contrast Mode improves visibility for users with visual impairments by:\n\n" +
                          "• Using color combinations with stronger contrast\n" + 
                          "• Making text more readable against backgrounds\n" +
                          "• Enhancing the distinction between UI elements\n\n" +
                          "This mode works in both light and dark themes and changes are applied immediately.",
                isChecked = highContrastMode,
                icon = Icons.Default.Info,
                onCheckedChange = { 
                    highContrastMode = it
                    settingsChanged = true
                    // Apply change immediately
                    sharedPrefs.edit().putBoolean("high_contrast_mode", it).apply()
                }
            )
            
            // Dark Mode
            SettingsSwitchItemWithHelp(
                title = "Dark Mode",
                subtitle = "Use dark theme for better night viewing",
                helpText = "Dark Mode uses a darker color scheme that is:\n\n" +
                           "• Easier on the eyes in low light environments\n" +
                           "• Helps reduce eye strain during extended use\n" +
                           "• May help conserve battery on some devices\n\n" +
                           "Changes to this setting are applied immediately.",
                isChecked = darkMode,
                icon = Icons.Default.Info,
                onCheckedChange = { 
                    darkMode = it
                    settingsChanged = true
                    // Apply change immediately
                    sharedPrefs.edit().putBoolean("dark_mode", it).apply()
                }
            )
            
            HorizontalDivider()
            
            // Behavior Settings
            SettingsSectionHeader(title = "Behavior")
            
            // Auto-speak Words
            SettingsSwitchItem(
                title = "Auto-speak Words",
                isChecked = autoSpeakWords,
                icon = Icons.Default.Info,
                onCheckedChange = { 
                    autoSpeakWords = it 
                    settingsChanged = true
                }
            )
            
            // Word Prediction
            val wordPredictionEnabled = sharedPrefs.getBoolean("word_prediction_enabled", true)
            var isWordPredictionEnabled by remember { mutableStateOf(wordPredictionEnabled) }
            
            SettingsSwitchItemWithHelp(
                title = "Word Prediction",
                subtitle = "Show word suggestions based on context",
                helpText = "Word prediction suggests words that might come next in your sentence based on:\n\n" +
                          "• Words you've used together before\n" +
                          "• Common word combinations\n" +
                          "• The current sentence context\n\n" +
                          "This can speed up communication by reducing the number of taps needed to form a sentence.",
                isChecked = isWordPredictionEnabled,
                icon = Icons.Default.Info,
                onCheckedChange = { 
                    isWordPredictionEnabled = it
                    sharedPrefs.edit().putBoolean("word_prediction_enabled", it).apply()
                    settingsChanged = true
                }
            )
            
            HorizontalDivider()
            
            // Vocabulary Management
            SettingsSectionHeader(title = "Vocabulary Management")
            
            // Add New Word
            SettingsItem(
                title = "Add New Word",
                subtitle = "Create a custom word with your own image",
                icon = Icons.Default.Add
            ) {
                onNavigateToWordEditor(null) // null means creating a new word
            }
            
            // Edit Existing Words
            SettingsItem(
                title = "Edit Words",
                subtitle = "Modify existing words or replace images",
                icon = Icons.Default.Edit
            ) {
                // This would navigate to a word list for editing
                // Simplified for now
                onNavigateToWordEditor("edit")
            }
            
            // Backup & Restore
            SettingsSectionHeaderWithHelp(
                title = "Backup & Restore",
                helpText = "Backup your custom vocabulary to save your words and images. " +
                "You can restore these later if you change devices or reinstall the app."
            )
            
            SettingsItem(
                title = "Backup Vocabulary",
                subtitle = "Save your custom words and images",
                icon = Icons.Default.Info
            ) {
                // Show confirmation prompt
                showBackupPrompt = true
            }
            
            SettingsItem(
                title = "Restore from Backup",
                subtitle = "Load previously saved vocabulary",
                icon = Icons.Default.Info
            ) {
                // Show confirmation prompt
                showRestorePrompt = true
            }
            
            // Reset to Default Vocabulary
            SettingsItem(
                title = "Reset to Default Vocabulary",
                subtitle = "Clear all custom words and return to default settings",
                icon = Icons.Default.Info
            ) {
                // Show confirmation prompt
                showResetPrompt = true
            }
            
            // Show backup/restore status if available
            backupStatus?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.contains("failed")) 
                            MaterialTheme.colorScheme.errorContainer 
                        else 
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBackupInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.contains("failed")) 
                                MaterialTheme.colorScheme.onErrorContainer 
                            else 
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (!isBackupInProgress) {
                            TextButton(onClick = { backupStatus = null }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            
            restoreStatus?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (status.contains("failed")) 
                            MaterialTheme.colorScheme.errorContainer 
                        else 
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRestoreInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.contains("failed")) 
                                MaterialTheme.colorScheme.onErrorContainer 
                            else 
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (!isRestoreInProgress) {
                            TextButton(onClick = { restoreStatus = null }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            
            // About & Help
            SettingsSectionHeader(title = "About & Help")
            
            SettingsItem(
                title = "About Communication Buddy",
                subtitle = "Version 1.0",
                icon = Icons.Default.Info
            ) {
                // Show About dialog
                showAboutDialog = true
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // About Dialog
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "About Communication Buddy",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Version 1.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            "Communication Buddy is a free AAC (Augmentative and Alternative Communication) app designed to help people with speech difficulties communicate more effectively.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "Features:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("• Word grid with customizable size")
                        Text("• Recent words category")
                        Text("• Custom vocabulary with your own images")
                        Text("• Text-to-speech with adjustable voices")
                        Text("• Backup and restore vocabulary")
                        Text("• High contrast mode for accessibility")
                        Text("• Adjustable text size")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "Credits:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Developed by: Robert Fillingame")
                        Text("Development Assistance: Cursor AI")
                        Text("Images & Icon: Created with OpenAI's DALL-E 3")
                        Text("Built with: Jetpack Compose")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "License:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Licensed under the Apache License, Version 2.0")
                        Text(
                            "Full license text available at: http://www.apache.org/licenses/LICENSE-2.0",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "This app is open source and freely available for anyone who needs communication assistance.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showAboutDialog = false }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
        
        // Language Selection Dialog
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text("Select Language") },
                text = {
                    if (!ttsInitialized) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Initializing Text-to-Speech engine...")
                            }
                        }
                    } else if (availableLanguages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No Text-to-Speech voices found on your device.",
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Please install a TTS engine from the Play Store or enable system TTS in device settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            items(availableLanguages) { locale ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLanguage = locale
                                            showLanguageDialog = false
                                            showVoiceDialog = true
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = locale.displayLanguage,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "(${voicesByLanguage[locale]?.size ?: 0} voices)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    if (availableLanguages.isEmpty()) {
                        TextButton(
                            onClick = {
                                // Open Android TTS settings
                                openTTSSettings(context)
                            }
                        ) {
                            Text("Open Settings")
                        }
                    }
                }
            )
        }
        
        // Voice Selection Dialog (shown after language is selected)
        if (showVoiceDialog && selectedLanguage != null) {
            val voicesForLanguage = voicesByLanguage[selectedLanguage] ?: emptyList()
            
            AlertDialog(
                onDismissRequest = { showVoiceDialog = false },
                title = { 
                    Column {
                        Text("Select Voice")
                        Text(
                            text = "(${selectedLanguage?.displayLanguage})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        items(voicesForLanguage) { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedVoice = voice
                                        tts.value?.voice = voice  // Set the selected voice
                                        speakSample()  // Test the voice immediately
                                        settingsChanged = true
                                        showVoiceDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedVoice?.name == voice.name,
                                    onClick = {
                                        selectedVoice = voice
                                        tts.value?.voice = voice  // Set the selected voice
                                        speakSample()  // Test the voice immediately
                                        settingsChanged = true
                                        showVoiceDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = voice.name.substringAfterLast('.').replace('_', ' '),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (voice.features.isNotEmpty()) {
                                        Text(
                                            text = voice.features.joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVoiceDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Grid Size Selection Dialog
        if (showGridSizeDialog) {
            AlertDialog(
                onDismissRequest = { showGridSizeDialog = false },
                title = { Text("Select Grid Size") },
                text = {
                    Column {
                        gridSizeOptions.forEach { size ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        gridSize = size
                                        settingsChanged = true
                                        showGridSizeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = gridSize == size,
                                    onClick = {
                                        gridSize = size
                                        settingsChanged = true
                                        showGridSizeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$size × $size")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGridSizeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Text Size Selection Dialog
        if (showTextSizeDialog) {
            AlertDialog(
                onDismissRequest = { showTextSizeDialog = false },
                title = { Text("Select Text Size") },
                text = {
                    Column {
                        textSizeOptions.forEach { size ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        textSize = size
                                        settingsChanged = true
                                        showTextSizeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = textSize == size,
                                    onClick = {
                                        textSize = size
                                        settingsChanged = true
                                        showTextSizeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(size)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTextSizeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Backup confirmation dialog
        if (showBackupPrompt) {
            AlertDialog(
                onDismissRequest = { showBackupPrompt = false },
                title = { Text("Backup Vocabulary") },
                text = { 
                    Text(
                        "This will create a backup of all your custom words and images. " +
                        "You can share or save this backup file to restore later.\n\n" +
                        "Do you want to continue?"
                    ) 
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            showBackupPrompt = false
                            performBackup()
                        }
                    ) {
                        Text("Create Backup")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showBackupPrompt = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Restore confirmation dialog
        if (showRestorePrompt) {
            AlertDialog(
                onDismissRequest = { showRestorePrompt = false },
                title = { Text("Restore from Backup") },
                text = { 
                    Text(
                        "This will replace all your current custom words with ones from the backup. " +
                        "Any words not in the backup will be lost.\n\n" +
                        "Do you want to continue?"
                    ) 
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            showRestorePrompt = false
                            restoreLauncher.launch("application/zip")
                        }
                    ) {
                        Text("Select Backup")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRestorePrompt = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Reset confirmation dialog
        if (showResetPrompt) {
            AlertDialog(
                onDismissRequest = { showResetPrompt = false },
                title = { Text("Reset to Default Vocabulary") },
                text = { 
                    Text(
                        "This will delete all your custom words and images, returning to the default vocabulary. " +
                        "This action cannot be undone unless you have a backup.\n\n" +
                        "Are you sure you want to reset?"
                    ) 
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            showResetPrompt = false
                            isResetInProgress = true
                            resetStatus = "Resetting vocabulary..."
                            
                            coroutineScope.launch(Dispatchers.IO) {
                                val success = vocabularyManager.resetToDefaultVocabulary()
                                
                                withContext(Dispatchers.Main) {
                                    isResetInProgress = false
                                    resetStatus = if (success) {
                                        "Reset successful! All custom words have been removed."
                                    } else {
                                        "Reset failed. Please try again."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset Vocabulary")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetPrompt = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Reset status card
        resetStatus?.let { status ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.contains("failed")) 
                        MaterialTheme.colorScheme.errorContainer 
                    else 
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isResetInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.contains("failed")) 
                            MaterialTheme.colorScheme.onErrorContainer 
                        else 
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isResetInProgress) {
                        TextButton(onClick = { resetStatus = null }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    isChecked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!isChecked) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = String.format("%.1f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun SettingsItemWithHelp(
    title: String,
    subtitle: String? = null,
    helpText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { showHelpDialog = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Help",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(title) },
            text = { 
                Column {
                    Text(helpText)
                    
                    if (title == "Voice") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "How to add more voices (including male voices):",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1. Install or update Google Text-to-Speech from the Play Store")
                        Text("2. Go to your device Settings > Accessibility > Text-to-Speech")
                        Text("3. Select Google Text-to-Speech Engine")
                        Text("4. Tap 'Install voice data' or 'Settings' > 'Install voice data'")
                        Text("5. Find 'English (United States)' or your preferred language")
                        Text("6. Download all available voices - this will include male voices")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("For even more voice options:", fontWeight = FontWeight.Bold)
                        Text("• Try other TTS engines like 'Samsung TTS' or 'Amazon Polly'")
                        Text("• Some engines offer premium voices with better quality")
                        Text("• Male voices are available in most language packs")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("After installing new voices, return to this app and tap 'Voice' again to see your new options.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            },
            dismissButton = if (title == "Voice") {
                {
                    TextButton(
                        onClick = {
                            // Open Android TTS settings
                            openTTSSettings(context)
                            showHelpDialog = false
                        }
                    ) {
                        Text("Open System Settings")
                    }
                }
            } else null
        )
    }
}

@Composable
fun SettingsSliderItemWithHelp(
    title: String,
    helpText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onValueChange: (Float) -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = String.format("%.1f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
    
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(title) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeaderWithHelp(
    title: String,
    helpText: String
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        
        IconButton(
            onClick = { showHelpDialog = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Section Help",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
    
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(title) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun SettingsSwitchItemWithHelp(
    title: String,
    subtitle: String? = null,
    helpText: String,
    isChecked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!isChecked) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { showHelpDialog = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Help",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
    
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(title) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
} 