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
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onPhotoTaken: (Uri) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Request camera permission
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )
    
    LaunchedEffect(Unit) {
        if (cameraPermissionState.status != PermissionStatus.Granted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Preview use case
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(800, 800),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .build() 
    }
    val cameraExecutor = remember { ContextCompat.getMainExecutor(context) }
    
    // Camera provider future
    var isCapturing by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Take Photo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show camera preview or permission request
            if (cameraPermissionState.status == PermissionStatus.Granted) {
                LaunchedEffect(previewView) {
                    val cameraProvider = context.getCameraProvider()
                    cameraProvider.unbindAll()
                    
                    val preview = Preview.Builder()
                        .build()
                        .apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                    
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        // Handle exceptions
                    }
                }
                
                // Camera preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Capture button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (!isCapturing) {
                                isCapturing = true
                                takePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    executor = cameraExecutor,
                                    onPhotoTaken = { uri ->
                                        isCapturing = false
                                        onPhotoTaken(uri)
                                    },
                                    onError = {
                                        isCapturing = false
                                        // Handle error
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Take Photo",
                            modifier = Modifier.size(50.dp),
                            tint = Color.White
                        )
                    }
                }
            } else {
                // Show permission request UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera permission is required to take photos.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() }
                    ) {
                        Text("Request Permission")
                    }
                }
            }
            
            // Loading indicator for capture
            if (isCapturing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onPhotoTaken: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    // Create a file to save the image
    val photoFile = createImageFile(context)
    
    // Create output options
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    // Take the photo
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // Create a URI from the file
                val savedUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                
                onPhotoTaken(savedUri)
            }
            
            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

private fun createImageFile(context: Context): File {
    // Create a timestamp for unique file name
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val imageFileName = "JPEG_${timeStamp}_"
    
    // Create file in app's private directory
    val storageDir = context.getExternalFilesDir(null)
    return File.createTempFile(
        imageFileName,
        ".webp",
        storageDir
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener({
                continuation.resume(future.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }
} 