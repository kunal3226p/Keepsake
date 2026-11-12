package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.firestore.Collage
import com.example.data.firestore.CollageLayoutStyle
import com.example.data.firestore.CollagePaletteTone
import com.example.data.local.MonthSummary
import com.example.ui.viewmodel.ProcessingState

@Composable
fun MonthlyCollagesScreen(
    monthSummaries: List<MonthSummary>,
    collages: List<Collage>,
    processingState: ProcessingState,
    onCraftCollage: (monthKey: String, monthLabel: String) -> Unit,
    onUpdateCollage: (Collage, String, CollageLayoutStyle, CollagePaletteTone) -> Unit,
    onSaveCollageToGallery: (Collage) -> Unit,
    onShareCollage: (Collage) -> Unit,
    onBrowseMonthPhotos: (monthKey: String, monthLabel: String) -> Unit
) {
    val collageByMonth = remember(collages) { collages.associateBy { it.monthKey } }
    var editingCollage by remember { mutableStateOf<Collage?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Monthly Collages",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Each month's favorite memories arranged by AI into warm, shareable keepsake posters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (monthSummaries.isEmpty() && collages.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoAlbum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No monthly memories yet",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Import photos in the Albums tab and heart your favorites—Keepsake groups them by month so you can craft editorial collages.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(monthSummaries, key = { it.monthKey }) { summary ->
                val savedCollage = collageByMonth[summary.monthKey]
                MonthlyCollageSection(
                    summary = summary,
                    collage = savedCollage,
                    isGenerating = processingState is ProcessingState.CuratingCollage &&
                        processingState.monthLabel == summary.monthLabel,
                    onCraft = { onCraftCollage(summary.monthKey, summary.monthLabel) },
                    onOpenStudio = { if (savedCollage != null) editingCollage = savedCollage },
                    onBrowseMonth = { onBrowseMonthPhotos(summary.monthKey, summary.monthLabel) }
                )
            }
        }
    }

    val activeCollage = editingCollage
    if (activeCollage != null) {
        CollageStudioDialog(
            collage = activeCollage,
            onDismiss = { editingCollage = null },
            onSaveChanges = { title, layout, tone ->
                onUpdateCollage(activeCollage, title, layout, tone)
                editingCollage = activeCollage.copy(
                    title = title,
                    layoutStyle = layout.name,
                    paletteTone = tone.name
                )
            },
            onSaveToGallery = { onSaveCollageToGallery(activeCollage) },
            onShare = { onShareCollage(activeCollage) }
        )
    }
}

