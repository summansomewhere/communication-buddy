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
package com.freeaac.communicationbuddy.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

/**
 * Utility to convert WebP images to PNG for compatibility purposes
 * (mainly for the README on GitHub which doesn't support WebP images)
 */
object WebpToPngConverter {
    
    /**
     * Converts the app icon from WebP to PNG and saves it to external storage
     * @param context The application context
     * @return The file path of the generated PNG or null if conversion failed
     */
    fun convertAppIconToPng(context: Context): String? {
        try {
            // Get the app icon as a bitmap
            val resources = context.resources
            val iconResourceId = resources.getIdentifier(
                "ic_launcher", 
                "mipmap", 
                context.packageName
            )
            
            if (iconResourceId == 0) {
                return null
            }
            
            val bitmap = BitmapFactory.decodeResource(resources, iconResourceId)
            
            // Create output directory in Pictures folder
            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "CommunicationBuddy"
            )
            
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            // Create output file
            val outputFile = File(outputDir, "app_icon.png")
            
            // Save as PNG
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            return outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
} 