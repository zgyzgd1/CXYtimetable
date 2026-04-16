package com.example.timetable.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/**
 * 二维码显示组件
 * 将文本内容生成为二维码图片并显示
 *
 * @param content 要编码为二维码的文本内容
 * @param modifier 修饰符，用于自定义布局和样式
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier) {
    // 根据内容生成二维码位图，使用 remember 避免重复生成
    val bitmap = remember(content) {
        generateQrBitmap(content, 800)
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "课程表二维码",
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f),
        )
    }
}

/**
 * 生成二维码位图
 * 使用 ZXing 库将文本内容编码为 QR Code 格式的位图
 *
 * @param content 要编码的文本内容
 * @param size 生成的位图尺寸（像素）
 * @return 生成的二维码位图
 */
private fun generateQrBitmap(content: String, size: Int): Bitmap {
    // 设置编码参数，边距设为 1
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    // 使用 MultiFormatWriter 生成二维码矩阵
    val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    // 创建空白位图
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    // 遍历矩阵，根据每个点的值设置像素颜色
    for (x in 0 until size) {
        for (y in 0 until size) {
            val color = if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb()
            bitmap.setPixel(x, y, color)
        }
    }

    return bitmap
}
