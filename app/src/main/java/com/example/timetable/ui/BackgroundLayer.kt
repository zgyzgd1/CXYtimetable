package com.example.timetable.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppBackgroundLayer(backgroundImageUri: String?) {
    val context = LocalContext.current
    val backgroundImage by produceState<ImageBitmap?>(initialValue = null, context, backgroundImageUri) {
        value = loadBackgroundImage(context, backgroundImageUri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                    ),
                ),
            ),
    ) {
        backgroundImage?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.72f,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.58f),
                        ),
                    ),
                ),
        )
    }
}

private suspend fun loadBackgroundImage(context: Context, backgroundImageUri: String?): ImageBitmap? {
    val uriText = backgroundImageUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriText)
            val displayMetrics = context.resources.displayMetrics
            val targetWidth = displayMetrics.widthPixels.coerceAtLeast(1)
            val targetHeight = displayMetrics.heightPixels.coerceAtLeast(1)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return@runCatching null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    sourceWidth = bounds.outWidth,
                    sourceHeight = bounds.outHeight,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }?.asImageBitmap()
        }.getOrNull()
    }
}

internal fun calculateInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1

    var inSampleSize = 1
    var halfWidth = sourceWidth / 2
    var halfHeight = sourceHeight / 2

    while (
        halfWidth / inSampleSize >= targetWidth &&
        halfHeight / inSampleSize >= targetHeight
    ) {
        inSampleSize *= 2
    }

    return inSampleSize.coerceAtLeast(1)
}
