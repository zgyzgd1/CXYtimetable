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
    val customBackground by produceState<ImageBitmap?>(initialValue = null, backgroundAppearance) {
        value = if (backgroundAppearance.mode == AppBackgroundMode.CUSTOM_IMAGE) {
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
        when {
            customBackground != null -> {
                Image(
                    bitmap = customBackground!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0.04f, -0.02f),
                )
            }
            backgroundAppearance.mode != AppBackgroundMode.GRADIENT -> {
                Image(
                    painter = painterResource(id = R.drawable.default_background_image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(horizontalBias = 0.28f, verticalBias = -0.10f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
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
            modifier = Modifier
                .fillMaxSize()
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
            modifier = Modifier
                .fillMaxSize()
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
}
