package com.example.data.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.local.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoImportHelper {

    fun tryTakePersistableReadPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Photo Picker URIs may grant temporary/system-managed access without persistable flags
        } catch (_: Exception) {
        }
    }

    suspend fun scanFolderTreeForPhotos(
        context: Context,
        treeUri: Uri,
        userId: String,
        onDiscoveredBatch: suspend (List<PhotoEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        tryTakePersistableReadPermission(context, treeUri)
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val stack = ArrayDeque<String>()
        stack.addLast(rootDocId)

        var totalFound = 0
        val batch = ArrayList<PhotoEntity>(100)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        while (stack.isNotEmpty()) {
            val currentDocId = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.addLast(docId)
                    } else if (mime.startsWith("image/")) {
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Photo" else "Photo"
                        val lastMod = if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                            cursor.getLong(modIdx)
                        } else {
                            System.currentTimeMillis()
                        }
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val takenMillis = extractExifOrFallbackTime(context, docUri, lastMod)
                        val (monthKey, monthLabel) = formatMonthKeyAndLabel(takenMillis)
                        batch.add(
                            PhotoEntity(
                                id = stablePhotoId(userId, docUri.toString()),
                                userId = userId,
                                uriString = docUri.toString(),
                                displayName = name,
                                takenAtMillis = takenMillis,
                                monthKey = monthKey,
                                monthLabel = monthLabel
                            )
                        )
                        totalFound++
                        if (batch.size >= 100) {
                            onDiscoveredBatch(batch.toList())
                            batch.clear()
                        }
                    }
                }
            }
        }
        if (batch.isNotEmpty()) {
            onDiscoveredBatch(batch.toList())
            batch.clear()
        }
        totalFound
    }

    suspend fun buildEntitiesFromUris(
        context: Context,
        uris: List<Uri>,
        userId: String
    ): List<PhotoEntity> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            tryTakePersistableReadPermission(context, uri)
            val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "Photo"
            val takenMillis = extractExifOrFallbackTime(context, uri, System.currentTimeMillis())
            val (monthKey, monthLabel) = formatMonthKeyAndLabel(takenMillis)
            PhotoEntity(
                id = stablePhotoId(userId, uri.toString()),
                userId = userId,
                uriString = uri.toString(),
                displayName = name,
                takenAtMillis = takenMillis,
                monthKey = monthKey,
                monthLabel = monthLabel
            )
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractExifOrFallbackTime(context: Context, uri: Uri, fallbackMillis: Long): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!dateStr.isNullOrBlank()) {
                    val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    parser.parse(dateStr)?.time ?: fallbackMillis
                } else {
                    fallbackMillis
                }
            } ?: fallbackMillis
        } catch (_: Exception) {
            fallbackMillis
        }
    }

    fun formatMonthKeyAndLabel(millis: Long): Pair<String, String> {
        val date = Date(millis)
        val key = SimpleDateFormat("yyyy-MM", Locale.US).format(date)
        val label = SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
        return key to label
    }

    fun stablePhotoId(userId: String, uriString: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$userId::$uriString".toByteArray(Charsets.UTF_8))
        return "ph_" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    suspend fun loadScaledBitmap(
        context: Context,
        uri: Uri,
        maxEdgePx: Int = 384
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > maxEdgePx * 2 ||
                (bounds.outHeight / sampleSize) > maxEdgePx * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            val rotated = applyExifRotation(context, uri, rawBitmap)
            val maxDim = maxOf(rotated.width, rotated.height)
            if (maxDim <= maxEdgePx) {
                rotated
            } else {
                val scale = maxEdgePx.toFloat() / maxDim.toFloat()
                val targetW = (rotated.width * scale).toInt().coerceAtLeast(1)
                val targetH = (rotated.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                    if (it !== rotated) rotated.recycle()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    fun cropSquareFaceToBase64(
        source: Bitmap,
        centerXFraction: Float,
        centerYFraction: Float,
        cropSizeFraction: Float
    ): String {
        val minSide = minOf(source.width, source.height).toFloat()
        val cropSide = (minSide * cropSizeFraction.coerceIn(0.2f, 1.0f)).toInt().coerceAtLeast(32)
        val cx = (source.width * centerXFraction.coerceIn(0f, 1f)).toInt()
        val cy = (source.height * centerYFraction.coerceIn(0f, 1f)).toInt()
        val left = (cx - cropSide / 2).coerceIn(0, (source.width - cropSide).coerceAtLeast(0))
        val top = (cy - cropSide / 2).coerceIn(0, (source.height - cropSide).coerceAtLeast(0))
        val actualW = cropSide.coerceAtMost(source.width - left)
        val actualH = cropSide.coerceAtMost(source.height - top)

        val cropped = Bitmap.createBitmap(source, left, top, actualW, actualH)
        val scaled = Bitmap.createScaledBitmap(cropped, 160, 160, true)
        if (scaled !== cropped) cropped.recycle()

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
        scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeBase64Bitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
