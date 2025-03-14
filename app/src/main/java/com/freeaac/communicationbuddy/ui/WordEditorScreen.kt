package com.freeaac.communicationbuddy.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import com.freeaac.communicationbuddy.data.VocabularyManager
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(
    wordId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToWordEditor: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vocabularyManager = remember { VocabularyManager(context) }
    
    // State for the word being edited
    val isNewWord = wordId == null || wordId == "new"
    
    var wordText by remember { mutableStateOf(TextFieldValue("")) }
    var categoryText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCategorySelector by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showImageOptionsDialog by remember { mutableStateOf(false) }
    
    // State for loading and success/error feedback
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var operationSuccess by remember { mutableStateOf(false) }
    
    // Load word data if editing an existing word
    LaunchedEffect(wordId) {
        // Clear debug logs when starting a new session
        vocabularyManager.clearDebugLog()
        
        if (!isNewWord && wordId != "edit") {
            // Load actual word data from VocabularyManager
            try {
                val wordData = vocabularyManager.getWordDataFromPrefs(wordId)
                if (wordData != null) {
                    wordText = TextFieldValue(wordData.word)
                    categoryText = TextFieldValue(wordData.category)
                    
                    // Load image if available
                    wordData.imagePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            selectedImageUri = Uri.fromFile(file)
                        }
                    }
                } else {
                    saveError = "Couldn't find the word to edit"
                }
            } catch (e: Exception) {
                saveError = "Error loading word: ${e.message}"
            }
        }
    }
    
    // Check for image from camera
    val navBackStackEntry = (LocalContext.current as? NavController)
        ?.currentBackStackEntryAsState()?.value
    
    DisposableEffect(navBackStackEntry) {
        val photoUri = navBackStackEntry?.savedStateHandle?.get<Uri>("photo_uri")
        if (photoUri != null) {
            selectedImageUri = photoUri
            navBackStackEntry.savedStateHandle.remove<Uri>("photo_uri")
        }
        onDispose { }
    }
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            Log.d("WordEditorScreen", "Image selected from gallery: $uri")
            Log.d("WordEditorScreen", "URI details - scheme: ${uri.scheme}, path: ${uri.path}, authority: ${uri.authority}")
            selectedImageUri = uri 
        }
    }
    
    // Save word function
    suspend fun saveWord() {
        if (wordText.text.isBlank() || categoryText.text.isBlank()) {
            saveError = "Please fill in all fields"
            return
        }

        try {
            Log.d("WordEditorScreen", "Starting to save word: ${wordText.text} in category: ${categoryText.text}")
            Log.d("WordEditorScreen", "Image URI: $selectedImageUri")
            if (selectedImageUri != null) {
                Log.d("WordEditorScreen", "URI details - scheme: ${selectedImageUri!!.scheme}, path: ${selectedImageUri!!.path}, authority: ${selectedImageUri!!.authority}")
                
                // Verify content resolver can access the URI
                try {
                    context.contentResolver.openInputStream(selectedImageUri!!).use { stream ->
                        if (stream != null) {
                            val available = stream.available()
                            Log.d("WordEditorScreen", "Stream available bytes: $available")
                        } else {
                            Log.e("WordEditorScreen", "Failed to open input stream for URI")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WordEditorScreen", "Error accessing URI with content resolver", e)
                }
            }
            
            val success = if (isNewWord) {
                // Add new word
                vocabularyManager.saveCustomWord(
                    word = wordText.text, 
                    category = categoryText.text,
                    imageUri = selectedImageUri
                )
            } else {
                // Update existing word
                vocabularyManager.updateCustomWord(
                    wordId = wordId!!,
                    word = wordText.text,
                    category = categoryText.text,
                    imageUri = selectedImageUri
                )
            }
            
            Log.d("WordEditorScreen", "Save result: $success")
            
            if (success) {
                operationSuccess = true
                // Short delay to show success before navigating back
                kotlinx.coroutines.delay(500)
                onNavigateBack()
            } else {
                saveError = "Failed to save word"
            }
        } catch (e: Exception) {
            Log.e("WordEditorScreen", "Error saving word", e)
            saveError = "Error: ${e.message}"
        }
    }
    
    // Delete word function
    fun deleteWord() {
        if (!isNewWord) {
            isSaving = true
            saveError = null
            
            scope.launch {
                try {
                    val result = vocabularyManager.deleteCustomWord(wordId!!)
                    
                    isSaving = false
                    
                    if (result) {
                        operationSuccess = true
                        // Short delay to show success before navigating back
                        kotlinx.coroutines.delay(500)
                        onNavigateBack()
                    } else {
                        saveError = "Failed to delete word"
                    }
                } catch (e: Exception) {
                    isSaving = false
                    saveError = "Error: ${e.message}"
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (isNewWord) "Add New Word" else if (wordId == "edit") "Edit Words" else "Edit Word") 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (wordId != "edit" && !isSaving) {
                        IconButton(
                            onClick = { 
                                scope.launch {
                                    saveWord()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (wordId == "edit") {
            // Show list of words to edit
            WordListForEditing(
                vocabularyManager = vocabularyManager,
                onEditWord = { selectedWordId ->
                    Log.d("WordEditorScreen", "Selected word to edit: $selectedWordId")
                    
                    // Use the callback if available, otherwise fall back to old method
                    if (onNavigateToWordEditor != null) {
                        // First go back to settings
                        onNavigateBack()
                        // Short delay to ensure UI updates
                        scope.launch {
                            kotlinx.coroutines.delay(100)
                            // Use the callback to navigate to edit screen
                            onNavigateToWordEditor(selectedWordId)
                        }
                    } else {
                        // Fallback - just try to navigate back
                        Log.e("WordEditorScreen", "No onNavigateToWordEditor callback available")
                        onNavigateBack()
                    }
                },
                paddingValues = paddingValues
            )
        } else {
            // Show editor for a single word
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Image Selection Area
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                // Show image options dialog
                                showImageOptionsDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            // Display selected image
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(context)
                                        .data(selectedImageUri)
                                        .build()
                                ),
                                contentDescription = "Selected Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Display placeholder
                            Text(
                                text = "Select an Image",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Image selection buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Camera option
                        OutlinedButton(
                            onClick = onNavigateToCamera,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Take Photo"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Camera")
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Gallery option
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Choose from Gallery"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gallery")
                        }
                    }
                    
                    // Word Text Field
                    OutlinedTextField(
                        value = wordText,
                        onValueChange = { wordText = it },
                        label = { Text("Word or Phrase") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true
                    )
                    
                    // Category Text Field
                    OutlinedTextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("Category") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showCategorySelector = true }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Category"
                                )
                            }
                        }
                    )
                    
                    // Error message
                    saveError?.let { error ->
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            // Debug button to view logs
                            OutlinedButton(
                                onClick = {
                                    // Show log content in a dialog
                                    val logFile = File(vocabularyManager.getDebugLogFilePath())
                                    if (logFile.exists()) {
                                        try {
                                            val logContent = logFile.readText()
                                            // You would normally show this in a dialog, but for now
                                            // we'll just update the error message with last few lines
                                            val lastLines = logContent.split("\n").takeLast(10).joinToString("\n")
                                            saveError = "Last log entries:\n$lastLines"
                                        } catch (e: Exception) {
                                            saveError = "Error reading log: ${e.message}"
                                        }
                                    } else {
                                        saveError = "No log file found at ${logFile.absolutePath}"
                                    }
                                }
                            ) {
                                Text("Debug: View Logs")
                            }
                        }
                    }
                    
                    // Success message
                    if (operationSuccess) {
                        Text(
                            text = if (isNewWord) "Word added successfully!" else "Word updated successfully!",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Delete Button (only for existing words)
                    if (!isNewWord) {
                        Button(
                            onClick = { showDeleteConfirmation = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Word")
                        }
                    }
                    
                    // Save Button
                    Button(
                        onClick = { 
                            scope.launch {
                                saveWord()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSaving) "Saving..." else "Save Word")
                    }
                }
                
                // Show loading overlay if saving
                if (isSaving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            
            // Image Options Dialog
            if (showImageOptionsDialog) {
                AlertDialog(
                    onDismissRequest = { showImageOptionsDialog = false },
                    title = { Text("Select Image") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showImageOptionsDialog = false
                                    onNavigateToCamera()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Take Photo"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Photo")
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    showImageOptionsDialog = false
                                    imagePickerLauncher.launch("image/*")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Choose from Gallery"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose from Gallery")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showImageOptionsDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            // Category Selector Dialog
            if (showCategorySelector) {
                CategorySelectorDialog(
                    onDismissRequest = { showCategorySelector = false },
                    onCategorySelected = { category ->
                        categoryText = TextFieldValue(category)
                        showCategorySelector = false
                    }
                )
            }
            
            // Delete Confirmation Dialog
            if (showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text("Delete Word") },
                    text = { Text("Are you sure you want to delete this word?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmation = false
                                deleteWord()
                            }
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun WordListForEditing(
    vocabularyManager: VocabularyManager,
    onEditWord: (String) -> Unit,
    paddingValues: PaddingValues
) {
    // Get actual custom words from the vocabulary manager
    val customWords = remember { vocabularyManager.getAllCustomWords() }
    val customWordIds = remember { vocabularyManager.getAllWordIds() }
    
    // Group words by category for better organization
    val wordsByCategory = remember(customWords) {
        customWords.groupBy { it.category }
    }
    
    if (customWords.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No custom words found",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Add words to get started",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            wordsByCategory.forEach { (category, words) ->
                // Category header
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(16.dp)
                    )
                }
                
                // Words in this category
                items(words) { word ->
                    val index = customWords.indexOf(word)
                    val wordId = if (index >= 0 && index < customWordIds.size) {
                        customWordIds[index]
                    } else null
                    
                    if (wordId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditWord(wordId) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Image placeholder or actual image
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                // Try to load image if available
                                val imagePath = vocabularyManager.getImagePathForWord(wordId)
                                if (imagePath != null) {
                                    val bitmap = vocabularyManager.loadBitmapFromPath(imagePath)
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = word.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = word.label.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                } else {
                                    Text(
                                        text = word.label.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = word.label,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = word.category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            IconButton(onClick = { onEditWord(wordId) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit"
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectorDialog(
    onDismissRequest: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    // Sample categories from our main app
    val categories = listOf(
        "People", "Actions", "Describing Words", "Places", "Things",
        "Emotions", "Time", "Numbers", "Colors", "Shapes", "School",
        "Sports", "Holidays", "Food", "Clothing", "Weather", "Health",
        "Social Interactions", "Questions", "Commands", "Create",
        "Daily Life", "Let's Talk", "Motor Play", "Out & About", 
        "Reading", "Toys & Games", "Animals", "Body Parts"
    )
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Category") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                items(categories) { category ->
                    Text(
                        text = category,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
} 