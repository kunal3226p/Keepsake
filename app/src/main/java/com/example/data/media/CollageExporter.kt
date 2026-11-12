package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.firestore.Collage
import com.example.data.firestore.CollageLayoutStyle
import com.example.data.firestore.CollagePaletteTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CollageExporter {

    suspend fun renderCollageBitmap(
        context: Context,
        collage: Collage
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1350
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val tone = CollagePaletteTone.fromRaw(collage.paletteTone)
        val bgColor = when (tone) {
            CollagePaletteTone.TERRACOTTA -> 0xFFFAF7F2.toInt()
            CollagePaletteTone.SAGE -> 0xFFF2F6F1.toInt()
            CollagePaletteTone.ESPRESSO -> 0xFF1D1815.toInt()
            CollagePaletteTone.GOLDEN -> 0xFFFDF8ED.toInt()
        }
        val textColor = if (tone == CollagePaletteTone.ESPRESSO) 0xFFF3ECE2.toInt() else 0xFF1E1915.toInt()
        val accentColor = when (tone) {
            CollagePaletteTone.TERRACOTTA -> 0xFFB34C21.toInt()
            CollagePaletteTone.SAGE -> 0xFF47664F.toInt()
            CollagePaletteTone.ESPRESSO -> 0xFFFFB59A.toInt()
            CollagePaletteTone.GOLDEN -> 0xFF8C5F18.toInt()
        }

        canvas.drawColor(bgColor)

        val serifTypeface = try {
            ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF
        } catch (_: Exception) {
            Typeface.SERIF
        }
        val sansTypeface = try {
            ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.SANS_SERIF
        } catch (_: Exception) {
            Typeface.SANS_SERIF
        }

        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 30f
            typeface = Typeface.create(sansTypeface, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 62f
            typeface = serifTypeface
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            alpha = 195
            textSize = 30f
            typeface = sansTypeface
        }

        val margin = 64f
        canvas.drawText(collage.monthLabel.uppercase(), margin, 96f, monthPaint)
        canvas.drawText(collage.title.take(32), margin, 168f, titlePaint)

        val bitmaps = collage.photoUris.take(6).mapNotNull { uriStr ->
            PhotoImportHelper.loadScaledBitmap(context, Uri.parse(uriStr), 720)
        }

        val gridTop = 216f
        val gridBottom = 1190f
        val gridRect = RectF(margin, gridTop, width - margin, gridBottom)
        val gap = 20f

        val slots = computeSlots(
            CollageLayoutStyle.fromRaw(collage.layoutStyle),
            bitmaps.size,
            gridRect,
            gap
        )

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (tone == CollagePaletteTone.ESPRESSO) 0xFF2B2420.toInt() else 0xFFEBE2D6.toInt()
        }

        slots.forEachIndexed { index, rect ->
            canvas.drawRoundRect(rect, 24f, 24f, framePaint)
            val bmp = bitmaps.getOrNull(index) ?: return@forEachIndexed
            drawCenterCroppedBitmap(canvas, bmp, rect, 24f)
        }

        val note = collage.storyNote.ifBlank { "Curated with Keepsake" }
        canvas.drawText(note.take(64), margin, 1270f, notePaint)
        bitmaps.forEach { it.recycle() }
        output
    }

    private fun computeSlots(
        style: CollageLayoutStyle,
        count: Int,
        area: RectF,
        gap: Float
    ): List<RectF> {
        if (count <= 1) return listOf(area)
        val w = area.width()
        val h = area.height()
        return when (style) {
            CollageLayoutStyle.EDITORIAL_TRIO -> {
                val topH = h * 0.56f
                val bottomTop = area.top + topH + gap
                val halfW = (w - gap) / 2f
                listOf(
                    RectF(area.left, area.top, area.right, area.top + topH),
                    RectF(area.left, bottomTop, area.left + halfW, area.bottom),
                    RectF(area.left + halfW + gap, bottomTop, area.right, area.bottom)
                ).take(count.coerceAtMost(3))
            }
            CollageLayoutStyle.GRID_2X2 -> {
                val cellW = (w - gap) / 2f
                val cellH = (h - gap) / 2f
                listOf(
                    RectF(area.left, area.top, area.left + cellW, area.top + cellH),
                    RectF(area.left + cellW + gap, area.top, area.right, area.top + cellH),
                    RectF(area.left, area.top + cellH + gap, area.left + cellW, area.bottom),
                    RectF(area.left + cellW + gap, area.top + cellH + gap, area.right, area.bottom)
                ).take(count.coerceAtMost(4))
            }
            CollageLayoutStyle.HERO_STRIP -> {
                val topH = h * 0.65f
                val bottomTop = area.top + topH + gap
                val cols = (count - 1).coerceIn(1, 3)
                val cellW = (w - gap * (cols - 1)) / cols
                buildList {
                    add(RectF(area.left, area.top, area.right, area.top + topH))
                    for (i in 0 until cols) {
                        val left = area.left + i * (cellW + gap)
                        add(RectF(left, bottomTop, left + cellW, area.bottom))
                    }
                }
            }
            CollageLayoutStyle.MOSAIC_6 -> {
                val cellW = (w - gap) / 2f
                val cellH = (h - gap * 2f) / 3f
                buildList {
                    for (r in 0 until 3) {
                        for (c in 0 until 2) {
                            if (size < count.coerceAtMost(6)) {
                                val left = area.left + c * (cellW + gap)
                                val top = area.top + r * (cellH + gap)
                                add(RectF(left, top, left + cellW, top + cellH))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun drawCenterCroppedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        dest: RectF,
        cornerRadius: Float
    ) {
        val path = android.graphics.Path().apply {
            addRoundRect(dest, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)

        val scale = maxOf(dest.width() / bitmap.width, dest.height() / bitmap.height)
        val scaledW = bitmap.width * scale
        val scaledH = bitmap.height * scale
        val dx = dest.left + (dest.width() - scaledW) / 2f
        val dy = dest.top + (dest.height() - scaledH) / 2f
        val drawRect = RectF(dx, dy, dx + scaledW, dy + scaledH)
        canvas.drawBitmap(bitmap, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    suspend fun shareCollage(context: Context, collage: Collage) {
        val bitmap = renderCollageBitmap(context, collage)
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "collages").apply { mkdirs() }
            val file = File(dir, "keepsake_${collage.monthKey}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, collage.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${collage.monthLabel} Collage"))
    }

    suspend fun saveCollageToGallery(context: Context, collage: Collage): Boolean =
        withContext(Dispatchers.IO) {
            val bitmap = renderCollageBitmap(context, collage)
            try {
                val filename = "Keepsake_${collage.monthKey}_${System.currentTimeMillis()}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Keepsake")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                true
            } catch (_: Exception) {
                false
            } finally {
                bitmap.recycle()
            }
        }
}
