package com.freeaac.communicationbuddy.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.speech.tts.TextToSpeech
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.freeaac.communicationbuddy.R
import androidx.compose.foundation.layout.size
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import com.freeaac.communicationbuddy.data.VocabularyManager
import android.content.Context
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import com.freeaac.communicationbuddy.data.prediction.WordPredictionManager
import com.freeaac.communicationbuddy.ui.prediction.PredictionBar

// Data classes representing an AAC card item and its category.
data class AACItem(
    val label: String,
    val imageRes: Int? = null,
    val category: String = "",  // Added category field
    val imagePath: String? = null  // Added image path field for custom images
)
data class AACCategory(val name: String, val items: List<AACItem>)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AACMainScreen(
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Initialize vocabulary manager
    val vocabularyManager = remember { VocabularyManager(context) }
    
    // Initialize word prediction manager
    val wordPredictionManager = remember { WordPredictionManager(context) }
    
    // Get custom words
    val customWords = remember { vocabularyManager.getAllCustomWords() }
    
    // Read settings from SharedPreferences
    val sharedPrefs = context.getSharedPreferences("communication_buddy_settings", Context.MODE_PRIVATE)
    
    // Get grid size from settings (default to 5)
    val gridSize = sharedPrefs.getInt("grid_size", 5)
    
    // Get text size from settings (default to "Medium")
    val textSizePreference = sharedPrefs.getString("text_size", "Medium") ?: "Medium"
    
    // Get high contrast mode setting
    val highContrastMode = sharedPrefs.getBoolean("high_contrast_mode", false)
    
    // Get auto-speak setting
    val autoSpeakWords = sharedPrefs.getBoolean("auto_speak_words", true)
    
    // Get word prediction setting
    val wordPredictionEnabled = sharedPrefs.getBoolean("word_prediction_enabled", true)
    
    // Get text style based on settings
    val textStyle = getTextStyleForPreference(textSizePreference)
    
    // Get recent words from SharedPreferences
    val recentWordsJson = sharedPrefs.getString("recent_words", "[]")
    var recentWords by remember { 
        mutableStateOf(parseRecentWordsFromJson(recentWordsJson ?: "[]")) 
    }
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    
    // State to track TTS initialization status
    var ttsStatus by remember { mutableStateOf(TextToSpeech.ERROR) }
    var ttsErrorMessage by remember { mutableStateOf<String?>(null) }
    
    // TTS setup with speech rate and pitch from settings
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    
    // State for the sentence builder
    var sentenceWords by remember { mutableStateOf(listOf<String>()) }
    
    // State for word predictions
    var currentPredictions by remember { mutableStateOf(emptyList<String>()) }
    
    // Helper function to speak text safely
    fun speak(text: String) {
        ttsRef.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    // Function to add a word to recent words
    fun addToRecentWords(item: AACItem) {
        // Create a new list with the selected word at the beginning
        val updatedRecentWords = (listOf(item) + recentWords)
            // Remove duplicates, keeping only the first occurrence
            .distinctBy { it.label }
            // Limit to 20 items
            .take(20)
        
        // Update state
        recentWords = updatedRecentWords
        
        // Save to SharedPreferences
        val jsonArray = JSONArray()
        updatedRecentWords.forEach { word ->
            val wordJson = JSONObject()
            wordJson.put("label", word.label)
            wordJson.put("category", word.category)
            jsonArray.put(wordJson)
        }
        
        sharedPrefs.edit()
            .putString("recent_words", jsonArray.toString())
            .apply()
    }
    
    DisposableEffect(Unit) {
        val speechRate = sharedPrefs.getFloat("speech_rate", 1.0f)
        val speechPitch = sharedPrefs.getFloat("speech_pitch", 1.0f)
        // Get saved voice name if available
        val savedVoiceName = sharedPrefs.getString("voice_name", null)
        
        // Initialize TTS instance
        val newTts = try {
            TextToSpeech(context) { status ->
                ttsStatus = status
                
                if (status == TextToSpeech.SUCCESS) {
                    // This will be called asynchronously after initialization
                    val tts = ttsRef.value ?: return@TextToSpeech
                    
                    try {
                        // Apply speech settings
                        tts.setSpeechRate(speechRate)
                        tts.setPitch(speechPitch)
                        
                        // Check if any voices are available first
                        if (tts.voices?.isEmpty() == true) {
                            ttsErrorMessage = "No Text-to-Speech voices found. Please install a TTS engine."
                            return@TextToSpeech
                        }
                        
                        // Apply saved voice if available
                        if (savedVoiceName != null) {
                            val voices = tts.voices
                            val matchingVoice = voices?.find { it.name == savedVoiceName }
                            
                            if (matchingVoice != null) {
                                try {
                                    tts.voice = matchingVoice
                                } catch (e: Exception) {
                                    // If setting the voice fails, try to use the default voice
                                    ttsErrorMessage = "Could not set saved voice. Using default voice instead."
                                }
                            } else {
                                ttsErrorMessage = "Saved voice '$savedVoiceName' not found. Using default voice."
                            }
                        }
                    } catch (e: Exception) {
                        ttsErrorMessage = "Error initializing TTS: ${e.message}"
                    }
                } else {
                    when (status) {
                        TextToSpeech.ERROR -> ttsErrorMessage = "Error initializing TTS engine"
                        TextToSpeech.LANG_MISSING_DATA -> ttsErrorMessage = "TTS language data is missing"
                        TextToSpeech.LANG_NOT_SUPPORTED -> ttsErrorMessage = "TTS language is not supported"
                        else -> ttsErrorMessage = "Unknown TTS error (code: $status)"
                    }
                }
            }
        } catch (e: Exception) {
            // Handle initialization exceptions
            ttsErrorMessage = "Failed to initialize TTS: ${e.message}"
            null
        }
        
        // Set reference immediately
        ttsRef.value = newTts
        
        onDispose {
            ttsRef.value?.shutdown()
            ttsRef.value = null
        }
    }
    
    // Define AAC categories using the complete list for Communication Buddy.
    val categoriesData = listOf(
        AACCategory("People", listOf(
            AACItem("mom"),
            AACItem("dad"),
            AACItem("brother"),
            AACItem("sister"),
            AACItem("grandma"),
            AACItem("grandpa"),
            AACItem("friend"),
            AACItem("teacher"),
            AACItem("doctor"),
            AACItem("me"),
            AACItem("you")
        )),
        AACCategory("Actions", listOf(
            AACItem("go"),
            AACItem("come"),
            AACItem("eat"),
            AACItem("drink"),
            AACItem("play"),
            AACItem("sleep"),
            AACItem("read"),
            AACItem("write"),
            AACItem("draw"),
            AACItem("run"),
            AACItem("jump"),
            AACItem("sit"),
            AACItem("stand"),
            AACItem("look"),
            AACItem("listen"),
            AACItem("talk"),
            AACItem("help"),
            AACItem("want"),
            AACItem("need"),
            AACItem("like"),
            AACItem("stop")
        )),
        AACCategory("Describing Words", listOf(
            AACItem("big"),
            AACItem("small"),
            AACItem("happy"),
            AACItem("sad"),
            AACItem("good"),
            AACItem("bad"),
            AACItem("hot"),
            AACItem("cold"),
            AACItem("fast"),
            AACItem("slow"),
            AACItem("loud"),
            AACItem("quiet"),
            AACItem("pretty"),
            AACItem("ugly"),
            AACItem("new"),
            AACItem("old")
        )),
        AACCategory("Places", listOf(
            AACItem("home"),
            AACItem("school"),
            AACItem("park"),
            AACItem("store"),
            AACItem("restaurant"),
            AACItem("library"),
            AACItem("playground"),
            AACItem("beach"),
            AACItem("zoo"),
            AACItem("friend's house")
        )),
        AACCategory("Things", listOf(
            AACItem("toy"),
            AACItem("book"),
            AACItem("ball"),
            AACItem("car"),
            AACItem("bike"),
            AACItem("chair"),
            AACItem("table"),
            AACItem("bed"),
            AACItem("door"),
            AACItem("window"),
            AACItem("phone"),
            AACItem("TV"),
            AACItem("computer"),
            AACItem("pencil"),
            AACItem("paper"),
            AACItem("backpack")
        )),
        AACCategory("Emotions", listOf(
            AACItem("happy"),
            AACItem("sad"),
            AACItem("angry"),
            AACItem("scared"),
            AACItem("surprised"),
            AACItem("excited"),
            AACItem("tired"),
            AACItem("bored")
        )),
        AACCategory("Time", listOf(
            AACItem("now"),
            AACItem("later"),
            AACItem("morning"),
            AACItem("afternoon"),
            AACItem("night"),
            AACItem("today"),
            AACItem("tomorrow"),
            AACItem("yesterday")
        )),
        AACCategory("Numbers", listOf(
            AACItem("1"),
            AACItem("2"),
            AACItem("3"),
            AACItem("4"),
            AACItem("5"),
            AACItem("6"),
            AACItem("7"),
            AACItem("8"),
            AACItem("9"),
            AACItem("10"),
            AACItem("first"),
            AACItem("second"),
            AACItem("third")
        )),
        AACCategory("Colors", listOf(
            AACItem("red"),
            AACItem("blue"),
            AACItem("green"),
            AACItem("yellow"),
            AACItem("orange"),
            AACItem("purple"),
            AACItem("pink"),
            AACItem("black"),
            AACItem("white"),
            AACItem("brown")
        )),
        AACCategory("Shapes", listOf(
            AACItem("circle"),
            AACItem("square"),
            AACItem("triangle"),
            AACItem("rectangle"),
            AACItem("star"),
            AACItem("heart")
        )),
        AACCategory("School", listOf(
            AACItem("teacher"),
            AACItem("classmate"),
            AACItem("desk"),
            AACItem("chair"),
            AACItem("book"),
            AACItem("pencil"),
            AACItem("paper"),
            AACItem("crayons"),
            AACItem("glue"),
            AACItem("scissors"),
            AACItem("backpack"),
            AACItem("lunchbox"),
            AACItem("playground"),
            AACItem("gym"),
            AACItem("art"),
            AACItem("music"),
            AACItem("math"),
            AACItem("reading"),
            AACItem("writing")
        )),
        AACCategory("Sports", listOf(
            AACItem("soccer"),
            AACItem("basketball"),
            AACItem("baseball"),
            AACItem("swimming"),
            AACItem("running"),
            AACItem("jumping"),
            AACItem("throwing"),
            AACItem("catching")
        )),
        AACCategory("Holidays", listOf(
            AACItem("birthday"),
            AACItem("Christmas"),
            AACItem("Halloween"),
            AACItem("Easter"),
            AACItem("Thanksgiving"),
            AACItem("New Year")
        )),
        AACCategory("Food", listOf(
            AACItem("apple"),
            AACItem("banana"),
            AACItem("orange"),
            AACItem("grapes"),
            AACItem("strawberry"),
            AACItem("carrot"),
            AACItem("broccoli"),
            AACItem("pizza"),
            AACItem("sandwich"),
            AACItem("burger"),
            AACItem("fries"),
            AACItem("chicken"),
            AACItem("fish"),
            AACItem("rice"),
            AACItem("pasta"),
            AACItem("bread"),
            AACItem("cheese"),
            AACItem("milk"),
            AACItem("juice"),
            AACItem("water"),
            AACItem("cookie"),
            AACItem("cake"),
            AACItem("ice cream")
        )),
        AACCategory("Clothing", listOf(
            AACItem("shirt"),
            AACItem("pants"),
            AACItem("shorts"),
            AACItem("dress"),
            AACItem("skirt"),
            AACItem("socks"),
            AACItem("shoes"),
            AACItem("hat"),
            AACItem("jacket"),
            AACItem("sweater"),
            AACItem("pajamas")
        )),
        AACCategory("Weather", listOf(
            AACItem("sunny"),
            AACItem("cloudy"),
            AACItem("rainy"),
            AACItem("snowy"),
            AACItem("windy"),
            AACItem("hot"),
            AACItem("cold")
        )),
        AACCategory("Health", listOf(
            AACItem("sick"),
            AACItem("hurt"),
            AACItem("pain"),
            AACItem("headache"),
            AACItem("stomachache"),
            AACItem("cough"),
            AACItem("sneeze"),
            AACItem("doctor"),
            AACItem("nurse"),
            AACItem("medicine"),
            AACItem("band-aid")
        )),
        AACCategory("Social Interactions", listOf(
            AACItem("hello"),
            AACItem("goodbye"),
            AACItem("please"),
            AACItem("thank you"),
            AACItem("sorry"),
            AACItem("excuse me"),
            AACItem("yes"),
            AACItem("no"),
            AACItem("okay"),
            AACItem("help"),
            AACItem("share"),
            AACItem("turn"),
            AACItem("wait")
        )),
        AACCategory("Questions", listOf(
            AACItem("who"),
            AACItem("what"),
            AACItem("where"),
            AACItem("when"),
            AACItem("why"),
            AACItem("how")
        )),
        AACCategory("Commands", listOf(
            AACItem("stop"),
            AACItem("go"),
            AACItem("wait"),
            AACItem("come"),
            AACItem("sit"),
            AACItem("stand"),
            AACItem("look"),
            AACItem("listen"),
            AACItem("quiet")
        )),
        AACCategory("Create", listOf(
            AACItem("draw"),
            AACItem("paint"),
            AACItem("color"),
            AACItem("build"),
            AACItem("make"),
            AACItem("cut"),
            AACItem("glue"),
            AACItem("paper"),
            AACItem("crayons"),
            AACItem("markers"),
            AACItem("scissors"),
            AACItem("playdough")
        )),
        AACCategory("Daily Life", listOf(
            AACItem("wake up"),
            AACItem("get dressed"),
            AACItem("brush teeth"),
            AACItem("wash face"),
            AACItem("eat breakfast"),
            AACItem("go to school"),
            AACItem("play"),
            AACItem("eat lunch"),
            AACItem("nap"),
            AACItem("snack"),
            AACItem("dinner"),
            AACItem("bath"),
            AACItem("bedtime")
        )),
        AACCategory("Let's Talk", listOf(
            AACItem("hi"),
            AACItem("hello"),
            AACItem("how are you"),
            AACItem("good"),
            AACItem("fine"),
            AACItem("what's up"),
            AACItem("bye"),
            AACItem("see you later")
        )),
        AACCategory("Motor Play", listOf(
            AACItem("run"),
            AACItem("jump"),
            AACItem("climb"),
            AACItem("swing"),
            AACItem("slide"),
            AACItem("throw"),
            AACItem("catch"),
            AACItem("kick"),
            AACItem("dance")
        )),
        AACCategory("Out & About", listOf(
            AACItem("park"),
            AACItem("playground"),
            AACItem("store"),
            AACItem("restaurant"),
            AACItem("library"),
            AACItem("zoo"),
            AACItem("museum"),
            AACItem("beach"),
            AACItem("car"),
            AACItem("bus"),
            AACItem("walk")
        )),
        AACCategory("Reading", listOf(
            AACItem("book"),
            AACItem("story"),
            AACItem("read"),
            AACItem("page"),
            AACItem("picture"),
            AACItem("word"),
            AACItem("letter"),
            AACItem("sound")
        )),
        AACCategory("Toys & Games", listOf(
            AACItem("doll"),
            AACItem("action figure"),
            AACItem("car"),
            AACItem("truck"),
            AACItem("train"),
            AACItem("blocks"),
            AACItem("puzzle"),
            AACItem("game"),
            AACItem("ball"),
            AACItem("bike"),
            AACItem("swing"),
            AACItem("slide")
        )),
        AACCategory("Animals", listOf(
            AACItem("dog"),
            AACItem("cat"),
            AACItem("bird"),
            AACItem("fish"),
            AACItem("horse"),
            AACItem("cow"),
            AACItem("pig"),
            AACItem("chicken"),
            AACItem("duck"),
            AACItem("rabbit")
        )),
        AACCategory("Body Parts", listOf(
            AACItem("head"),
            AACItem("hair"),
            AACItem("eyes"),
            AACItem("nose"),
            AACItem("mouth"),
            AACItem("ears"),
            AACItem("arms"),
            AACItem("hands"),
            AACItem("fingers"),
            AACItem("legs"),
            AACItem("feet"),
            AACItem("toes")
        ))
    )
    
    // Add custom words to the appropriate categories
    val categoriesWithCustomWords = remember(customWords) {
        val customCategories = customWords.map { it.category }.distinct()
        
        // Create a map of the original categories for easier access
        val categoriesMap = categoriesData.associateBy { it.name }
        
        // Create the updated list with custom words
        val updatedCategories = categoriesData.toMutableList()
        
        // For each custom category
        customCategories.forEach { customCategoryName ->
            val customCategoryWords = customWords.filter { it.category == customCategoryName }
            
            // If this category already exists, add words to it
            val existingCategory = categoriesMap[customCategoryName]
            if (existingCategory != null) {
                val existingIndex = updatedCategories.indexOf(existingCategory)
                updatedCategories[existingIndex] = AACCategory(
                    name = customCategoryName,
                    items = existingCategory.items + customCategoryWords
                )
            } else {
                // If it's a new category, add it
                updatedCategories.add(
                    AACCategory(
                        name = customCategoryName,
                        items = customCategoryWords
                    )
                )
            }
        }
        
        updatedCategories
    }
    
    // Create a Recents category with recent words
    val recentsCategory = AACCategory(
        name = "Recents",
        items = recentWords
    )
    
    // Add recents to categories list
    val categoriesWithRecents = remember(categoriesWithCustomWords, recentWords) {
        if (recentWords.isEmpty()) {
            categoriesWithCustomWords
        } else {
            listOf(recentsCategory) + categoriesWithCustomWords
        }
    }
    
    // Build the category list for the bar with Recents first, then All, then other categories
    val categoriesList = if (recentWords.isEmpty()) {
        listOf("All") + categoriesWithCustomWords.map { it.name }
    } else {
        listOf("Recents", "All") + categoriesWithCustomWords.map { it.name }.filter { it != "Recents" }
    }
    
    // Hold the user-selected category. Default to Recents if available, otherwise All
    var selectedCategory by remember { 
        mutableStateOf(if (recentWords.isNotEmpty()) "Recents" else "All") 
    }
    
    // Compute the AAC items to display based on the selected category
    val displayedItems = when (selectedCategory) {
        "Recents" -> recentWords
        "All" -> categoriesWithCustomWords.flatMap { category ->
            category.items.map { item -> 
                // Ensure each item has its category set
                item.copy(category = category.name) 
            }
        }.distinctBy { it.label }
        else -> categoriesWithCustomWords.firstOrNull { it.name == selectedCategory }?.items?.map { item ->
            // Ensure each item has its category set
            item.copy(category = selectedCategory)
        } ?: emptyList()
    }
    
    // Filter displayed items based on search query
    val filteredItems = if (searchQuery.isNotEmpty()) {
        displayedItems.filter { item ->
            item.label.contains(searchQuery, ignoreCase = true) ||
            item.category.contains(searchQuery, ignoreCase = true)
        }
    } else {
        displayedItems
    }
    
    // Show a header for the Recents category
    val showRecentsHeader = selectedCategory == "Recents" && !isSearchActive && recentWords.isNotEmpty()
    
    // Generate predictions when sentence changes
    LaunchedEffect(sentenceWords) {
        if (wordPredictionEnabled) {
            currentPredictions = wordPredictionManager.generatePredictions(sentenceWords)
        } else {
            currentPredictions = emptyList()
        }
    }
    
    // Function to handle when a prediction is selected
    fun onPredictionSelected(word: String) {
        // Add the word to the sentence
        sentenceWords = sentenceWords + word
        
        if (autoSpeakWords) {
            speak(word)
        }
        
        // Find the word in the categories and add it to recents
        val item = categoriesWithCustomWords.flatMap { category ->
            category.items.map { it.copy(category = category.name) }
        }.find { it.label == word }
        
        item?.let { addToRecentWords(it) }
        
        // Train the prediction model
        wordPredictionManager.learnWord(word)
    }
    
    Scaffold(
        topBar = {
            if (isSearchActive) {
                // Search TopAppBar
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("Search words...") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchActive = false
                            searchQuery = ""
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
                
                // Request focus after composition
                DisposableEffect(Unit) {
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        focusRequester.requestFocus()
                    }
                    onDispose { }
                }
            } else {
                // Regular TopAppBar
                TopAppBar(
                    title = { Text("Communication Buddy") },
                    actions = {
                        IconButton(onClick = { 
                            isSearchActive = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show TTS warning banner if there are issues
            if (ttsStatus != TextToSpeech.SUCCESS || ttsErrorMessage != null) {
                TTSWarningBanner(
                    errorMessage = ttsErrorMessage,
                    onSettingsClick = onNavigateToSettings
                )
            }
            
            // Sentence builder component
            SentenceBuilder(
                words = sentenceWords,
                onClearSentence = { 
                    // When clearing the sentence, also learn from it for future predictions
                    if (sentenceWords.isNotEmpty()) {
                        wordPredictionManager.learnFromSentence(sentenceWords)
                    }
                    sentenceWords = emptyList() 
                },
                onSpeakSentence = { 
                    speak(sentenceWords.joinToString(" "))
                    // When speaking the full sentence, learn from it for future predictions
                    wordPredictionManager.learnFromSentence(sentenceWords)
                },
                onRemoveWord = { index -> 
                    sentenceWords = sentenceWords.filterIndexed { i, _ -> i != index }
                },
                onWordAdded = { word ->
                    // Find the word in the categories and add it to recents
                    val item = categoriesWithCustomWords.flatMap { category ->
                        category.items.map { it.copy(category = category.name) }
                    }.find { it.label == word }
                    
                    item?.let { addToRecentWords(it) }
                }
            )
            
            // Add the prediction bar if enabled
            if (wordPredictionEnabled && currentPredictions.isNotEmpty()) {
                PredictionBar(
                    predictions = currentPredictions,
                    onPredictionSelected = { word -> onPredictionSelected(word) }
                )
            }
            
            // Only show the category bar if not in search mode
            if (!isSearchActive) {
                CategoryGroupBar(
                    categories = categoriesList,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            } else if (searchQuery.isNotEmpty() && filteredItems.isNotEmpty()) {
                // Show search result count
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Found ${filteredItems.size} ${if (filteredItems.size == 1) "result" else "results"} for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Show Recents header if needed
            if (showRecentsHeader) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Used Words",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Show "No results found" message when search has no matches
            if (isSearchActive && searchQuery.isNotEmpty() && filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try a different search term or check spelling",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Grid display of items
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridSize),
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                ) {
                    items(filteredItems) { item ->
                        CategoryCard(
                            label = item.label,
                            imageRes = item.imageRes,
                            category = item.category,
                            imagePath = item.imagePath,
                            textStyle = textStyle,
                            highContrastMode = highContrastMode,
                            onClick = {
                                if (autoSpeakWords) {
                                    speak(item.label)
                                }
                                sentenceWords = sentenceWords + item.label
                                
                                // Add to recent words when clicked
                                addToRecentWords(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TTSWarningBanner(
    errorMessage: String?,
    onSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Text-to-Speech Issue",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = errorMessage ?: "Speech functionality may not work correctly. Tap Settings to configure.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onSettingsClick) {
                Text("Settings")
            }
        }
    }
}

// Helper function to get text style based on preference
fun getTextStyleForPreference(textSizePreference: String): TextStyle {
    return when (textSizePreference) {
        "Small" -> TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        "Medium" -> TextStyle(
            fontSize = 19.sp,
            fontWeight = FontWeight.Black
        )
        "Large" -> TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        "Extra Large" -> TextStyle(
            fontSize = 24.sp, 
            fontWeight = FontWeight.ExtraBold
        )
        else -> TextStyle(
            fontSize = 19.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryGroupBar(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = categories) { category ->
            // Determine special styling for Recents category
            val isRecentsCategory = category == "Recents"
            
            Card(
                modifier = Modifier
                    .clickable { onCategorySelected(category) }
                    .padding(4.dp),
                colors = when {
                    // Selected Recents category
                    selectedCategory == category && isRecentsCategory -> 
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    // Selected regular category
                    selectedCategory == category -> 
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    // Non-selected Recents category
                    isRecentsCategory -> 
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    // Non-selected regular category
                    else -> 
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            // Selected Recents category
                            selectedCategory == category && isRecentsCategory ->
                                MaterialTheme.colorScheme.onTertiary
                            // Selected regular category
                            selectedCategory == category ->
                                MaterialTheme.colorScheme.onPrimary
                            // Non-selected Recents category
                            isRecentsCategory ->
                                MaterialTheme.colorScheme.onTertiaryContainer
                            // Non-selected regular category
                            else ->
                                MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    label: String, 
    imageRes: Int? = null, 
    category: String = "", 
    imagePath: String? = null,  // Add a parameter for imagePath
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Black),
    highContrastMode: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Get the vocabulary manager to check for custom images
    val vocabularyManager = remember { VocabularyManager(context) }
    
    // Determine card background color based on high contrast mode
    val cardBackgroundColor = if (highContrastMode) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    // Determine text background color based on high contrast mode
    val textBackgroundColor = if (highContrastMode) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }
    
    Card(
        modifier = Modifier
            .padding(6.dp)
            .aspectRatio(1f)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Image container
        Box(
            modifier = Modifier
                    .weight(0.65f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Check if this item has an imagePath (custom image)
                val customImageBitmap = remember(imagePath) {
                    if (imagePath != null && imagePath.isNotEmpty()) {
                        val bitmap = vocabularyManager.loadBitmapFromPath(imagePath)
                        bitmap?.asImageBitmap()
                    } else null
                }
                
                if (customImageBitmap != null) {
                    // If we have a custom image, use it
                    Log.d("CategoryCard", "Using custom image for $label from path: $imagePath")
                    Image(
                        bitmap = customImageBitmap,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Otherwise try to load image from assets based on both category and label
                    val assetBitmap = remember(label, category) {
                        // First try with category-specific image naming
                        if (category.isNotEmpty()) {
                            // Try category-specific file first (e.g. "animals/chicken.png" or "food/chicken.png")
                            val categoryVariations = listOf(
                                "$category/$label",               // e.g. "animals/chicken"
                                "${category.lowercase()}/${label.lowercase()}",  // e.g. "animals/chicken"
                                "${category.lowercase()}${label.replaceFirstChar { it.uppercase() }}",  // e.g. "animalsChicken"
                                "${category.lowercase()}_${label.lowercase()}"   // e.g. "animals_chicken"
                            ).distinct()
                            
                            // Try each category variation
                            for (variation in categoryVariations) {
                                try {
                                    context.assets.open("$variation.png").use { inputStream ->
                                        BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                                            return@remember bitmap.asImageBitmap()
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Try next variation
                                }
                            }
                        }
                        
                        // If category-specific image not found, try with just the label
                        val variations = listOf(
                            label,                    // Original (e.g., "Hello")
                            label.lowercase(),        // All lowercase (e.g., "hello")
                            label.uppercase(),        // All uppercase (e.g., "HELLO")
                            label.replaceFirstChar { it.uppercase() }, // First letter capital
                            label.replace(" ", "-"),  // Replace spaces with hyphens (e.g., "hello-world")
                            label.replace(" ", "_")   // Replace spaces with underscores (e.g., "hello_world")
                        ).distinct()
                        
                        // Try each variation
                        for (variation in variations) {
                            try {
                                context.assets.open("$variation.png").use { inputStream ->
                                    BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                                        return@remember bitmap.asImageBitmap()
                                    }
                                }
                            } catch (e: Exception) {
                                // Try next variation
                            }
                        }
                        
                        // If all variations failed, return null
                        null
                    }
                    
                    if (assetBitmap != null) {
                        // If we found an asset image, use it
                    Image(
                            bitmap = assetBitmap,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    } else if (imageRes != null) {
                        // If asset not found but we have a resource, use that
                    Image(
                            painter = painterResource(id = imageRes),
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    } else {
                        // If no image available, show first letter of label
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            // Text label container
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth()
                    .padding(top = 1.dp)
                    .padding(horizontal = 2.dp)
                    .background(
                        color = textBackgroundColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
                    .shadow(elevation = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label, 
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    lineHeight = if (textStyle.fontSize.value > 20) (textStyle.fontSize.value * 1.1).sp else 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (highContrastMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 1.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SentenceBuilder(
    words: List<String>,
    onClearSentence: () -> Unit,
    onSpeakSentence: () -> Unit,
    onRemoveWord: (Int) -> Unit,
    onWordAdded: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Sentence display area
            if (words.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap words to build a sentence",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(items = words) { index, word ->
                        Card(
                            modifier = Modifier
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                .clickable { onRemoveWord(index) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
            
            // Action buttons for the sentence
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onClearSentence,
                    enabled = words.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear")
                }
                
                Button(
                    onClick = onSpeakSentence,
                    enabled = words.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Speak Sentence")
                }
            }
        }
    }
}

@Composable
fun ExampleImage() {
    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Image not provided")
    }
}

// Helper function to parse recent words from JSON string
private fun parseRecentWordsFromJson(jsonString: String): List<AACItem> {
    return try {
        val jsonArray = JSONArray(jsonString)
        val words = mutableListOf<AACItem>()
        
        for (i in 0 until jsonArray.length()) {
            val wordJson = jsonArray.getJSONObject(i)
            val label = wordJson.getString("label")
            val category = wordJson.getString("category")
            words.add(AACItem(label = label, category = category))
        }
        
        words
    } catch (e: Exception) {
        emptyList()
    }
} 
