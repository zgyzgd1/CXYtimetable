package com.example.timetable.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

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
    fun saveCustomBackground(
        context: Context,
        contentResolver: ContentResolver,
        uri: Uri,
    ) {
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

        AppearanceStore.setBackgroundMode(context, AppBackgroundMode.CUSTOM_IMAGE)
    }

    fun clearCustomBackground(context: Context) {
        customBackgroundFile(context).delete()
    }

    private fun decodeScaledBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
    ): Bitmap? {
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
        if (longestEdge <= MAX_IMAGE_DIMENSION) {
            return decodedBitmap
        }

        val scale = MAX_IMAGE_DIMENSION.toFloat() / longestEdge.toFloat()
        val targetWidth = (decodedBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (decodedBitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(decodedBitmap, targetWidth, targetHeight, true).also { scaled ->
            if (scaled !== decodedBitmap) {
                decodedBitmap.recycle()
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
