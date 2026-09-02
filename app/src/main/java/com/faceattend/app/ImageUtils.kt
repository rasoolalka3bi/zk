package com.faceattend.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageUtils {

    /** يحوّل فريم من الكاميرا (YUV_420_888) إلى صورة Bitmap عادية قابلة
     * للمعالجة، مع تصحيح دوران الصورة تلقائيًا حسب اتجاه الكاميرا. */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
            val bytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            val rotation = image.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /** يقص منطقة الوجه فقط من الصورة الكاملة، بهامش بسيط حوالين المربع
     * المكتشف من ML Kit، مع حماية من تجاوز حدود الصورة. */
    fun cropFace(fullImage: Bitmap, box: Rect): Bitmap? {
        return try {
            val margin = (box.width() * 0.15f).toInt()
            val left = (box.left - margin).coerceAtLeast(0)
            val top = (box.top - margin).coerceAtLeast(0)
            val right = (box.right + margin).coerceAtMost(fullImage.width)
            val bottom = (box.bottom + margin).coerceAtMost(fullImage.height)
            val width = right - left
            val height = bottom - top
            if (width <= 0 || height <= 0) return null
            Bitmap.createBitmap(fullImage, left, top, width, height)
        } catch (e: Exception) {
            null
        }
    }
}
