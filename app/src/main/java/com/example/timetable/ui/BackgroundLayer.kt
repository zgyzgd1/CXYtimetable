package com.example.timetable.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.timetable.R
import com.example.timetable.data.AppBackgroundMode
import com.example.timetable.data.BackgroundAppearance
import com.example.timetable.data.BackgroundImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppBackgroundLayer(backgroundAppearance: BackgroundAppearance) {
    val context = LocalContext.current
    // Only decode custom background when mode is CUSTOM_IMAGE; cache bitmap until mode changes
    val customBackground by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = backgroundAppearance.mode,
    ) {
        if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
            withContext(Dispatchers.IO) {
                BackgroundImageManager.customBackgroundFile(context)
                    .takeIf { it.isFile }
                    ?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
                    ?.asImageBitmap()
            }
        } else {
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (backgroundAppearance.mode) {
            AppBackgroundMode.CUSTOM_IMAGE -> {
                customBackground?.let { bitmap ->
                    CustomBackgroundImage(
                        bitmap = bitmap,
                        imageTransform = backgroundAppearance.imageTransform,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            AppBackgroundMode.BUNDLED_IMAGE -> {
                Image(
                    painter = painterResource(id = R.drawable.default_background_image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(horizontalBias = 0.28f, verticalBias = -0.10f),
                )
            }
            AppBackgroundMode.GRADIENT -> { /* No image layer for gradient mode */ }
        }

        BackgroundTintOverlays(modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun BackgroundTintOverlays(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f),
                    ),
                ),
            ),
    )

    Box(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                ),
            ),
    )

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.34f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                    ),
                ),
            ),
    )
}
