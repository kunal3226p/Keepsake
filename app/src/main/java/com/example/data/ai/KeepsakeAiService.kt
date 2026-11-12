package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.firestore.CollageLayoutStyle
import com.example.data.firestore.CollagePaletteTone
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiPhotoClassification(
    val photoKey: String,
    val albumCategory: String,
    val tags: List<String>,
    val caption: String,
    val isHighlight: Boolean
)

data class AiFaceMatchResult(
    val photoKey: String,
    val matchedMemberIds: List<String>
)

data class AiCollageCuration(
    val title: String,
    val storyNote: String,
    val layoutStyle: CollageLayoutStyle,
    val paletteTone: CollagePaletteTone,
    val orderedPhotoKeys: List<String>
)

class KeepsakeAiService {

    private val categorizationModel by lazy {
        val itemSchema = Schema.obj(
            properties = mapOf(
                "photoKey" to Schema.string(description = "Exact photoKey supplied in the prompt"),
                "albumCategory" to Schema.string(description = "Concise searchable album category name, e.g. 'Family Gatherings', 'Beach & Coast', 'Kids & Milestones', 'Celebrations', 'Nature & Hikes', 'Pets', 'Food & Dining', 'Travel & Landmarks', 'Home & Everyday'"),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 7 lowercase searchable keywords describing people, setting, objects, mood, season"
                ),
                "caption" to Schema.string(description = "Warm 1-sentence editorial description of the photo"),
                "isHighlight" to Schema.boolean(description = "True if the photo is crisp, well-composed, and emotionally memorable")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "items" to Schema.array(
                    items = itemSchema,
                    description = "One classification entry per supplied photo"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    private val faceRecognitionModel by lazy {
        val matchSchema = Schema.obj(
            properties = mapOf(
                "photoKey" to Schema.string(description = "Exact photoKey of the target photo"),
                "matchedMemberIds" to Schema.array(
                    items = Schema.string(),
                    description = "List of memberIds of registered family members clearly visible in this photo"
                )
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "matches" to Schema.array(
                    items = matchSchema,
                    description = "Face match results for each target photo"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    private val collageModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Poetic, warm editorial title for the monthly collage, max 6 words"),
                "storyNote" to Schema.string(description = "Heartfelt 1-2 sentence summary capturing the month's favorite memories"),
                "layoutStyle" to Schema.enumeration(
                    values = CollageLayoutStyle.entries.map { it.name },
                    description = "Best visual layout for the number and feel of photos"
                ),
                "paletteTone" to Schema.enumeration(
                    values = CollagePaletteTone.entries.map { it.name },
                    description = "Best paper tone matching the photos' lighting and mood"
                ),
                "orderedPhotoKeys" to Schema.array(
                    items = Schema.string(),
                    description = "Ordered list of up to 6 photoKeys selected for the collage"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    suspend fun categorizePhotoBatch(
        photos: List<Pair<String, Bitmap>>,
        existingCategories: List<String>
    ): List<AiPhotoClassification> {
        if (photos.isEmpty()) return emptyList()
        val existingHint = if (existingCategories.isNotEmpty()) {
            "Prefer reusing these existing album categories when appropriate: ${existingCategories.joinToString(", ")}."
        } else {
            "Use cohesive, memorable album category names like 'Family Gatherings', 'Travel & Outdoors', 'Celebrations', 'Kids & Milestones', 'Pets', 'Food & Dining', 'Nature & Sunsets', or 'Everyday Moments'."
        }

        return try {
            val response = withTimeout(60_000L) {
                categorizationModel.generateContent(
                    content {
                        text(
                            "You are organizing a large personal photo library into searchable albums. " +
                                "$existingHint For each photo labeled with its photoKey below, assign an albumCategory, " +
                                "3-7 searchable tags, a brief caption, and whether it is a standout favorite highlight."
                        )
                        photos.forEach { (key, bitmap) ->
                            text("photoKey: $key")
                            image(bitmap)
                        }
                    }
                )
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty categorization response.")
            val root = JSONObject(raw)
            val items = root.getJSONArray("items")
            buildList {
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val tagsArray = obj.optJSONArray("tags")
                    val tags = buildList {
                        if (tagsArray != null) {
                            for (t in 0 until tagsArray.length()) {
                                val tag = tagsArray.optString(t).trim().lowercase()
                                if (tag.isNotEmpty()) add(tag)
                            }
                        }
                    }
                    add(
                        AiPhotoClassification(
                            photoKey = obj.getString("photoKey"),
                            albumCategory = obj.getString("albumCategory").trim().ifEmpty { "Everyday Moments" },
                            tags = tags.distinct().take(10),
                            caption = obj.optString("caption", "").trim(),
                            isHighlight = obj.optBoolean("isHighlight", false)
                        )
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo categorization timed out", e)
            throw IllegalStateException("Photo categorization timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo categorization failed", e)
            throw e
        }
    }

    suspend fun identifyFamilyFacesInBatch(
        referenceFaces: List<Triple<String, String, Bitmap>>,
        targetPhotos: List<Pair<String, Bitmap>>
    ): List<AiFaceMatchResult> {
        if (referenceFaces.isEmpty() || targetPhotos.isEmpty()) return emptyList()
        val validIds = referenceFaces.map { it.first }.toSet()

        return try {
            val response = withTimeout(60_000L) {
                faceRecognitionModel.generateContent(
                    content {
                        text(
                            "Compare the registered family reference faces with each target photo. " +
                                "Only include a memberId in matchedMemberIds when you are confident that specific person's face appears in the target photo."
                        )
                        referenceFaces.forEach { (memberId, name, faceBitmap) ->
                            text("REFERENCE FACE -> memberId: $memberId, name: $name")
                            image(faceBitmap)
                        }
                        targetPhotos.forEach { (photoKey, photoBitmap) ->
                            text("TARGET PHOTO -> photoKey: $photoKey")
                            image(photoBitmap)
                        }
                    }
                )
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty face matching response.")
            val root = JSONObject(raw)
            val matches = root.getJSONArray("matches")
            buildList {
                for (i in 0 until matches.length()) {
                    val obj = matches.getJSONObject(i)
                    val idsArr = obj.optJSONArray("matchedMemberIds")
                    val ids = buildList {
                        if (idsArr != null) {
                            for (j in 0 until idsArr.length()) {
                                val id = idsArr.optString(j).trim()
                                if (id in validIds) add(id)
                            }
                        }
                    }
                    add(
                        AiFaceMatchResult(
                            photoKey = obj.getString("photoKey"),
                            matchedMemberIds = ids.distinct()
                        )
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Face identification timed out", e)
            throw IllegalStateException("Family face identification timed out. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Face identification failed", e)
            throw e
        }
    }

    suspend fun curateMonthlyCollage(
        monthLabel: String,
        candidates: List<Triple<String, String, Bitmap>>
    ): AiCollageCuration {
        require(candidates.isNotEmpty()) { "At least one photo is needed to curate a collage." }

        return try {
            val response = withTimeout(60_000L) {
                collageModel.generateContent(
                    content {
                        text(
                            "You are an editorial family photo curator creating a keepsake monthly collage for $monthLabel. " +
                                "Examine these favorite photos, select the best sequence of up to 6 photos (or all if fewer), " +
                                "choose the ideal layoutStyle and paletteTone, and craft a warm title and storyNote."
                        )
                        candidates.forEach { (photoKey, metadataNote, bitmap) ->
                            text("photoKey: $photoKey | context: $metadataNote")
                            image(bitmap)
                        }
                    }
                )
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty collage curation.")
            val root = JSONObject(raw)
            val orderedArr = root.optJSONArray("orderedPhotoKeys")
            val validKeys = candidates.map { it.first }.toSet()
            val ordered = buildList {
                if (orderedArr != null) {
                    for (i in 0 until orderedArr.length()) {
                        val key = orderedArr.optString(i).trim()
                        if (key in validKeys && key !in this) add(key)
                    }
                }
            }.ifEmpty { candidates.take(6).map { it.first } }

            AiCollageCuration(
                title = root.optString("title", "$monthLabel Highlights").trim().ifEmpty { "$monthLabel Highlights" },
                storyNote = root.optString("storyNote", "Favorite moments gathered from $monthLabel.").trim(),
                layoutStyle = CollageLayoutStyle.fromRaw(root.optString("layoutStyle")),
                paletteTone = CollagePaletteTone.fromRaw(root.optString("paletteTone")),
                orderedPhotoKeys = ordered.take(6)
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Collage curation timed out", e)
            throw IllegalStateException("Collage curation timed out. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Collage curation failed", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "KeepsakeAiService"
    }
}
