package com.example.timetable.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.timetable.R
import com.example.timetable.data.BackgroundAppearance
import com.example.timetable.data.BackgroundImageManager
import com.example.timetable.data.BackgroundImageTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BackgroundImageAdjustDialog(
    backgroundAppearance: BackgroundAppearance,
    onDismiss: () -> Unit,
    onSave: (BackgroundImageTransform) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val customBackground by produceState<ImageBitmap?>(initialValue = null, backgroundAppearance.revision) {
        value = withContext(Dispatchers.IO) {
            BackgroundImageManager.customBackgroundFile(context)
                .takeIf { it.isFile }
                ?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
                ?.asImageBitmap()
        }
    }

    var scale by remember(backgroundAppearance.imageTransform) {
        mutableStateOf(backgroundAppearance.imageTransform.scale)
    }
    var horizontalBias by remember(backgroundAppearance.imageTransform) {
        mutableStateOf(backgroundAppearance.imageTransform.horizontalBias)
    }
    var verticalBias by remember(backgroundAppearance.imageTransform) {
        mutableStateOf(backgroundAppearance.imageTransform.verticalBias)
    }

    val previewAspectRatio = (
        configuration.screenWidthDp.toFloat() /
            configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
        ).coerceIn(0.45f, 0.72f)
    val previewTransform = remember(scale, horizontalBias, verticalBias) {
        BackgroundImageTransform(
            scale = scale,
            horizontalBias = horizontalBias,
            verticalBias = verticalBias,
        ).normalized()
    }

    // Pre-load string resources
    val strBiasLeft = stringResource(R.string.label_bias_left)
    val strBiasRight = stringResource(R.string.label_bias_right)
    val strBiasTop = stringResource(R.string.label_bias_top)
    val strBiasBottom = stringResource(R.string.label_bias_bottom)
    val strCentered = stringResource(R.string.label_centered)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.title_adjust_background),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = stringResource(R.string.hint_background_adjust),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (customBackground != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(156.dp)
                            .aspectRatio(previewAspectRatio)
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)),
                    ) {
                        CustomBackgroundImage(
                            bitmap = customBackground!!,
                            imageTransform = previewTransform,
                            modifier = Modifier.fillMaxSize(),
                        )
                        BackgroundTintOverlays(modifier = Modifier.fillMaxSize())
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                            ) {}
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(3) {
                                    Surface(
                                        color = Color.White.copy(alpha = 0.20f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp),
                                    ) {}
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.msg_no_custom_background_preview),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                BackgroundTransformSlider(
                    title = stringResource(R.string.label_zoom),
                    value = scale,
                    valueLabel = "${(previewTransform.scale * 100).roundToInt() / 100f}x",
                    valueRange = BackgroundImageTransform.MIN_SCALE..BackgroundImageTransform.MAX_SCALE,
                    startLabel = stringResource(R.string.label_full),
                    endLabel = stringResource(R.string.label_zoom_in),
                    onValueChange = { scale = it },
                )
                BackgroundTransformSlider(
                    title = stringResource(R.string.label_horizontal_position),
                    value = horizontalBias,
                    valueLabel = biasLabel(horizontalBias, negativeLabel = strBiasLeft, positiveLabel = strBiasRight, centered = strCentered),
                    valueRange = -1f..1f,
                    startLabel = stringResource(R.string.label_left),
                    endLabel = stringResource(R.string.label_right),
                    onValueChange = { horizontalBias = it },
                )
                BackgroundTransformSlider(
                    title = stringResource(R.string.label_vertical_position),
                    value = verticalBias,
                    valueLabel = biasLabel(verticalBias, negativeLabel = strBiasTop, positiveLabel = strBiasBottom, centered = strCentered),
                    valueRange = -1f..1f,
                    startLabel = stringResource(R.string.label_top),
                    endLabel = stringResource(R.string.label_bottom),
                    onValueChange = { verticalBias = it },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            scale = 1f
                            horizontalBias = 0f
                            verticalBias = 0f
                        },
                    ) {
                        Text(stringResource(R.string.action_reset))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = { onSave(previewTransform) },
                            enabled = customBackground != null,
                        ) {
                            Text(stringResource(R.string.action_apply))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundTransformSlider(
    title: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    startLabel: String,
    endLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = startLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = endLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun biasLabel(
    value: Float,
    negativeLabel: String,
    positiveLabel: String,
    centered: String,
): String {
    return when {
        abs(value) < 0.08f -> centered
        value < 0f -> negativeLabel
        else -> positiveLabel
    }
}
