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