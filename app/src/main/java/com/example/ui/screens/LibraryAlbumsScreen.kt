package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firestore.Album
import com.example.data.local.AlbumCountSummary
import com.example.data.local.PhotoEntity
import com.example.ui.viewmodel.LibraryFilterState
import com.example.ui.viewmodel.ProcessingState

@Composable
fun LibraryAlbumsScreen(
    photos: List<PhotoEntity>,
    totalCount: Int,
    analyzedCount: Int,
    favoriteCount: Int,
    albums: List<Album>,
    albumCounts: List<AlbumCountSummary>,
    filterState: LibraryFilterState,
    processingState: ProcessingState,
    onSearchChange: (String) -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onToggleFavorites: () -> Unit,
    onClearFilters: () -> Unit,
    onImportFolderClick: () -> Unit,
    onPickPhotosClick: () -> Unit,
    onResumeAiClick: () -> Unit,
    onPauseAiClick: () -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    onFavoriteClick: (PhotoEntity) -> Unit
) {
    val unanalyzedRemaining = (totalCount - analyzedCount).coerceAtLeast(0)
    val coverByCategory = remember(albums, photos) {
        val map = mutableMapOf<String, String>()
        albums.forEach { if (it.coverPhotoUri.isNotBlank()) map[it.category.lowercase()] = it.coverPhotoUri }
        photos.forEach { p ->
            val key = p.albumCategory.lowercase()
            if (!map.containsKey(key)) map[key] = p.uriString
        }
        map
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 840.dp) 4 else if (maxWidth >= 600.dp) 3 else 2

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Editorial Header & Upload Controls
            item(span = { GridItemSpan(columns) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Albums & Archive",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (totalCount == 0) {
                                    "Import a folder of 3,000+ photos to begin"
                                } else {
                                    "$totalCount photos indexed • $analyzedCount AI categorized • $favoriteCount favorites"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onImportFolderClick,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onPickPhotosClick,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_photos_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Photos", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    // Live AI Progress Banner
                    val showProgressBanner = processingState !is ProcessingState.Idle || unanalyzedRemaining > 0
                    AnimatedVisibility(visible = showProgressBanner) {
                        AiProcessingBanner(
                            processingState = processingState,
                            totalCount = totalCount,
                            analyzedCount = analyzedCount,
                            unanalyzedRemaining = unanalyzedRemaining,
                            onPause = onPauseAiClick,
                            onResume = onResumeAiClick
                        )
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search albums, tags, family names, months...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search photos"
                            )
                        },
                        trailingIcon = {
                            if (filterState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_photos_input")
                    )

                    // Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = filterState.favoritesOnly,
                            onClick = onToggleFavorites,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (filterState.favoritesOnly) {
                                        Icons.Rounded.Favorite
                                    } else {
                                        Icons.Rounded.FavoriteBorder
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Favorites ($favoriteCount)") },
                            modifier = Modifier.testTag("filter_favorites_chip")
                        )

                        if (filterState.selectedMemberName != null) {
                            FilterChip(
                                selected = true,
                                onClick = onClearFilters,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Face,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("${filterState.selectedMemberName} ×") }
                            )
                        }

                        if (filterState.selectedAlbumCategory != null) {
                            FilterChip(
                                selected = true,
                                onClick = { onSelectAlbum(null) },
                                label = { Text("${filterState.selectedAlbumCategory} ×") }
                            )
                        }

                        if (filterState.selectedMonthLabel != null) {
                            FilterChip(
                                selected = true,
                                onClick = onClearFilters,
                                label = { Text("${filterState.selectedMonthLabel} ×") }
                            )
                        }
                    }
                }
            }

            // AI Searchable Albums Carousel
            if (albumCounts.isNotEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI Searchable Albums",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (filterState.selectedAlbumCategory != null) {
                                TextButton(onClick = { onSelectAlbum(null) }) {
                                    Text("Show All")
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albumCounts, key = { it.albumCategory }) { albumItem ->
                                val isSelected = filterState.selectedAlbumCategory.equals(
                                    albumItem.albumCategory,
                                    ignoreCase = true
                                )
                                val coverUri = coverByCategory[albumItem.albumCategory.lowercase()]
                                AlbumCoverCard(
                                    title = albumItem.albumCategory,
                                    count = albumItem.photoCount,
                                    coverUri = coverUri,
                                    selected = isSelected,
                                    onClick = {
                                        onSelectAlbum(
                                            if (isSelected) null else albumItem.albumCategory
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Empty State
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    EmptyCollectionCard(
                        hasAnyPhotos = totalCount > 0,
                        onImportFolderClick = onImportFolderClick,
                        onClearFilters = onClearFilters
                    )
                }
            } else {
                item(span = { GridItemSpan(columns) }) {
                    Text(
                        text = if (filterState.selectedAlbumCategory != null) {
                            "${filterState.selectedAlbumCategory} (${photos.size})"
                        } else {
                            "Collection (${photos.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(photos, key = { it.id }) { photo ->
                    PhotoGridTile(
                        photo = photo,
                        onClick = { onPhotoClick(photo) },
                        onFavoriteClick = { onFavoriteClick(photo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiProcessingBanner(
    processingState: ProcessingState,
    totalCount: Int,
    analyzedCount: Int,
    unanalyzedRemaining: Int,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    val label = when (processingState) {
                        is ProcessingState.Importing ->
                            "Indexing ${processingState.importedCount} photos..."
                        is ProcessingState.Categorizing ->
                            "AI Categorizing ($analyzedCount / $totalCount)..."
                        is ProcessingState.ScanningFaces ->
                            "Scanning family faces (${processingState.processedCount} / ${processingState.totalPending})..."
                        is ProcessingState.CuratingCollage ->
                            "Crafting ${processingState.monthLabel} collage..."
                        ProcessingState.Idle ->
                            "$unanalyzedRemaining photos queued for AI categorization"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (processingState is ProcessingState.Categorizing ||
                    processingState is ProcessingState.ScanningFaces
                ) {
                    TextButton(
                        onClick = onPause,
                        modifier = Modifier.testTag("ai_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                } else if (processingState is ProcessingState.Idle && unanalyzedRemaining > 0) {
                    TextButton(
                        onClick = onResume,
                        modifier = Modifier.testTag("ai_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Categorize")
                    }
                }
            }

            if (totalCount > 0 && processingState !is ProcessingState.Idle) {
                if (processingState is ProcessingState.Categorizing) {
                    LinearProgressIndicator(
                        progress = { (analyzedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCoverCard(
    title: String,
    count: Int,
    coverUri: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(154.dp)
            .height(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(20.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
    ) {
        if (!coverUri.isNullOrBlank()) {
            AsyncImage(
                model = Uri.parse(coverUri),
                contentDescription = "$title album cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC1E1915))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFFAF7F2),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$count photos",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFEBE2D6)
            )
        }
    }
}

@Composable
private fun PhotoGridTile(
    photo: PhotoEntity,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val uri = remember(photo.uriString) { Uri.parse(photo.uriString) }
    val memberNames = remember(photo.taggedMemberNamesCsv) { photo.taggedMemberNameList() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = photo.caption.ifBlank { photo.displayName },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xB81E1915))
                    )
                )
        )

        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(48.dp)
        ) {
            Surface(
                color = Color(0x881E1915),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (photo.isFavorite) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    contentDescription = "Toggle favorite",
                    tint = if (photo.isFavorite) Color(0xFFFF8F70) else Color.White,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (memberNames.isNotEmpty()) {
                Text(
                    text = memberNames.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFDEA8),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = photo.albumCategory,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFAF7F2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyCollectionCard(
    hasAnyPhotos: Boolean,
    onImportFolderClick: () -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_collection),
                contentDescription = "Empty Keepsake photo album illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Text(
                text = if (hasAnyPhotos) "No matching memories" else "Your archive awaits",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (hasAnyPhotos) {
                    "Try clearing your search or album filters to see all imported photos."
                } else {
                    "Select a camera folder with 3,000+ photos or pick favorites from your device. Gemini AI automatically sorts them into searchable albums."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasAnyPhotos) {
                OutlinedButton(onClick = onClearFilters) {
                    Text("Clear All Filters")
                }
            } else {
                Button(onClick = onImportFolderClick) {
                    Text("Import Photo Folder")
                }
            }
        }
    }
}