@Composable
private fun MonthlyCollageSection(
    summary: MonthSummary,
    collage: Collage?,
    isGenerating: Boolean,
    onCraft: () -> Unit,
    onOpenStudio: () -> Unit,
    onBrowseMonth: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.monthLabel,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${summary.favoriteCount} favorites • ${summary.totalCount} photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onBrowseMonth)
                    )
                }
                Button(
                    onClick = onCraft,
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("craft_collage_button_${summary.monthKey}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isGenerating -> "Crafting..."
                            collage == null -> "Craft Collage"
                            else -> "Regenerate"
                        }
                    )
                }
            }

            if (collage != null) {
                CollagePosterCard(
                    collage = collage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStudio)
                )
                Text(
                    text = "Tap collage to customize layout, save, or share",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CollagePosterCard(
    collage: Collage,
    modifier: Modifier = Modifier
) {
    val tone = CollagePaletteTone.fromRaw(collage.paletteTone)
    val bgColor = when (tone) {
        CollagePaletteTone.TERRACOTTA -> Color(0xFFFAF7F2)
        CollagePaletteTone.SAGE -> Color(0xFFF1F6F0)
        CollagePaletteTone.ESPRESSO -> Color(0xFF1D1815)
        CollagePaletteTone.GOLDEN -> Color(0xFFFDF7EA)
    }
    val inkColor = if (tone == CollagePaletteTone.ESPRESSO) Color(0xFFF3ECE2) else Color(0xFF1E1915)
    val accentColor = when (tone) {
        CollagePaletteTone.TERRACOTTA -> Color(0xFFB34C21)
        CollagePaletteTone.SAGE -> Color(0xFF47664F)
        CollagePaletteTone.ESPRESSO -> Color(0xFFFFB59A)
        CollagePaletteTone.GOLDEN -> Color(0xFF8C5F18)
    }
    val layout = CollageLayoutStyle.fromRaw(collage.layoutStyle)
    val uris = remember(collage.photoUris) { collage.photoUris.map { Uri.parse(it) } }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = collage.monthLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
            Text(
                text = collage.title,
                style = MaterialTheme.typography.titleLarge,
                color = inkColor
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.95f)
            ) {
                CollagePhotoGrid(layout = layout, uris = uris)
            }

            if (collage.storyNote.isNotBlank()) {
                Text(
                    text = collage.storyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = inkColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun CollagePhotoGrid(
    layout: CollageLayoutStyle,
    uris: List<Uri>
) {
    if (uris.isEmpty()) return
    val gap = 6.dp
    when {
        uris.size == 1 -> {
            CollageImageCell(uri = uris[0], modifier = Modifier.fillMaxSize())
        }
        layout == CollageLayoutStyle.EDITORIAL_TRIO -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                CollageImageCell(
                    uri = uris[0],
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.35f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    CollageImageCell(
                        uri = uris.getOrElse(1) { uris[0] },
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    )
                    if (uris.size >= 3) {
                        CollageImageCell(
                            uri = uris[2],
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                        )
                    }
                }
            }
        }
        layout == CollageLayoutStyle.GRID_2X2 -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    CollageImageCell(uri = uris[0], modifier = Modifier.fillMaxHeight().weight(1f))
                    CollageImageCell(
                        uri = uris.getOrElse(1) { uris[0] },
                        modifier = Modifier.fillMaxHeight().weight(1f)
                    )
                }
                if (uris.size >= 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        CollageImageCell(uri = uris[2], modifier = Modifier.fillMaxHeight().weight(1f))
                        CollageImageCell(
                            uri = uris.getOrElse(3) { uris[0] },
                            modifier = Modifier.fillMaxHeight().weight(1f)
                        )
                    }
                }
            }
        }
        layout == CollageLayoutStyle.HERO_STRIP -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                CollageImageCell(
                    uri = uris[0],
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.7f)
                )
                val strip = uris.drop(1).take(3)
                if (strip.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        strip.forEach { u ->
                            CollageImageCell(uri = u, modifier = Modifier.fillMaxHeight().weight(1f))
                        }
                    }
                }
            }
        }
        else -> {
            val rows = uris.take(6).chunked(2)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                rows.forEach { rowUris ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rowUris.forEach { u ->
                            CollageImageCell(uri = u, modifier = Modifier.fillMaxHeight().weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageImageCell(uri: Uri, modifier: Modifier = Modifier) {
    AsyncImage(
        model = uri,
        contentDescription = "Collage memory photo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollageStudioDialog(
    collage: Collage,
    onDismiss: () -> Unit,
    onSaveChanges: (String, CollageLayoutStyle, CollagePaletteTone) -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit
) {
    var title by remember(collage.id) { mutableStateOf(collage.title) }
    var selectedLayout by remember(collage.id) {
        mutableStateOf(CollageLayoutStyle.fromRaw(collage.layoutStyle))
    }
    var selectedTone by remember(collage.id) {
        mutableStateOf(CollagePaletteTone.fromRaw(collage.paletteTone))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 18.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Collage Studio",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close studio"
                        )
                    }
                }

                CollagePosterCard(
                    collage = collage.copy(
                        title = title,
                        layoutStyle = selectedLayout.name,
                        paletteTone = selectedTone.name
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onSaveChanges(it, selectedLayout, selectedTone)
                    },
                    label = { Text("Collage Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Layout Style", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CollageLayoutStyle.entries.forEach { style ->
                        FilterChip(
                            selected = selectedLayout == style,
                            onClick = {
                                selectedLayout = style
                                onSaveChanges(title, style, selectedTone)
                            },
                            label = { Text(style.displayName) }
                        )
                    }
                }

                Text("Paper Tone", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CollagePaletteTone.entries.forEach { tone ->
                        FilterChip(
                            selected = selectedTone == tone,
                            onClick = {
                                selectedTone = tone
                                onSaveChanges(title, selectedLayout, tone)
                            },
                            label = { Text(tone.displayName) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveToGallery,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Image")
                    }
                    Button(
                        onClick = onShare,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}
