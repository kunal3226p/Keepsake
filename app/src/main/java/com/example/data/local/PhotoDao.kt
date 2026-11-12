package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class MonthSummary(
    val monthKey: String,
    val monthLabel: String,
    val totalCount: Int,
    val favoriteCount: Int
)

data class AlbumCountSummary(
    val albumCategory: String,
    val photoCount: Int
)

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE userId = :userId ORDER BY takenAtMillis DESC")
    fun observeAllPhotos(userId: String): Flow<List<PhotoEntity>>

    @Query("SELECT COUNT(*) FROM photos WHERE userId = :userId")
    fun observeTotalCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE userId = :userId AND aiAnalyzed = 1")
    fun observeAnalyzedCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE userId = :userId AND isFavorite = 1")
    fun observeFavoriteCount(userId: String): Flow<Int>

    @Query(
        """
        SELECT * FROM photos
        WHERE userId = :userId
          AND (
            displayName LIKE '%' || :query || '%'
            OR albumCategory LIKE '%' || :query || '%'
            OR tagsCsv LIKE '%' || :query || '%'
            OR caption LIKE '%' || :query || '%'
            OR taggedMemberNamesCsv LIKE '%' || :query || '%'
            OR monthLabel LIKE '%' || :query || '%'
          )
        ORDER BY takenAtMillis DESC
        """
    )
    fun searchPhotos(userId: String, query: String): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT monthKey, monthLabel,
               COUNT(*) AS totalCount,
               SUM(CASE WHEN isFavorite = 1 THEN 1 ELSE 0 END) AS favoriteCount
        FROM photos
        WHERE userId = :userId
        GROUP BY monthKey, monthLabel
        ORDER BY monthKey DESC
        """
    )
    fun observeMonthSummaries(userId: String): Flow<List<MonthSummary>>

    @Query(
        """
        SELECT albumCategory, COUNT(*) AS photoCount
        FROM photos
        WHERE userId = :userId
        GROUP BY albumCategory
        ORDER BY photoCount DESC
        """
    )
    fun observeAlbumCounts(userId: String): Flow<List<AlbumCountSummary>>

    @Query("SELECT * FROM photos WHERE userId = :userId AND aiAnalyzed = 0 ORDER BY takenAtMillis DESC LIMIT :limit")
    suspend fun getUnanalyzedBatch(userId: String, limit: Int): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE userId = :userId AND faceScanned = 0 ORDER BY takenAtMillis DESC LIMIT :limit")
    suspend fun getUnscannedFacesBatch(userId: String, limit: Int): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE userId = :userId AND monthKey = :monthKey AND isFavorite = 1 ORDER BY takenAtMillis DESC")
    suspend fun getFavoritesForMonth(userId: String, monthKey: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE userId = :userId AND monthKey = :monthKey ORDER BY takenAtMillis DESC LIMIT :limit")
    suspend fun getPhotosForMonth(userId: String, monthKey: String, limit: Int): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :photoId AND userId = :userId LIMIT 1")
    suspend fun getPhotoById(userId: String, photoId: String): PhotoEntity?

    @Query("SELECT DISTINCT albumCategory FROM photos WHERE userId = :userId AND albumCategory != 'Uncategorized'")
    suspend fun getExistingCategories(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatchIgnoreExisting(photos: List<PhotoEntity>)

    @Query(
        """
        UPDATE photos
        SET albumCategory = :albumCategory,
            tagsCsv = :tagsCsv,
            caption = :caption,
            isFavorite = CASE WHEN isFavorite = 1 THEN 1 ELSE :suggestedFavorite END,
            aiAnalyzed = 1
        WHERE id = :photoId AND userId = :userId
        """
    )
    suspend fun updateAiClassification(
        userId: String,
        photoId: String,
        albumCategory: String,
        tagsCsv: String,
        caption: String,
        suggestedFavorite: Boolean
    )

    @Query(
        """
        UPDATE photos
        SET taggedMemberIdsCsv = :memberIdsCsv,
            taggedMemberNamesCsv = :memberNamesCsv,
            faceScanned = 1
        WHERE id = :photoId AND userId = :userId
        """
    )
    suspend fun updateFaceTags(
        userId: String,
        photoId: String,
        memberIdsCsv: String,
        memberNamesCsv: String
    )

    @Query("UPDATE photos SET faceScanned = 0 WHERE userId = :userId")
    suspend fun resetFaceScanFlags(userId: String)

    @Query("UPDATE photos SET isFavorite = :isFavorite WHERE id = :photoId AND userId = :userId")
    suspend fun setFavorite(userId: String, photoId: String, isFavorite: Boolean)

    @Query("UPDATE photos SET albumCategory = :newCategory WHERE id = :photoId AND userId = :userId")
    suspend fun setAlbumCategory(userId: String, photoId: String, newCategory: String)

    @Query("SELECT COUNT(*) FROM photos WHERE userId = :userId AND taggedMemberIdsCsv LIKE '%' || :memberId || '%'")
    suspend fun countPhotosWithMember(userId: String, memberId: String): Int

    @Query("SELECT COUNT(*) FROM photos WHERE userId = :userId AND albumCategory = :category")
    suspend fun countPhotosInCategory(userId: String, category: String): Int

    @Query("DELETE FROM photos WHERE id = :photoId AND userId = :userId")
    suspend fun deletePhoto(userId: String, photoId: String)
}
