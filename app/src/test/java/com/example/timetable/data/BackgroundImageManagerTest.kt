package com.example.timetable.data

import android.graphics.Bitmap
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BackgroundImageManagerTest {
    @Test
    fun applyExifOrientationRotatesNinetyDegrees() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)

        val rotated = BackgroundImageManager.applyExifOrientation(bitmap, ExifInterface.ORIENTATION_ROTATE_90)

        assertEquals(2, rotated.width)
        assertEquals(3, rotated.height)
    }

    @Test
    fun applyExifOrientationKeepsNormalBitmapInstance() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)

        val rotated = BackgroundImageManager.applyExifOrientation(bitmap, ExifInterface.ORIENTATION_NORMAL)

        assertSame(bitmap, rotated)
    }
}
