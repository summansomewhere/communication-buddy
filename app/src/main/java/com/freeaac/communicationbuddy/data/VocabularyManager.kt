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
package com.freeaac.communicationbuddy.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.freeaac.communicationbuddy.ui.AACCategory
import com.freeaac.communicationbuddy.ui.AACItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "VocabularyManager"
private const val CUSTOM_WORDS_PREFS = "custom_words_prefs"
private const val CUSTOM_IMAGES_DIR = "custom_images"
private const val BACKUPS_DIR = "backups"

/**
 * Manages the user's custom vocabulary items, including:
 * - Adding new words
 * - Modifying existing words
 * - Replacing images
 * - Storing and retrieving custom images
 * - Backup and restore functionality
 */
class VocabularyManager(private val context: Context) {
    
    private val customWordsPrefs: SharedPreferences = context.getSharedPreferences(
        CUSTOM_WORDS_PREFS, Context.MODE_PRIVATE
    )
    
    private val customImagesDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), CUSTOM_IMAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    
    private val backupsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), BACKUPS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    
    /**
     * Logs debug information to a file
     */
    private fun logToFile(message: String) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val logFile = File(logDir, "debug_log.txt")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                .format(java.util.Date())
            
            val logLine = "$timestamp: $message\n"
            
            FileOutputStream(logFile, true).use { out ->
                out.write(logLine.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to debug log file", e)
        }
    }
    
    /**
     * Saves a custom word with an optional image
     */
    suspend fun saveCustomWord(
        word: String,
        category: String,
        imageUri: Uri?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Generate a unique ID for the word
            val wordId = UUID.randomUUID().toString()
            Log.d(TAG, "Saving custom word: $word in category: $category with ID: $wordId")
            logToFile("Saving custom word: $word in category: $category with ID: $wordId")
            Log.d(TAG, "Image URI provided: ${imageUri != null}")
            logToFile("Image URI provided: ${imageUri != null}")
            
            // Save the image if provided
            val imagePath = imageUri?.let { uri ->
                Log.d(TAG, "Attempting to save image from URI: $uri")
                logToFile("Attempting to save image from URI: $uri")
                Log.d(TAG, "URI details - scheme: ${uri.scheme}, path: ${uri.path}, authority: ${uri.authority}")
                logToFile("URI details - scheme: ${uri.scheme}, path: ${uri.path}, authority: ${uri.authority}")
                val path = saveImageFromUri(uri, wordId)
                if (path != null) {
                    Log.d(TAG, "Image saved successfully at: $path")
                    logToFile("Image saved successfully at: $path")
                    val file = File(path)
                    if (file.exists()) {
                        Log.d(TAG, "File exists with size: ${file.length()} bytes")
                        logToFile("File exists with size: ${file.length()} bytes")
                    } else {
                        Log.e(TAG, "File does not exist after saving")
                        logToFile("ERROR: File does not exist after saving")
                    }
                } else {
                    Log.e(TAG, "Failed to save image (path is null)")
                    logToFile("ERROR: Failed to save image (path is null)")
                }
                path
            }
            
            // Save word data to preferences
            val wordData = WordData(
                id = wordId,
                word = word,
                category = category,
                imagePath = imagePath
            )
            
            saveWordDataToPrefs(wordData)
            Log.d(TAG, "Word data saved successfully with imagePath: $imagePath")
            logToFile("Word data saved successfully with imagePath: $imagePath")
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom word", e)
            logToFile("ERROR: Error saving custom word: ${e.message}\n${e.stackTraceToString()}")
            return@withContext false
        }
    }
    
    /**
     * Updates an existing custom word
     */
    suspend fun updateCustomWord(
        wordId: String,
        word: String,
        category: String,
        imageUri: Uri?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating custom word - ID: $wordId, word: $word, category: $category")
            Log.d(TAG, "New image URI provided: ${imageUri != null}")
            
            // Get the existing word data
            val existingWordData = getWordDataFromPrefs(wordId) ?: run {
                Log.e(TAG, "Failed to find existing word data for ID: $wordId")
                return@withContext false
            }
            
            Log.d(TAG, "Existing word data - word: ${existingWordData.word}, category: ${existingWordData.category}, imagePath: ${existingWordData.imagePath}")
            
            // Update the image if a new one is provided
            val imagePath = if (imageUri != null) {
                // Delete the old image if it exists
                existingWordData.imagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val deleteSuccess = file.delete()
                        Log.d(TAG, "Deleted old image file: $deleteSuccess (path: $path)")
                    } else {
                        Log.d(TAG, "Old image file not found: $path")
                    }
                }
                
                // Save the new image
                Log.d(TAG, "Saving new image from URI: $imageUri")
                val path = saveImageFromUri(imageUri, wordId)
                if (path != null) {
                    Log.d(TAG, "New image saved successfully at: $path")
                    val file = File(path)
                    if (file.exists()) {
                        Log.d(TAG, "File exists with size: ${file.length()} bytes")
                    } else {
                        Log.e(TAG, "File does not exist after saving")
                    }
                } else {
                    Log.e(TAG, "Failed to save new image")
                }
                path
            } else {
                Log.d(TAG, "No new image provided, keeping existing imagePath: ${existingWordData.imagePath}")
                existingWordData.imagePath
            }
            
            // Update word data
            val updatedWordData = WordData(
                id = wordId,
                word = word,
                category = category,
                imagePath = imagePath
            )
            
            saveWordDataToPrefs(updatedWordData)
            Log.d(TAG, "Word data updated successfully with new imagePath: $imagePath")
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating custom word", e)
            return@withContext false
        }
    }
    
    /**
     * Deletes a custom word and its associated image
     */
    suspend fun deleteCustomWord(wordId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get the word data
            val wordData = getWordDataFromPrefs(wordId) ?: return@withContext false
            
            // Delete the image if it exists
            wordData.imagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            
            // Remove from preferences
            customWordsPrefs.edit {
                remove(wordId)
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting custom word", e)
            return@withContext false
        }
    }
    
    /**
     * Get all custom words
     */
    fun getAllCustomWords(): List<AACItem> {
        val items = mutableListOf<AACItem>()
        
        try {
            // Get all word IDs from preferences
            val allWords = customWordsPrefs.all
            
            for ((id, dataJson) in allWords) {
                try {
                    if (dataJson is String) {
                        val wordData = parseWordDataFromJson(dataJson)
                        
                        // Check if the image file exists
                        val imagePath = if (wordData.imagePath != null) {
                            val imageFile = File(wordData.imagePath)
                            if (imageFile.exists() && imageFile.length() > 0) {
                                Log.d(TAG, "Found valid image for word '${wordData.word}' at ${wordData.imagePath}")
                                wordData.imagePath
                            } else {
                                Log.w(TAG, "Image file missing or empty for word '${wordData.word}' at ${wordData.imagePath}")
                                null
                            }
                        } else null
                        
                        items.add(AACItem(
                            label = wordData.word,
                            category = wordData.category,
                            imagePath = imagePath
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing word data: $dataJson", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all custom words", e)
        }
        
        return items
    }
    
    /**
     * Get custom words by category
     */
    fun getCustomWordsByCategory(category: String): List<AACItem> {
        return getAllCustomWords().filter { it.category == category }
    }
    
    /**
     * Get a custom word by ID
     */
    fun getCustomWordById(wordId: String): AACItem? {
        val wordData = getWordDataFromPrefs(wordId) ?: return null
        
        // Check if the image file exists
        val imagePath = if (wordData.imagePath != null) {
            val imageFile = File(wordData.imagePath)
            if (imageFile.exists() && imageFile.length() > 0) {
                Log.d(TAG, "Found valid image for word '${wordData.word}' at ${wordData.imagePath}")
                wordData.imagePath
            } else {
                Log.w(TAG, "Image file missing or empty for word '${wordData.word}' at ${wordData.imagePath}")
                null
            }
        } else null
        
        return AACItem(
            label = wordData.word,
            category = wordData.category,
            imagePath = imagePath
        )
    }
    
    /**
     * Get all custom categories
     */
    fun getCustomCategories(): List<String> {
        return getAllCustomWords()
            .map { it.category }
            .distinct()
    }
    
    /**
     * Save image from URI to internal storage
     */
    private suspend fun saveImageFromUri(uri: Uri, wordId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting image save process for wordId: $wordId")
                logToFile("Starting image save process for wordId: $wordId")
                Log.d(TAG, "Input URI: $uri")
                logToFile("Input URI: $uri")
                Log.d(TAG, "URI scheme: ${uri.scheme}, path: ${uri.path}, authority: ${uri.authority}")
                logToFile("URI scheme: ${uri.scheme}, path: ${uri.path}, authority: ${uri.authority}")
                
                // Ensure the customImagesDir exists
                if (!customImagesDir.exists()) {
                    val created = customImagesDir.mkdirs()
                    Log.d(TAG, "Created customImagesDir: $created, path: ${customImagesDir.absolutePath}")
                    logToFile("Created customImagesDir: $created, path: ${customImagesDir.absolutePath}")
                } else {
                    Log.d(TAG, "customImagesDir already exists at: ${customImagesDir.absolutePath}")
                    logToFile("customImagesDir already exists at: ${customImagesDir.absolutePath}")
                }
                
                // Prepare the output file before attempting to read the input
                val outputFile = File(customImagesDir, "$wordId.webp")
                Log.d(TAG, "Output file path: ${outputFile.absolutePath}")
                logToFile("Output file path: ${outputFile.absolutePath}")
                
                if (outputFile.parentFile?.exists() != true) {
                    val created = outputFile.parentFile?.mkdirs()
                    Log.d(TAG, "Created parent directories: $created")
                    logToFile("Created parent directories: $created")
                }
                
                // Handle content:// and file:// URIs differently
                val bitmap = when (uri.scheme) {
                    "content" -> {
                        try {
                            logToFile("Using content resolver to open URI")
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                Log.e(TAG, "Failed to open input stream from URI")
                                logToFile("ERROR: Failed to open input stream from URI")
                                return@withContext null
                            }
                            
                            // Check if the input stream has content
                            val available = inputStream.available()
                            Log.d(TAG, "Input stream has $available bytes available")
                            logToFile("Input stream has $available bytes available")
                            if (available <= 0) {
                                Log.e(TAG, "Input stream has no available bytes")
                                logToFile("ERROR: Input stream has no available bytes")
                                inputStream.close()
                                return@withContext null
                            }
                            
                            // Read the entire stream into a byte array
                            val imageBytes = inputStream.readBytes()
                            inputStream.close()
                            Log.d(TAG, "Read ${imageBytes.size} bytes from input stream")
                            logToFile("Read ${imageBytes.size} bytes from input stream")
                            
                            if (imageBytes.isEmpty()) {
                                Log.e(TAG, "Image bytes array is empty")
                                logToFile("ERROR: Image bytes array is empty")
                                return@withContext null
                            }
                            
                            // Decode byte array into a bitmap
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading from content URI", e)
                            logToFile("ERROR: Error reading from content URI: ${e.message}")
                            null
                        }
                    }
                    "file" -> {
                        try {
                            logToFile("Decoding from file path")
                            val filePath = uri.path
                            if (filePath.isNullOrEmpty()) {
                                Log.e(TAG, "File path is null or empty from URI")
                                logToFile("ERROR: File path is null or empty from URI")
                                return@withContext null
                            }
                            BitmapFactory.decodeFile(filePath)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading from file URI", e)
                            logToFile("ERROR: Error reading from file URI: ${e.message}")
                            null
                        }
                    }
                    else -> {
                        // Try general approach for other schemes
                        try {
                            logToFile("Using general approach for URI scheme: ${uri.scheme}")
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            bitmap
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading from URI with scheme ${uri.scheme}", e)
                            logToFile("ERROR: Error reading from URI with scheme ${uri.scheme}: ${e.message}")
                            null
                        }
                    }
                }
                
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode image from URI")
                    logToFile("ERROR: Failed to decode image from URI")
                    return@withContext null
                }
                
                Log.d(TAG, "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
                logToFile("Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
                
                // Resize large images to a more appropriate size for AAC cards
                val resizedBitmap = if (bitmap.width > 800 || bitmap.height > 800) {
                    val scaleFactor = when {
                        bitmap.width >= bitmap.height -> 800f / bitmap.width
                        else -> 800f / bitmap.height
                    }
                    
                    val newWidth = (bitmap.width * scaleFactor).toInt()
                    val newHeight = (bitmap.height * scaleFactor).toInt()
                    
                    Log.d(TAG, "Resizing image from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight}")
                    logToFile("Resizing image from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight}")
                    
                    Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                } else {
                    bitmap
                }
                
                // Compress and save the bitmap to the file
                var success = false
                try {
                    FileOutputStream(outputFile).use { outputStream ->
                        // Reduce quality from 80 to 65 to get smaller file sizes while maintaining good image quality
                        success = resizedBitmap.compress(Bitmap.CompressFormat.WEBP, 65, outputStream)
                        outputStream.flush()
                    }
                    Log.d(TAG, "Compress operation success: $success")
                    logToFile("Compress operation success: $success")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during file write", e)
                    logToFile("ERROR: Error during file write: ${e.message}\n${e.stackTraceToString()}")
                    return@withContext null
                }
                
                if (!success) {
                    Log.e(TAG, "Failed to compress and save bitmap")
                    logToFile("ERROR: Failed to compress and save bitmap")
                    return@withContext null
                }
                Log.d(TAG, "Successfully compressed and saved bitmap")
                logToFile("Successfully compressed and saved bitmap")
                
                // Recycle the bitmap
                bitmap.recycle()
                // Recycle the resized bitmap if it's different from the original
                if (resizedBitmap != bitmap) {
                    resizedBitmap.recycle()
                }
                
                // Verify the file was created and has content
                if (!outputFile.exists()) {
                    Log.e(TAG, "Output file does not exist after saving")
                    logToFile("ERROR: Output file does not exist after saving")
                    return@withContext null
                }
                
                val fileSize = outputFile.length()
                Log.d(TAG, "Output file exists with size: $fileSize bytes")
                logToFile("Output file exists with size: $fileSize bytes")
                
                if (fileSize == 0L) {
                    Log.e(TAG, "Output file is empty")
                    logToFile("ERROR: Output file is empty")
                    outputFile.delete()
                    return@withContext null
                }
                
                return@withContext outputFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Error saving image from URI", e)
                logToFile("ERROR: Error saving image from URI: ${e.message}\n${e.stackTraceToString()}")
                return@withContext null
            }
        }
    
    /**
     * Load a bitmap from a file path
     */
    fun loadBitmapFromPath(path: String): Bitmap? {
        return try {
            Log.d(TAG, "Attempting to load bitmap from path: $path")
            val file = File(path)
            if (!file.exists()) {
                Log.e(TAG, "Image file does not exist at path: $path")
                return null
            }
            if (file.length() == 0L) {
                Log.e(TAG, "Image file is empty at path: $path")
                return null
            }
            Log.d(TAG, "File exists with size: ${file.length()} bytes")
            
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from path: $path")
            } else {
                Log.d(TAG, "Successfully loaded bitmap: ${bitmap.width}x${bitmap.height}")
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from path: $path", e)
            null
        }
    }
    
    /**
     * Save word data to SharedPreferences
     */
    private fun saveWordDataToPrefs(wordData: WordData) {
        customWordsPrefs.edit {
            putString(wordData.id, wordData.toJson())
        }
    }
    
    /**
     * Get word data from SharedPreferences
     */
    fun getWordDataFromPrefs(wordId: String): WordData? {
        val json = customWordsPrefs.getString(wordId, null) ?: return null
        return parseWordDataFromJson(json)
    }
    
    /**
     * Get all word IDs from preferences
     */
    fun getAllWordIds(): List<String> {
        return customWordsPrefs.all.keys.toList()
    }
    
    /**
     * Get the image path for a word by ID
     */
    fun getImagePathForWord(wordId: String): String? {
        val wordData = getWordDataFromPrefs(wordId) ?: return null
        
        // Check if the image file exists
        return if (wordData.imagePath != null) {
            val imageFile = File(wordData.imagePath)
            if (imageFile.exists() && imageFile.length() > 0) {
                Log.d(TAG, "Found valid image for word '${wordData.word}' at ${wordData.imagePath}")
                wordData.imagePath
            } else {
                Log.w(TAG, "Image file missing or empty for word '${wordData.word}' at ${wordData.imagePath}")
                null
            }
        } else null
    }
    
    /**
     * Parse word data from JSON
     */
    private fun parseWordDataFromJson(json: String): WordData {
        // A simple parsing implementation for demonstration
        // In a real app, use a JSON library like Gson or Moshi
        val parts = json.split("|")
        return WordData(
            id = parts[0],
            word = parts[1],
            category = parts[2],
            imagePath = if (parts.size > 3 && parts[3].isNotEmpty()) parts[3] else null
        )
    }
    
    /**
     * Data class for storing word information
     */
    data class WordData(
        val id: String,
        val word: String,
        val category: String,
        val imagePath: String?
    ) {
        /**
         * Convert to a simple string representation
         */
        fun toJson(): String {
            // A simple string representation for demonstration
            // In a real app, use a JSON library like Gson or Moshi
            return "$id|$word|$category|${imagePath ?: ""}"
        }
    }
    
    /**
     * Creates a backup of all custom words and their images
     * Returns the Uri of the backup file if successful, or null if it fails
     */
    suspend fun createBackup(): Uri? = withContext(Dispatchers.IO) {
        try {
            // Create backup filename with timestamp
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val backupFile = File(backupsDir, "vocabulary_backup_$timestamp.zip")
            
            // Create zip file
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                // Add words data as JSON
                val wordsJson = createWordsBackupJson()
                zipOut.putNextEntry(ZipEntry("words.json"))
                zipOut.write(wordsJson.toString().toByteArray())
                zipOut.closeEntry()
                
                // Add all images
                val allWords = getAllWordData()
                allWords.forEach { wordData ->
                    wordData.imagePath?.let { path ->
                        val imageFile = File(path)
                        if (imageFile.exists()) {
                            val imageFileName = imageFile.name
                            zipOut.putNextEntry(ZipEntry("images/$imageFileName"))
                            FileInputStream(imageFile).use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
            
            // Return the URI to the backup file
            return@withContext Uri.fromFile(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup", e)
            return@withContext null
        }
    }
    
    /**
     * Restores a backup from the given URI
     * Returns true if successful, false otherwise
     */
    suspend fun restoreFromBackup(backupUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Clear existing custom words and images
            clearAllCustomWords()
            
            // Open the zip file
            val inputStream = context.contentResolver.openInputStream(backupUri)
                ?: return@withContext false
            
            // Extract the backup
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when {
                        // Process words.json
                        entry.name == "words.json" -> {
                            val wordsJson = zipIn.readBytes().toString(Charsets.UTF_8)
                            restoreWordsFromJson(wordsJson)
                        }
                        
                        // Process images
                        entry.name.startsWith("images/") -> {
                            val fileName = File(entry.name).name
                            val outputFile = File(customImagesDir, fileName)
                            FileOutputStream(outputFile).use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                    }
                    
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup", e)
            return@withContext false
        }
    }
    
    /**
     * Get a list of all available backups
     */
    fun getAvailableBackups(): List<File> {
        return backupsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".zip")
        }?.toList() ?: emptyList()
    }
    
    /**
     * Creates a JSON object containing all word data for backup
     */
    private fun createWordsBackupJson(): JSONObject {
        val rootJson = JSONObject()
        val wordsArray = JSONArray()
        
        // Get all word IDs from preferences
        val allWords = customWordsPrefs.all
        
        for ((id, dataJson) in allWords) {
            try {
                if (dataJson is String) {
                    val wordData = parseWordDataFromJson(dataJson)
                    val wordJson = JSONObject()
                    wordJson.put("id", wordData.id)
                    wordJson.put("word", wordData.word)
                    wordJson.put("category", wordData.category)
                    
                    // Store just the filename for the image, not the full path
                    wordData.imagePath?.let { path ->
                        val imageFile = File(path)
                        wordJson.put("image", imageFile.name)
                    }
                    
                    wordsArray.put(wordJson)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding word to backup: $dataJson", e)
            }
        }
        
        rootJson.put("words", wordsArray)
        rootJson.put("version", 1) // For future compatibility
        
        return rootJson
    }
    
    /**
     * Restore words from a JSON backup
     */
    private fun restoreWordsFromJson(jsonString: String) {
        try {
            val rootJson = JSONObject(jsonString)
            val wordsArray = rootJson.getJSONArray("words")
            
            // Clear existing preferences first
            customWordsPrefs.edit {
                clear()
            }
            
            // Restore each word
            for (i in 0 until wordsArray.length()) {
                val wordJson = wordsArray.getJSONObject(i)
                val id = wordJson.getString("id")
                val word = wordJson.getString("word")
                val category = wordJson.getString("category")
                
                // Determine image path if it exists
                val imagePath = if (wordJson.has("image")) {
                    val imageFileName = wordJson.getString("image")
                    File(customImagesDir, imageFileName).absolutePath
                } else {
                    null
                }
                
                // Create word data and save
                val wordData = WordData(
                    id = id,
                    word = word, 
                    category = category,
                    imagePath = imagePath
                )
                
                saveWordDataToPrefs(wordData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring words from JSON", e)
        }
    }
    
    /**
     * Clear all custom words and their images
     */
    private fun clearAllCustomWords() {
        try {
            // Clear SharedPreferences
            customWordsPrefs.edit {
                clear()
            }
            
            // Delete all image files
            customImagesDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing custom words", e)
        }
    }
    
    /**
     * Get all word data objects from preferences
     */
    private fun getAllWordData(): List<WordData> {
        val wordDataList = mutableListOf<WordData>()
        
        try {
            val allWords = customWordsPrefs.all
            
            for ((id, dataJson) in allWords) {
                try {
                    if (dataJson is String) {
                        val wordData = parseWordDataFromJson(dataJson)
                        wordDataList.add(wordData)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing word data: $dataJson", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all word data", e)
        }
        
        return wordDataList
    }
    
    /**
     * Get the path to the debug log file
     */
    fun getDebugLogFilePath(): String {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, "debug_log.txt").absolutePath
    }
    
    /**
     * Clear the debug log file
     */
    fun clearDebugLog() {
        try {
            val logFile = File(getDebugLogFilePath())
            if (logFile.exists()) {
                logFile.delete()
                logToFile("Log file cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing debug log file", e)
        }
    }
    
    /**
     * Reset vocabulary to defaults by clearing all custom words
     * Returns true if successful
     */
    fun resetToDefaultVocabulary(): Boolean {
        return try {
            // Clear SharedPreferences
            customWordsPrefs.edit {
                clear()
                apply()
            }
            
            // Delete all image files
            customImagesDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            
            Log.d(TAG, "Vocabulary reset to defaults successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting vocabulary to defaults", e)
            false
        }
    }
} 