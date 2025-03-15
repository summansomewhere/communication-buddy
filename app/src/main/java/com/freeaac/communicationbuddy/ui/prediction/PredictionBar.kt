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
package com.freeaac.communicationbuddy.ui.prediction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Displays a horizontal bar of predicted words that the user can tap to select.
 * 
 * @param predictions List of predicted words to display
 * @param onPredictionSelected Callback for when a prediction is selected
 * @param modifier Optional modifier for the component
 * @param enabled Whether the prediction bar is enabled
 */
@Composable
fun PredictionBar(
    predictions: List<String>,
    onPredictionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (predictions.isEmpty()) {
        return
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            predictions.forEach { prediction ->
                PredictionChip(
                    text = prediction,
                    onClick = { onPredictionSelected(prediction) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // If we have fewer than 5 predictions, add spacers to maintain layout
            repeat(5 - predictions.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * A single prediction chip displaying a suggested word
 */
@Composable
private fun PredictionChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ElevatedSuggestionChip(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.padding(horizontal = 4.dp),
        colors = SuggestionChipDefaults.elevatedSuggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(percent = 50),
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
} 