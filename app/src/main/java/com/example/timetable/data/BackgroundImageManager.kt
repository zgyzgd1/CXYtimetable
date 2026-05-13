package com.example.timetable.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackgroundImageManager {
    private const val DIRECTORY_NAME = "appearance"
    private const val FILE_NAME = "custom_background.jpg"
    private const val MAX_IMAGE_DIMENSION = 1800
    private const val JPEG_QUALITY = 90

    fun customBackgroundFile(context: Context): File {
        return File(File(context.applicationContext.filesDir, DIRECTORY_NAME), FILE_NAME)
    }

    fun hasCustomBackground(context: Context): Boolean = customBackgroundFile(context).isFile

    @Throws(IOException::class)
    suspend fun saveCustomBackground(
        context: Context,
        contentResolver: ContentResolver,
        uri: Uri,
    ) = withContext(Dispatchers.IO) {
        val bitmap = decodeScaledBitmap(contentResolver, uri)
            ?: throw IOException("Unable to decode selected image.")
        val targetFile = customBackgroundFile(context)
        targetFile.parentFile?.mkdirs()

        try {
            FileOutputStream(targetFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IOException("Unable to write background image.")
                }
            }
        } finally {
            bitmap.recycle()
        }

        AppearanceStore.setCustomBackground(context)
    }

    suspend fun clearCustomBackground(context: Context) = withContext(Dispatchers.IO) {
        customBackgroundFile(context).delete()
    }

    private fun decodeScaledBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
    ): Bitmap? {
        val orientation = readExifOrientation(contentResolver, uri)
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateInSampleSize(
                width = boundsOptions.outWidth,
                height = boundsOptions.outHeight,
                maxDimension = MAX_IMAGE_DIMENSION,
            )
        }

        val decodedBitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val longestEdge = max(decodedBitmap.width, decodedBitmap.height)
        val scaledBitmap = if (longestEdge <= MAX_IMAGE_DIMENSION) {
            decodedBitmap
        } else {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / longestEdge.toFloat()
            val targetWidth = (decodedBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (decodedBitmap.height * scale).roundToInt().coerceAtLeast(1)
            decodedBitmap.scale(targetWidth, targetHeight, filter = true).also { scaled ->
                if (scaled !== decodedBitmap) {
                    decodedBitmap.recycle()
                }
            }
        }
        return applyExifOrientation(scaledBitmap, orientation)
    }

    private fun readExifOrientation(
        contentResolver: ContentResolver,
        uri: Uri,
    ): Int {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { transformed ->
            if (transformed !== bitmap) {
                bitmap.recycle()
            }
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Int {
        var sampleSize = 1
        val longestEdge = max(width, height)
        while (longestEdge / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
