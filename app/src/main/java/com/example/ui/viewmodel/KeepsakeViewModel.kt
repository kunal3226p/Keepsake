package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.KeepsakeAiService
import com.example.data.firestore.Album
import com.example.data.firestore.Collage
import com.example.data.firestore.CollageLayoutStyle
import com.example.data.firestore.CollagePaletteTone
import com.example.data.firestore.FamilyMember
import com.example.data.firestore.KeepsakeRepository
import com.example.data.local.AlbumCountSummary
import com.example.data.local.MonthSummary
import com.example.data.local.PhotoDao
import com.example.data.local.PhotoEntity
import com.example.data.media.CollageExporter
import com.example.data.media.PhotoImportHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Importing(val importedCount: Int) : ProcessingState
    data class Categorizing(val processedCount: Int, val totalPending: Int) : ProcessingState
    data class ScanningFaces(val processedCount: Int, val totalPending: Int) : ProcessingState
    data class CuratingCollage(val monthLabel: String) : ProcessingState
}

data class LibraryFilterState(
    val searchQuery: String = "",
    val selectedAlbumCategory: String? = null,
    val selectedMemberId: String? = null,
    val selectedMemberName: String? = null,
    val selectedMonthKey: String? = null,
    val selectedMonthLabel: String? = null,
    val favoritesOnly: Boolean = false
)

