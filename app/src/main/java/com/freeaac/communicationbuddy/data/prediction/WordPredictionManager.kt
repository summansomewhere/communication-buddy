package com.freeaac.communicationbuddy.data.prediction

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.freeaac.communicationbuddy.data.VocabularyManager
import com.freeaac.communicationbuddy.ui.AACItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Manages word prediction functionality for the Communication Buddy app.
 * 
 * Provides predictions based on:
 * 1. Current sentence context
 * 2. Frequency of word usage (usage stats)
 * 3. Common word combinations (bigrams)
 */
class WordPredictionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WordPredictionManager"
        private const val PREDICTION_PREFS = "prediction_prefs"
        private const val WORD_USAGE_KEY = "word_usage_stats"
        private const val WORD_PAIRS_KEY = "word_pairs_stats"
        private const val MAX_PREDICTIONS = 5
    }
    
    private val vocabularyManager = VocabularyManager(context)
    private val predictionPrefs: SharedPreferences = context.getSharedPreferences(
        PREDICTION_PREFS, Context.MODE_PRIVATE
    )
    
    // Word usage frequencies (how often each word is used)
    private val wordUsage = mutableMapOf<String, Int>()
    
    // Word pairs (bigrams) to learn common word combinations
    private val wordPairs = mutableMapOf<String, MutableMap<String, Int>>()
    
    // For observing predictions
    private val _predictions = MutableStateFlow<List<String>>(emptyList())
    val predictions: StateFlow<List<String>> = _predictions.asStateFlow()
    
    init {
        loadStats()
    }
    
    /**
     * Generate predictions based on the current sentence.
     * 
     * @param currentSentence The list of words in the current sentence
     * @return A list of predicted words (up to MAX_PREDICTIONS)
     */
    fun generatePredictions(currentSentence: List<String>): List<String> {
        if (currentSentence.isEmpty()) {
            // For empty sentences, suggest common starting words
            val predictions = getTopWords(MAX_PREDICTIONS)
            _predictions.value = predictions
            return predictions
        }
        
        // Get the last word in the sentence to find likely follow-up words
        val lastWord = currentSentence.last()
        
        // Combine different prediction strategies
        val predictions = combinePredictions(lastWord, currentSentence)
        
        _predictions.value = predictions
        return predictions
    }
    
    /**
     * Combine different prediction strategies to get the best predictions
     */
    private fun combinePredictions(lastWord: String, currentSentence: List<String>): List<String> {
        val result = mutableListOf<String>()
        
        // 1. First try to find direct follow-up words based on bigrams (pairs)
        val followUpWords = getFollowUpWords(lastWord)
        result.addAll(followUpWords.take(3))
        
        // 2. Add some generally common words that haven't been used in the sentence yet
        val commonWords = getTopWords(5)
            .filter { it !in currentSentence && it !in result }
        result.addAll(commonWords.take(MAX_PREDICTIONS - result.size))
        
        // 3. Add grammatically appropriate words based on sentence structure
        if (result.size < MAX_PREDICTIONS) {
            result.addAll(getGrammaticalSuggestions(currentSentence)
                .filter { it !in result }
                .take(MAX_PREDICTIONS - result.size))
        }
        
        return result.take(MAX_PREDICTIONS)
    }
    
    /**
     * Get words that commonly follow the given word, based on observed pairs
     */
    private fun getFollowUpWords(word: String): List<String> {
        val followUps = wordPairs[word.lowercase(Locale.getDefault())] ?: return emptyList()
        
        return followUps.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(MAX_PREDICTIONS)
    }
    
    /**
     * Get the most frequently used words
     */
    private fun getTopWords(count: Int): List<String> {
        return wordUsage.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(count)
    }
    
    /**
     * Get grammatically appropriate suggestions based on sentence structure
     */
    private fun getGrammaticalSuggestions(sentence: List<String>): List<String> {
        // This is a simple implementation that could be expanded
        // with more sophisticated language modeling
        
        val suggestions = mutableListOf<String>()
        
        // If sentence has just 1 word, suggest action words
        if (sentence.size == 1) {
            suggestions.addAll(getWordsInCategory("Actions").take(3))
        }
        
        // If sentence starts with a question word, suggest appropriate follow-ups
        if (sentence.firstOrNull()?.lowercase() in listOf("what", "where", "who", "when", "why", "how")) {
            suggestions.addAll(listOf("is", "are", "do", "does", "can", "will"))
        }
        
        return suggestions
    }
    
    /**
     * Get words from a specific category
     */
    private fun getWordsInCategory(category: String): List<String> {
        // Get all vocabulary including custom words
        val allVocabulary = vocabularyManager.getAllCustomWords()
        
        // Add common category words from built-in vocabulary
        // This is simplified - in a real implementation, you'd get this from your vocabulary system
        val commonWords = when (category.lowercase()) {
            "actions" -> listOf("want", "need", "like", "go", "eat", "drink", "play", "help")
            "people" -> listOf("I", "you", "mom", "dad", "teacher", "friend")
            "describing words" -> listOf("big", "small", "hot", "cold", "happy", "sad", "good")
            else -> emptyList()
        }
        
        return (allVocabulary.filter { it.category == category }.map { it.label } + commonWords)
            .distinct()
    }
    
    /**
     * Record word usage for a new sentence to improve future predictions
     */
    fun learnFromSentence(sentence: List<String>) {
        if (sentence.isEmpty()) return
        
        // Update individual word frequencies
        sentence.forEach { word ->
            val normalizedWord = word.lowercase(Locale.getDefault())
            wordUsage[normalizedWord] = (wordUsage[normalizedWord] ?: 0) + 1
        }
        
        // Update word pairs (bigrams)
        if (sentence.size > 1) {
            for (i in 0 until sentence.size - 1) {
                val firstWord = sentence[i].lowercase(Locale.getDefault())
                val secondWord = sentence[i + 1].lowercase(Locale.getDefault())
                
                // Create map for this word if it doesn't exist
                if (firstWord !in wordPairs) {
                    wordPairs[firstWord] = mutableMapOf()
                }
                
                // Update frequency of this pair
                val pairsForWord = wordPairs[firstWord]!!
                pairsForWord[secondWord] = (pairsForWord[secondWord] ?: 0) + 1
            }
        }
        
        // Save updated stats
        saveStats()
    }
    
    /**
     * Record usage of a single word to improve future predictions
     */
    fun learnWord(word: String) {
        val normalizedWord = word.lowercase(Locale.getDefault())
        wordUsage[normalizedWord] = (wordUsage[normalizedWord] ?: 0) + 1
        saveStats()
    }
    
    /**
     * Load usage statistics from SharedPreferences
     */
    private fun loadStats() {
        try {
            // Load word usage stats
            val usageJson = predictionPrefs.getString(WORD_USAGE_KEY, null)
            if (usageJson != null) {
                val jsonObject = JSONObject(usageJson)
                val keys = jsonObject.keys()
                
                while (keys.hasNext()) {
                    val key = keys.next()
                    wordUsage[key] = jsonObject.getInt(key)
                }
            }
            
            // Load word pairs stats
            val pairsJson = predictionPrefs.getString(WORD_PAIRS_KEY, null)
            if (pairsJson != null) {
                val jsonObject = JSONObject(pairsJson)
                val firstWordKeys = jsonObject.keys()
                
                while (firstWordKeys.hasNext()) {
                    val firstWord = firstWordKeys.next()
                    val followingWordsObj = jsonObject.getJSONObject(firstWord)
                    val followingWordKeys = followingWordsObj.keys()
                    
                    val followingWordsMap = mutableMapOf<String, Int>()
                    while (followingWordKeys.hasNext()) {
                        val followingWord = followingWordKeys.next()
                        followingWordsMap[followingWord] = followingWordsObj.getInt(followingWord)
                    }
                    
                    wordPairs[firstWord] = followingWordsMap
                }
            }
            
            Log.d(TAG, "Loaded prediction stats: ${wordUsage.size} words, ${wordPairs.size} word pairs")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading prediction stats", e)
        }
    }
    
    /**
     * Save usage statistics to SharedPreferences
     */
    private fun saveStats() {
        try {
            // Save word usage stats
            val usageJson = JSONObject()
            for ((word, count) in wordUsage) {
                usageJson.put(word, count)
            }
            
            // Save word pairs stats
            val pairsJson = JSONObject()
            for ((firstWord, followingWords) in wordPairs) {
                val followingWordsJson = JSONObject()
                for ((followingWord, count) in followingWords) {
                    followingWordsJson.put(followingWord, count)
                }
                pairsJson.put(firstWord, followingWordsJson)
            }
            
            predictionPrefs.edit()
                .putString(WORD_USAGE_KEY, usageJson.toString())
                .putString(WORD_PAIRS_KEY, pairsJson.toString())
                .apply()
            
            Log.d(TAG, "Saved prediction stats: ${wordUsage.size} words, ${wordPairs.size} word pairs")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving prediction stats", e)
        }
    }
    
    /**
     * Reset all prediction data
     */
    fun resetPredictionData() {
        wordUsage.clear()
        wordPairs.clear()
        predictionPrefs.edit().clear().apply()
        _predictions.value = emptyList()
    }
} 