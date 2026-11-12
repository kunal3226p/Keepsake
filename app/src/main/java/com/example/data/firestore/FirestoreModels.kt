package com.example.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CollageLayoutStyle(val displayName: String) {
    EDITORIAL_TRIO("Editorial Trio"),
    GRID_2X2("2x2 Classic Grid"),
    HERO_STRIP("Hero & Strip"),
    MOSAIC_6("Six-Frame Mosaic");

    companion object {
        fun fromRaw(raw: String?): CollageLayoutStyle =
            entries.firstOrNull { it.name == raw } ?: EDITORIAL_TRIO
    }
}

enum class CollagePaletteTone(val displayName: String) {
    TERRACOTTA("Warm Linen"),
    SAGE("Botanical Sage"),
    ESPRESSO("Gallery Dark"),
    GOLDEN("Sunlit Gold");

    companion object {
        fun fromRaw(raw: String?): CollagePaletteTone =
            entries.firstOrNull { it.name == raw } ?: TERRACOTTA
    }
}

@IgnoreExtraProperties
data class Album(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "",
    val summary: String = "",
    val tags: List<String> = emptyList(),
    val coverPhotoUri: String = "",
    val photoCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val relationship: String = "",
    val faceCropBase64: String = "",
    val referencePhotoUri: String = "",
    val taggedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Collage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monthKey: String = "",
    val monthLabel: String = "",
    val title: String = "",
    val storyNote: String = "",
    val layoutStyle: String = CollageLayoutStyle.EDITORIAL_TRIO.name,
    val photoUris: List<String> = emptyList(),
    val paletteTone: String = CollagePaletteTone.TERRACOTTA.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MemoryRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val photoKey: String = "",
    val uriString: String = "",
    val displayName: String = "",
    val monthKey: String = "",
    val albumCategory: String = "",
    val caption: String = "",
    val tags: List<String> = emptyList(),
    val taggedMemberIds: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val takenAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
