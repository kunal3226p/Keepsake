package com.example.data.firestore

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KeepsakeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")

    fun observeAlbums(userId: String): Flow<List<Album>> {
        val path = "users/$userId/albums"
        return userDoc(userId).collection("albums")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    Album::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.photoCount }
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeFamilyMembers(userId: String): Flow<List<FamilyMember>> {
        val path = "users/$userId/family_members"
        return userDoc(userId).collection("family_members")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    FamilyMember::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeCollages(userId: String): Flow<List<Collage>> {
        val path = "users/$userId/collages"
        return userDoc(userId).collection("collages")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    Collage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.monthKey }
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    fun observeMemories(userId: String): Flow<List<MemoryRecord>> {
        val path = "users/$userId/memories"
        return userDoc(userId).collection("memories")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    MemoryRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.takenAtMillis }
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    suspend fun upsertAlbum(
        albumId: String,
        title: String,
        category: String,
        summary: String,
        tags: List<String>,
        coverPhotoUri: String,
        photoCount: Long
    ) {
        val uid = requireUid()
        val safeId = sanitizeDocId(albumId)
        val docRef = userDoc(uid).collection("albums").document(safeId)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val createdAt = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
                val updatePayload = mapOf(
                    "title" to title.take(80).ifBlank { "Memories" },
                    "category" to category.take(60).ifBlank { "General" },
                    "summary" to summary.take(300),
                    "tags" to tags.take(15),
                    "coverPhotoUri" to coverPhotoUri.take(512),
                    "photoCount" to photoCount.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existing.getTimestamp("createdAt") != null) {
                    docRef.update(updatePayload).await()
                } else {
                    docRef.set(updatePayload + mapOf("userId" to uid, "createdAt" to createdAt)).await()
                }
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "title" to title.take(80).ifBlank { "Memories" },
                    "category" to category.take(60).ifBlank { "General" },
                    "summary" to summary.take(300),
                    "tags" to tags.take(15),
                    "coverPhotoUri" to coverPhotoUri.take(512),
                    "photoCount" to photoCount.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAlbum(albumId: String) {
        val uid = requireUid()
        val docRef = userDoc(uid).collection("albums").document(sanitizeDocId(albumId))
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun addFamilyMember(
        memberId: String,
        name: String,
        relationship: String,
        faceCropBase64: String,
        referencePhotoUri: String,
        taggedCount: Long = 0L
    ): String {
        val uid = requireUid()
        val safeId = sanitizeDocId(memberId)
        val docRef = userDoc(uid).collection("family_members").document(safeId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(60),
            "relationship" to relationship.trim().take(40).ifBlank { "Family" },
            "faceCropBase64" to faceCropBase64.take(155000),
            "referencePhotoUri" to referencePhotoUri.take(512),
            "taggedCount" to taggedCount.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return safeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateFamilyMemberTaggedCount(member: FamilyMember, newCount: Long) {
        val uid = requireUid()
        require(member.id.isNotBlank()) { "Member ID required" }
        requireNotNull(member.createdAt) { "Member createdAt required" }
        val docRef = userDoc(uid).collection("family_members").document(member.id)
        try {
            docRef.set(member.copy(taggedCount = newCount.coerceAtLeast(0L), updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFamilyMember(memberId: String) {
        val uid = requireUid()
        val docRef = userDoc(uid).collection("family_members").document(sanitizeDocId(memberId))
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun upsertCollage(
        collageId: String,
        monthKey: String,
        monthLabel: String,
        title: String,
        storyNote: String,
        layoutStyle: CollageLayoutStyle,
        photoUris: List<String>,
        paletteTone: CollagePaletteTone
    ) {
        val uid = requireUid()
        val safeId = sanitizeDocId(collageId)
        val docRef = userDoc(uid).collection("collages").document(safeId)
        try {
            val existing = docRef.get().await()
            if (existing.exists() && existing.getTimestamp("createdAt") != null) {
                docRef.update(
                    mapOf(
                        "monthKey" to monthKey.take(12),
                        "monthLabel" to monthLabel.take(40),
                        "title" to title.take(100).ifBlank { "$monthLabel Memories" },
                        "storyNote" to storyNote.take(400),
                        "layoutStyle" to layoutStyle.name,
                        "photoUris" to photoUris.take(12).map { it.take(512) },
                        "paletteTone" to paletteTone.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "monthKey" to monthKey.take(12),
                        "monthLabel" to monthLabel.take(40),
                        "title" to title.take(100).ifBlank { "$monthLabel Memories" },
                        "storyNote" to storyNote.take(400),
                        "layoutStyle" to layoutStyle.name,
                        "photoUris" to photoUris.take(12).map { it.take(512) },
                        "paletteTone" to paletteTone.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCollage(collageId: String) {
        val uid = requireUid()
        val docRef = userDoc(uid).collection("collages").document(sanitizeDocId(collageId))
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun upsertMemoryRecord(
        photoKey: String,
        uriString: String,
        displayName: String,
        monthKey: String,
        albumCategory: String,
        caption: String,
        tags: List<String>,
        taggedMemberIds: List<String>,
        isFavorite: Boolean,
        takenAtMillis: Long
    ) {
        val uid = requireUid()
        val safeId = sanitizeDocId(photoKey)
        val docRef = userDoc(uid).collection("memories").document(safeId)
        try {
            val existing = docRef.get().await()
            if (existing.exists() && existing.getTimestamp("createdAt") != null) {
                docRef.update(
                    mapOf(
                        "albumCategory" to albumCategory.take(60).ifBlank { "Uncategorized" },
                        "caption" to caption.take(300),
                        "tags" to tags.take(15),
                        "taggedMemberIds" to taggedMemberIds.take(15),
                        "isFavorite" to isFavorite,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "photoKey" to safeId,
                        "uriString" to uriString.take(512),
                        "displayName" to displayName.take(180).ifBlank { "photo.jpg" },
                        "monthKey" to monthKey.take(12).ifBlank { "2026-01" },
                        "albumCategory" to albumCategory.take(60).ifBlank { "Uncategorized" },
                        "caption" to caption.take(300),
                        "tags" to tags.take(15),
                        "taggedMemberIds" to taggedMemberIds.take(15),
                        "isFavorite" to isFavorite,
                        "takenAtMillis" to takenAtMillis.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getAlbum(userId: String, albumId: String): Album? {
        val docRef = userDoc(userId).collection("albums").document(sanitizeDocId(albumId))
        return try {
            docRef.get().await()
                .toObject(Album::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: return
        val activeUid = try {
            Firebase.auth.currentUser?.uid
        } catch (_: Exception) {
            null
        }
        if (activeUid == expectedUid || activeUid == null) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    companion object {
        fun sanitizeDocId(raw: String): String {
            val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .trim('_')
                .take(120)
            return cleaned.ifBlank { "doc_${System.currentTimeMillis()}" }
        }
    }
}
