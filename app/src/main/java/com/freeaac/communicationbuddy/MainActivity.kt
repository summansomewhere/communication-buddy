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
package com.freeaac.communicationbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freeaac.communicationbuddy.ui.AACMainScreen
import com.freeaac.communicationbuddy.ui.settings.SettingsScreen
import com.freeaac.communicationbuddy.ui.WordEditorScreen
import com.freeaac.communicationbuddy.ui.CameraScreen
import com.freeaac.communicationbuddy.ui.theme.CommunicationBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommunicationBuddyTheme {
                // Surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Navigation setup
                    val navController = rememberNavController()
                    
                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            AACMainScreen(
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToWordEditor = { wordId ->
                                    navController.navigate("word_editor/${wordId ?: "new"}")
                                }
                            )
                        }
                        composable(
                            "word_editor/{wordId}",
                            arguments = listOf(navArgument("wordId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val wordId = backStackEntry.arguments?.getString("wordId")
                            WordEditorScreen(
                                wordId = if (wordId == "new") null else wordId,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToCamera = {
                                    navController.navigate("camera")
                                },
                                onNavigateToWordEditor = { selectedWordId ->
                                    navController.navigate("word_editor/$selectedWordId")
                                }
                            )
                        }
                        composable("camera") {
                            CameraScreen(
                                onPhotoTaken = { uri ->
                                    navController.previousBackStackEntry?.savedStateHandle?.set("photo_uri", uri)
                                    navController.popBackStack()
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}