package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firestore.CollageLayoutStyle
import com.example.data.firestore.CollagePaletteTone
import com.example.data.firestore.KeepsakeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class KeepsakeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAlbum_authenticatedOwner_createsAndObservesAlbum() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = KeepsakeRepository(firestore, auth)
        val albumId = "alb_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertAlbum(
                albumId = albumId,
                title = "Summer Beaches",
                category = "Travel & Outdoors",
                summary = "Coastal sunsets and ocean swims",
                tags = listOf("beach", "sunset", "ocean"),
                coverPhotoUri = "content://media/external/images/1",
                photoCount = 34L
            )
        }

        val albums = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAlbums(uid).first { list -> list.any { it.id == albumId } }
        }
        val created = albums.first { it.id == albumId }
        assertEquals("Summer Beaches", created.title)
        assertEquals(34L, created.photoCount)
    }

    @Test
    fun addFamilyMemberAndCollage_authenticatedOwner_persistsData() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = KeepsakeRepository(firestore, auth)
        val memberId = "mem_${UUID.randomUUID().toString().take(8)}"
        val collageId = "col_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFamilyMember(
                memberId = memberId,
                name = "Grandma Clara",
                relationship = "Grandmother",
                faceCropBase64 = "ZmFrZUJhc2U2NA==",
                referencePhotoUri = "content://media/external/images/2",
                taggedCount = 19L
            )
            repository.upsertCollage(
                collageId = collageId,
                monthKey = "2026-11",
                monthLabel = "November 2026",
                title = "Golden Autumn Evenings",
                storyNote = "Quiet family dinners and crisp leaf walks.",
                layoutStyle = CollageLayoutStyle.EDITORIAL_TRIO,
                photoUris = listOf("content://media/1", "content://media/2"),
                paletteTone = CollagePaletteTone.TERRACOTTA
            )
        }

        val members = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFamilyMembers(uid).first { list -> list.any { it.id == memberId } }
        }
        assertTrue(members.any { it.name == "Grandma Clara" })

        val collages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCollages(uid).first { list -> list.any { it.id == collageId } }
        }
        assertEquals("Golden Autumn Evenings", collages.first { it.id == collageId }.title)
    }

    @Test
    fun getAlbum_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = KeepsakeRepository(firestore, auth)
        val albumId = "alb_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertAlbum(
                albumId = albumId,
                title = "Alice Private",
                category = "Family",
                summary = "Private family moments",
                tags = listOf("family"),
                coverPhotoUri = "content://media/9",
                photoCount = 5L
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = KeepsakeRepository(firestore, auth)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getAlbum(aliceUid, albumId)
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user access")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun observeAlbums_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = KeepsakeRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAlbums("alice_uid_placeholder").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@keepsake.test"
        const val BOB_EMAIL = "bob@keepsake.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
