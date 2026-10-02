package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AtomicCyan
import com.example.ui.theme.DarkBorder

@Composable
fun QuickPrompts(
    onPromptClick: (String) -> Unit,
    cachedPrompts: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val defaultPrompts = listOf(
        "Open YouTube",
        "Set a 5 minute timer",
        "Note: Buy groceries after work",
        "What is the date and time?",
        "Turn volume up",
        "Open Camera",
        "Search latest Mars rover news",
        "Open Google Maps for coffee"
    )

    // Combine frequently used cached queries first, then fallback to defaults
    val displayedPrompts = (cachedPrompts + defaultPrompts).distinct().take(12)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(displayedPrompts) { prompt ->
            val isCached = cachedPrompts.contains(prompt)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isCached) {
                    AtomicCyan.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isCached) AtomicCyan.copy(alpha = 0.5f) else DarkBorder
                ),
                modifier = Modifier
                    .testTag("quick_prompt_chip")
                    .clickable { onPromptClick(prompt) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    if (isCached) {
                        Icon(
                            imageVector = Icons.Default.OfflineBolt,
                            contentDescription = "Cached offline response available",
                            tint = AtomicCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = prompt,
                        fontSize = 13.sp,
                        color = if (isCached) AtomicCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
