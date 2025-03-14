package com.freeaac.communicationbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.freeaac.communicationbuddy.ui.AppNavigation
import com.freeaac.communicationbuddy.ui.theme.CommunicationBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommunicationBuddyTheme {
                AppNavigation()
            }
        }
    }
}