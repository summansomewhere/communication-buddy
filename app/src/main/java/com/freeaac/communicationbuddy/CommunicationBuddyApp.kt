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

import android.app.Application

class CommunicationBuddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Suppress TTY errors
        suppressTTYErrors()
    }
    
    private fun suppressTTYErrors() {
        try {
            // This system property helps suppress TTY-related error messages from TextToSpeech
            System.setProperty("speechd.sock", "null")
        } catch (e: Exception) {
            // If setting the property fails, just continue - this is just to reduce log noise
        }
    }
} 