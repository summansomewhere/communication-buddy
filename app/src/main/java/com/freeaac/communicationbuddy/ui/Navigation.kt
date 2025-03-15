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
package com.freeaac.communicationbuddy.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freeaac.communicationbuddy.ui.settings.SettingsScreen

// Define route constants
object AppDestinations {
    const val MAIN_SCREEN = "main_screen"
    const val SETTINGS_SCREEN = "settings_screen"
    const val WORD_EDITOR_SCREEN = "word_editor_screen"
    const val CAMERA_SCREEN = "camera_screen"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppDestinations.MAIN_SCREEN
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Main AAC Screen
        composable(AppDestinations.MAIN_SCREEN) {
            AACMainScreen(
                onNavigateToSettings = {
                    navController.navigate(AppDestinations.SETTINGS_SCREEN)
                }
            )
        }
        
        // Settings Screen
        composable(AppDestinations.SETTINGS_SCREEN) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToWordEditor = { wordId ->
                    navController.navigate("${AppDestinations.WORD_EDITOR_SCREEN}/${wordId ?: "new"}")
                }
            )
        }
        
        // Word Editor Screen
        composable(
            route = "${AppDestinations.WORD_EDITOR_SCREEN}/{wordId}",
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getString("wordId")
            WordEditorScreen(
                wordId = wordId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = {
                    navController.navigate(AppDestinations.CAMERA_SCREEN)
                },
                onNavigateToWordEditor = { selectedWordId ->
                    navController.navigate("${AppDestinations.WORD_EDITOR_SCREEN}/$selectedWordId") {
                        popUpTo(AppDestinations.SETTINGS_SCREEN)
                    }
                }
            )
        }
        
        // Camera Screen
        composable(AppDestinations.CAMERA_SCREEN) {
            CameraScreen(
                onPhotoTaken = { uri ->
                    // Navigate back to editor with the photo URI
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("photo_uri", uri)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
} 