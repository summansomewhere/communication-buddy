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
package com.freeaac.communicationbuddy.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Enhanced dark color scheme with deeper contrasts and more vibrant accents
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9C70FF), // Brighter purple for dark mode
    secondary = Color(0xFFB0A8C0),
    tertiary = Color(0xFFFFB0C6),
    background = Color(0xFF1A1A1F), // Darker background
    surface = Color(0xFF1A1A1F),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF2EDF7),
    onSurface = Color(0xFFF2EDF7),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    surfaceVariant = Color(0xFF33303A), // Darker surface variant
    onSurfaceVariant = Color(0xFFDCD4E0),
    error = Color(0xFFFF5C5C), // Brighter error color for dark mode
    onError = Color.Black
)

// Enhanced light color scheme with better contrast and accessibility
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFAF8FF), // Slightly tinted background
    surface = Color(0xFFFAF8FF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1A1A1F),
    onSurface = Color(0xFF1A1A1F),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005E),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

// High contrast light color scheme - optimized for visibility while maintaining aesthetics
private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF0000C0),          // Deep blue 
    secondary = Color(0xFF4B0082),        // Indigo
    tertiary = Color(0xFF880E4F),         // Deep pink
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    primaryContainer = Color(0xFFD0BCFF), // Light purple with higher saturation
    onPrimaryContainer = Color(0xFF000080), // Navy blue
    surfaceVariant = Color(0xFFE0E0E0),   // Light gray
    onSurfaceVariant = Color(0xFF000000),
    error = Color(0xFFB00020),            // Bright red
    onError = Color.White
)

// High contrast dark color scheme - maximum visibility with reduced eye strain
private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD700),          // Gold
    secondary = Color(0xFFB0C4DE),        // Light steel blue
    tertiary = Color(0xFFFFB6C1),         // Light pink
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4F378B), // Deep purple at full opacity
    onPrimaryContainer = Color(0xFFFFD700), // Gold
    surfaceVariant = Color(0xFF333333),   // Dark gray
    onSurfaceVariant = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B6B),            // Bright red
    onError = Color(0xFF000000)
)

/**
 * Preference listener that updates state when dark mode setting changes
 */
@Composable
fun rememberDarkModeState(context: Context): State<Boolean> {
    val darkModeState = remember { mutableStateOf(isDarkModeEnabled(context)) }
    
    DisposableEffect(context) {
        val sharedPrefs = context.getSharedPreferences("communication_buddy_settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dark_mode") {
                darkModeState.value = isDarkModeEnabled(context)
            }
        }
        
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    return darkModeState
}

/**
 * Preference listener that updates state when high contrast mode setting changes
 */
@Composable
fun rememberHighContrastModeState(context: Context): State<Boolean> {
    val highContrastState = remember { mutableStateOf(isHighContrastModeEnabled(context)) }
    
    DisposableEffect(context) {
        val sharedPrefs = context.getSharedPreferences("communication_buddy_settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "high_contrast_mode") {
                highContrastState.value = isHighContrastModeEnabled(context)
            }
        }
        
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    return highContrastState
}

/**
 * Gets the current dark mode setting from SharedPreferences
 */
private fun isDarkModeEnabled(context: Context): Boolean {
    val sharedPrefs = context.getSharedPreferences("communication_buddy_settings", Context.MODE_PRIVATE)
    return sharedPrefs.getBoolean("dark_mode", false)
}

/**
 * Gets the current high contrast mode setting from SharedPreferences
 */
private fun isHighContrastModeEnabled(context: Context): Boolean {
    val sharedPrefs = context.getSharedPreferences("communication_buddy_settings", Context.MODE_PRIVATE)
    return sharedPrefs.getBoolean("high_contrast_mode", false)
}

@Composable
fun CommunicationBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    forceDarkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkModeEnabled = rememberDarkModeState(context).value
    val highContrastModeEnabled = rememberHighContrastModeState(context).value
    val effectiveDarkMode = forceDarkTheme ?: darkModeEnabled ?: darkTheme
    
    val colorScheme = when {
        // High contrast mode overrides dynamic colors
        highContrastModeEnabled -> {
            if (effectiveDarkMode) HighContrastDarkColorScheme else HighContrastLightColorScheme
        }
        // Otherwise, use dynamic colors if available
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDarkMode -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // Update system bars to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(effectiveDarkMode, highContrastModeEnabled) {
            val window = (view.context as Activity).window
            
            // Enable edge-to-edge experience
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Update the system bars' appearance
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !effectiveDarkMode
            insetsController.isAppearanceLightNavigationBars = !effectiveDarkMode
            
            // Set system bars behavior without using deprecated properties
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            
            onDispose {}
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}