class KeepsakeViewModel(
    private val userId: String,
    private val repository: KeepsakeRepository,
    private val photoDao: PhotoDao,
    private val aiService: KeepsakeAiService
) : ViewModel() {

    private val _filterState = MutableStateFlow(LibraryFilterState())
    val filterState: StateFlow<LibraryFilterState> = _filterState.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeAiJob: Job? = null

    val totalPhotoCount: StateFlow<Int> = photoDao.observeTotalCount(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val analyzedPhotoCount: StateFlow<Int> = photoDao.observeAnalyzedCount(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val favoritePhotoCount: StateFlow<Int> = photoDao.observeFavoriteCount(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val albumCounts: StateFlow<List<AlbumCountSummary>> = photoDao.observeAlbumCounts(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val monthSummaries: StateFlow<List<MonthSummary>> = photoDao.observeMonthSummaries(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val albums: StateFlow<List<Album>> = repository.observeAlbums(userId)
        .catch { err ->
            Log.e(TAG, "Error observing albums", err)
            _errorMessage.value = err.localizedMessage ?: "Failed to load cloud albums."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val familyMembers: StateFlow<List<FamilyMember>> = repository.observeFamilyMembers(userId)
        .catch { err ->
            Log.e(TAG, "Error observing family members", err)
            _errorMessage.value = err.localizedMessage ?: "Failed to load family faces."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val collages: StateFlow<List<Collage>> = repository.observeCollages(userId)
        .catch { err ->
            Log.e(TAG, "Error observing collages", err)
            _errorMessage.value = err.localizedMessage ?: "Failed to load collages."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @Suppress("OPT_IN_USAGE")
    val filteredPhotos: StateFlow<List<PhotoEntity>> = _filterState
        .flatMapLatest { filter ->
            val q = filter.searchQuery.trim()
            val baseFlow = if (q.isBlank()) {
                photoDao.observeAllPhotos(userId)
            } else {
                photoDao.searchPhotos(userId, q)
            }
            baseFlow.map { list ->
                list.filter { photo ->
                    val albumOk = filter.selectedAlbumCategory == null ||
                        photo.albumCategory.equals(filter.selectedAlbumCategory, ignoreCase = true)
                    val memberOk = filter.selectedMemberId == null ||
                        photo.taggedMemberIdList().contains(filter.selectedMemberId)
                    val monthOk = filter.selectedMonthKey == null ||
                        photo.monthKey == filter.selectedMonthKey
                    val favOk = !filter.favoritesOnly || photo.isFavorite
                    albumOk && memberOk && monthOk && favOk
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allPhotosForPicker: StateFlow<List<PhotoEntity>> = photoDao.observeAllPhotos(userId)
        .map { it.take(120) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun selectAlbumFilter(category: String?) {
        _filterState.value = _filterState.value.copy(selectedAlbumCategory = category)
    }

    fun selectMemberFilter(memberId: String?, memberName: String?) {
        _filterState.value = _filterState.value.copy(
            selectedMemberId = memberId,
            selectedMemberName = memberName
        )
    }

    fun selectMonthFilter(monthKey: String?, monthLabel: String?) {
        _filterState.value = _filterState.value.copy(
            selectedMonthKey = monthKey,
            selectedMonthLabel = monthLabel
        )
    }

    fun toggleFavoritesOnly() {
        _filterState.value = _filterState.value.copy(
            favoritesOnly = !_filterState.value.favoritesOnly
        )
    }

    fun clearAllFilters() {
        _filterState.value = LibraryFilterState()
    }

    fun dismissStatus() {
        _statusMessage.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun pauseActiveAiTask() {
        activeAiJob?.cancel()
        activeAiJob = null
        _processingState.value = ProcessingState.Idle
        _statusMessage.value = "AI processing paused. Tap Resume anytime."
    }

    fun importFolder(appContext: Context, treeUri: Uri) {
        viewModelScope.launch {
            _errorMessage.value = null
            var runningTotal = 0
            _processingState.value = ProcessingState.Importing(0)
            try {
                val found = PhotoImportHelper.scanFolderTreeForPhotos(
                    context = appContext.applicationContext,
                    treeUri = treeUri,
                    userId = userId
                ) { batch ->
                    photoDao.insertBatchIgnoreExisting(batch)
                    runningTotal += batch.size
                    _processingState.value = ProcessingState.Importing(runningTotal)
                }
                _processingState.value = ProcessingState.Idle
                if (found > 0) {
                    _statusMessage.value = "Imported $found photos. Starting AI album categorization..."
                    startAiCategorization(appContext)
                } else {
                    _statusMessage.value = "No image files were found in the selected folder."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Folder import failed", e)
                _processingState.value = ProcessingState.Idle
                _errorMessage.value = e.localizedMessage ?: "Failed to import folder."
            }
        }
    }

    fun importSelectedPhotos(appContext: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _errorMessage.value = null
            _processingState.value = ProcessingState.Importing(uris.size)
            try {
                val entities = PhotoImportHelper.buildEntitiesFromUris(
                    context = appContext.applicationContext,
                    uris = uris,
                    userId = userId
                )
                entities.chunked(100).forEach { chunk ->
                    photoDao.insertBatchIgnoreExisting(chunk)
                }
                _processingState.value = ProcessingState.Idle
                _statusMessage.value = "Added ${entities.size} photos. Organizing with AI..."
                startAiCategorization(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Photo picker import failed", e)
                _processingState.value = ProcessingState.Idle
                _errorMessage.value = e.localizedMessage ?: "Failed to import selected photos."
            }
        }
    }

    fun startAiCategorization(appContext: Context) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _errorMessage.value = null
            val context = appContext.applicationContext
            try {
                var processed = 0
                val initialPending = (totalPhotoCount.value - analyzedPhotoCount.value).coerceAtLeast(1)
                while (true) {
                    val batch = photoDao.getUnanalyzedBatch(userId, 8)
                    if (batch.isEmpty()) break

                    _processingState.value = ProcessingState.Categorizing(
                        processedCount = processed,
                        totalPending = maxOf(initialPending, processed + batch.size)
                    )

                    val bitmaps = batch.mapNotNull { entity ->
                        val bmp = PhotoImportHelper.loadScaledBitmap(context, Uri.parse(entity.uriString), 384)
                        if (bmp != null) entity.id to bmp else null
                    }

                    if (bitmaps.isEmpty()) {
                        // Mark unreadable URIs analyzed so queue does not stall
                        batch.forEach { entity ->
                            photoDao.updateAiClassification(
                                userId = userId,
                                photoId = entity.id,
                                albumCategory = "Everyday Moments",
                                tagsCsv = "",
                                caption = "",
                                suggestedFavorite = false
                            )
                        }
                        processed += batch.size
                        continue
                    }

                    val existingCategories = photoDao.getExistingCategories(userId)
                    val results = try {
                        aiService.categorizePhotoBatch(bitmaps, existingCategories)
                    } finally {
                        bitmaps.forEach { it.second.recycle() }
                    }

                    val resultByKey = results.associateBy { it.photoKey }
                    val touchedCategories = mutableSetOf<String>()
                    batch.forEach { entity ->
                        val res = resultByKey[entity.id]
                        val category = res?.albumCategory ?: "Everyday Moments"
                        val tags = res?.tags ?: emptyList()
                        val caption = res?.caption ?: ""
                        val highlight = res?.isHighlight ?: false
                        touchedCategories.add(category)

                        photoDao.updateAiClassification(
                            userId = userId,
                            photoId = entity.id,
                            albumCategory = category,
                            tagsCsv = tags.joinToString(","),
                            caption = caption,
                            suggestedFavorite = highlight
                        )

                        if (highlight || entity.isFavorite) {
                            repository.upsertMemoryRecord(
                                photoKey = entity.id,
                                uriString = entity.uriString,
                                displayName = entity.displayName,
                                monthKey = entity.monthKey,
                                albumCategory = category,
                                caption = caption,
                                tags = tags,
                                taggedMemberIds = entity.taggedMemberIdList(),
                                isFavorite = entity.isFavorite || highlight,
                                takenAtMillis = entity.takenAtMillis
                            )
                        }
                    }

                    syncAlbumSummariesToFirestore(touchedCategories, batch)
                    processed += batch.size
                }

                _processingState.value = ProcessingState.Idle
                if (processed > 0) {
                    _statusMessage.value = "AI categorized $processed photos into searchable albums."
                }
            } catch (e: CancellationException) {
                _processingState.value = ProcessingState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI categorization failed", e)
                _processingState.value = ProcessingState.Idle
                _errorMessage.value = e.localizedMessage ?: "AI categorization failed."
            }
        }
    }

    private suspend fun syncAlbumSummariesToFirestore(
        categories: Set<String>,
        sampleBatch: List<PhotoEntity>
    ) {
        val existingMap = albums.value.associateBy { it.category.lowercase() }
        categories.forEach { category ->
            val cover = sampleBatch.firstOrNull {
                it.albumCategory.equals(category, ignoreCase = true)
            }?.uriString ?: existingMap[category.lowercase()]?.coverPhotoUri.orEmpty()
            val exactCount = photoDao.countPhotosInCategory(userId, category).toLong().coerceAtLeast(1L)
            val albumId = "alb_${KeepsakeRepository.sanitizeDocId(category.lowercase())}"
            repository.upsertAlbum(
                albumId = albumId,
                title = category,
                category = category,
                summary = "AI-organized $category memories",
                tags = category.lowercase().split(" ", "&").filter { it.length > 2 },
                coverPhotoUri = cover,
                photoCount = exactCount
            )
        }
    }

    fun addFamilyMemberFromFaceCrop(
        appContext: Context,
        name: String,
        relationship: String,
        referencePhotoUri: Uri,
        centerXFraction: Float,
        centerYFraction: Float,
        cropSizeFraction: Float
    ) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val context = appContext.applicationContext
                PhotoImportHelper.tryTakePersistableReadPermission(context, referencePhotoUri)
                val fullBmp = PhotoImportHelper.loadScaledBitmap(context, referencePhotoUri, 960)
                    ?: throw IllegalStateException("Could not decode the selected photo.")
                val base64Crop = try {
                    PhotoImportHelper.cropSquareFaceToBase64(
                        fullBmp,
                        centerXFraction,
                        centerYFraction,
                        cropSizeFraction
                    )
                } finally {
                    fullBmp.recycle()
                }
                val memberId = "mem_${KeepsakeRepository.sanitizeDocId(name.lowercase())}_${System.currentTimeMillis() % 10000}"
                repository.addFamilyMember(
                    memberId = memberId,
                    name = name,
                    relationship = relationship,
                    faceCropBase64 = base64Crop,
                    referencePhotoUri = referencePhotoUri.toString(),
                    taggedCount = 0L
                )
                _statusMessage.value = "Saved $name's face profile. Scanning collection..."
                scanCollectionForFamilyFaces(appContext, rescanAll = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add family member", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to save family face."
            }
        }
    }

    fun scanCollectionForFamilyFaces(appContext: Context, rescanAll: Boolean = false) {
        val currentMembers = familyMembers.value
        if (currentMembers.isEmpty()) {
            _errorMessage.value = "Add at least one family member face before scanning."
            return
        }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _errorMessage.value = null
            val context = appContext.applicationContext
            val refFaces = currentMembers.mapNotNull { member ->
                val bmp = PhotoImportHelper.decodeBase64Bitmap(member.faceCropBase64)
                if (bmp != null) Triple(member.id, member.name, bmp) else null
            }
            if (refFaces.isEmpty()) {
                _errorMessage.value = "No valid reference face thumbnails available."
                return@launch
            }
            val memberNameById = currentMembers.associate { it.id to it.name }

            try {
                if (rescanAll) {
                    photoDao.resetFaceScanFlags(userId)
                }
                var scanned = 0
                val totalTarget = totalPhotoCount.value.coerceAtLeast(1)

                while (true) {
                    val batch = photoDao.getUnscannedFacesBatch(userId, 6)
                    if (batch.isEmpty()) break

                    _processingState.value = ProcessingState.ScanningFaces(
                        processedCount = scanned,
                        totalPending = totalTarget
                    )

                    val targetBitmaps = batch.mapNotNull { entity ->
                        val bmp = PhotoImportHelper.loadScaledBitmap(context, Uri.parse(entity.uriString), 384)
                        if (bmp != null) entity.id to bmp else null
                    }

                    if (targetBitmaps.isEmpty()) {
                        batch.forEach { entity ->
                            photoDao.updateFaceTags(userId, entity.id, "", "")
                        }
                        scanned += batch.size
                        continue
                    }

                    val matches = try {
                        aiService.identifyFamilyFacesInBatch(refFaces, targetBitmaps)
                    } finally {
                        targetBitmaps.forEach { it.second.recycle() }
                    }

                    val matchMap = matches.associateBy { it.photoKey }
                    batch.forEach { entity ->
                        val matchedIds = matchMap[entity.id]?.matchedMemberIds ?: emptyList()
                        val mergedIds = (entity.taggedMemberIdList() + matchedIds).distinct()
                        val mergedNames = mergedIds.mapNotNull { memberNameById[it] }
                        photoDao.updateFaceTags(
                            userId = userId,
                            photoId = entity.id,
                            memberIdsCsv = mergedIds.joinToString(","),
                            memberNamesCsv = mergedNames.joinToString(",")
                        )
                        if (mergedIds.isNotEmpty()) {
                            repository.upsertMemoryRecord(
                                photoKey = entity.id,
                                uriString = entity.uriString,
                                displayName = entity.displayName,
                                monthKey = entity.monthKey,
                                albumCategory = entity.albumCategory,
                                caption = entity.caption,
                                tags = entity.tagList(),
                                taggedMemberIds = mergedIds,
                                isFavorite = entity.isFavorite,
                                takenAtMillis = entity.takenAtMillis
                            )
                        }
                    }
                    scanned += batch.size
                }

                // Refresh tagged counts on Firestore family profiles
                currentMembers.forEach { member ->
                    val count = photoDao.countPhotosWithMember(userId, member.id).toLong()
                    if (member.createdAt != null) {
                        repository.updateFamilyMemberTaggedCount(member, count)
                    }
                }

                _processingState.value = ProcessingState.Idle
                _statusMessage.value = "Family face scan complete across $scanned photos."
            } catch (e: CancellationException) {
                _processingState.value = ProcessingState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Family face scan failed", e)
                _processingState.value = ProcessingState.Idle
                _errorMessage.value = e.localizedMessage ?: "Family face scan failed."
            } finally {
                refFaces.forEach { it.third.recycle() }
            }
        }
    }

    fun deleteFamilyMember(memberId: String) {
        viewModelScope.launch {
            try {
                repository.deleteFamilyMember(memberId)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to remove family member."
            }
        }
    }

    fun toggleFavorite(photo: PhotoEntity) {
        viewModelScope.launch {
            val nextFav = !photo.isFavorite
            try {
                photoDao.setFavorite(userId, photo.id, nextFav)
                repository.upsertMemoryRecord(
                    photoKey = photo.id,
                    uriString = photo.uriString,
                    displayName = photo.displayName,
                    monthKey = photo.monthKey,
                    albumCategory = photo.albumCategory,
                    caption = photo.caption,
                    tags = photo.tagList(),
                    taggedMemberIds = photo.taggedMemberIdList(),
                    isFavorite = nextFav,
                    takenAtMillis = photo.takenAtMillis
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update favorite."
            }
        }
    }

    fun updatePhotoAlbum(photo: PhotoEntity, newCategory: String) {
        viewModelScope.launch {
            val cleanCategory = newCategory.trim().ifBlank { return@launch }
            try {
                photoDao.setAlbumCategory(userId, photo.id, cleanCategory)
                val albumId = "alb_${KeepsakeRepository.sanitizeDocId(cleanCategory.lowercase())}"
                repository.upsertAlbum(
                    albumId = albumId,
                    title = cleanCategory,
                    category = cleanCategory,
                    summary = "Curated $cleanCategory photos",
                    tags = cleanCategory.lowercase().split(" ", "&").filter { it.length > 2 },
                    coverPhotoUri = photo.uriString,
                    photoCount = 1L
                )
                repository.upsertMemoryRecord(
                    photoKey = photo.id,
                    uriString = photo.uriString,
                    displayName = photo.displayName,
                    monthKey = photo.monthKey,
                    albumCategory = cleanCategory,
                    caption = photo.caption,
                    tags = photo.tagList(),
                    taggedMemberIds = photo.taggedMemberIdList(),
                    isFavorite = photo.isFavorite,
                    takenAtMillis = photo.takenAtMillis
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update album."
            }
        }
    }

    fun craftMonthlyCollage(appContext: Context, monthKey: String, monthLabel: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            _processingState.value = ProcessingState.CuratingCollage(monthLabel)
            val context = appContext.applicationContext
            try {
                val favorites = photoDao.getFavoritesForMonth(userId, monthKey)
                val pool = if (favorites.isNotEmpty()) {
                    favorites.take(8)
                } else {
                    photoDao.getPhotosForMonth(userId, monthKey, 8)
                }
                if (pool.isEmpty()) {
                    _processingState.value = ProcessingState.Idle
                    _errorMessage.value = "No photos available in $monthLabel yet."
                    return@launch
                }

                val candidates = pool.mapNotNull { entity ->
                    val bmp = PhotoImportHelper.loadScaledBitmap(context, Uri.parse(entity.uriString), 384)
                    if (bmp != null) {
                        val note = buildString {
                            append(entity.albumCategory)
                            if (entity.taggedMemberNamesCsv.isNotBlank()) {
                                append(" | People: ${entity.taggedMemberNamesCsv}")
                            }
                            if (entity.caption.isNotBlank()) {
                                append(" | ${entity.caption}")
                            }
                        }
                        Triple(entity.id, note, bmp)
                    } else null
                }

                if (candidates.isEmpty()) {
                    _processingState.value = ProcessingState.Idle
                    _errorMessage.value = "Could not load thumbnails for $monthLabel."
                    return@launch
                }

                val curation = try {
                    aiService.curateMonthlyCollage(monthLabel, candidates)
                } finally {
                    candidates.forEach { it.third.recycle() }
                }

                val uriByKey = pool.associate { it.id to it.uriString }
                val orderedUris = curation.orderedPhotoKeys.mapNotNull { uriByKey[it] }
                    .ifEmpty { pool.take(6).map { it.uriString } }

                val collageId = "col_${KeepsakeRepository.sanitizeDocId(monthKey)}"
                repository.upsertCollage(
                    collageId = collageId,
                    monthKey = monthKey,
                    monthLabel = monthLabel,
                    title = curation.title,
                    storyNote = curation.storyNote,
                    layoutStyle = curation.layoutStyle,
                    photoUris = orderedUris,
                    paletteTone = curation.paletteTone
                )

                _processingState.value = ProcessingState.Idle
                _statusMessage.value = "Crafted \"$monthLabel\" collage: ${curation.title}"
            } catch (e: CancellationException) {
                _processingState.value = ProcessingState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Collage crafting failed", e)
                _processingState.value = ProcessingState.Idle
                _errorMessage.value = e.localizedMessage ?: "Failed to craft monthly collage."
            }
        }
    }

    fun updateCollageAppearance(
        collage: Collage,
        title: String,
        layoutStyle: CollageLayoutStyle,
        paletteTone: CollagePaletteTone
    ) {
        viewModelScope.launch {
            try {
                repository.upsertCollage(
                    collageId = collage.id.ifBlank { "col_${KeepsakeRepository.sanitizeDocId(collage.monthKey)}" },
                    monthKey = collage.monthKey,
                    monthLabel = collage.monthLabel,
                    title = title,
                    storyNote = collage.storyNote,
                    layoutStyle = layoutStyle,
                    photoUris = collage.photoUris,
                    paletteTone = paletteTone
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update collage style."
            }
        }
    }

    fun saveCollageToGallery(appContext: Context, collage: Collage) {
        viewModelScope.launch {
            val ok = CollageExporter.saveCollageToGallery(appContext.applicationContext, collage)
            if (ok) {
                _statusMessage.value = "Saved ${collage.monthLabel} collage to Pictures/Keepsake."
            } else {
                _errorMessage.value = "Could not save collage to gallery."
            }
        }
    }

    fun shareCollage(activityContext: Context, collage: Collage) {
        viewModelScope.launch {
            try {
                CollageExporter.shareCollage(activityContext, collage)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to share collage."
            }
        }
    }

    private companion object {
        const val TAG = "KeepsakeVM"
    }
}
