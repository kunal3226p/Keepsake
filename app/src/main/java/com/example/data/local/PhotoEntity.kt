package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "monthKey"]),
        Index(value = ["userId", "albumCategory"]),
        Index(value = ["userId", "isFavorite"]),
        Index(value = ["userId", "aiAnalyzed"])
    ]
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val uriString: String,
    val displayName: String,
    val takenAtMillis: Long,
    val monthKey: String,
    val monthLabel: String,
    val albumCategory: String = "Uncategorized",
    val caption: String = "",
    val tagsCsv: String = "",
    val taggedMemberIdsCsv: String = "",
    val taggedMemberNamesCsv: String = "",
    val isFavorite: Boolean = false,
    val aiAnalyzed: Boolean = false,
    val faceScanned: Boolean = false,
    val addedAtMillis: Long = System.currentTimeMillis()
) {
    fun tagList(): List<String> =
        tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun taggedMemberIdList(): List<String> =
        taggedMemberIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun taggedMemberNameList(): List<String> =
        taggedMemberNamesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
