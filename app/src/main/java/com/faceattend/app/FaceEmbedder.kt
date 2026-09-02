package com.faceattend.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * يحوّل صورة وجه مقصوصة إلى "بصمة رقمية" (Embedding) - مصفوفة أرقام عشرية
 * (عادة 128 أو 192 رقمًا حسب الموديل) تمثّل ملامح الوجه بشكل رياضي. وجهان
 * لنفس الشخص ينتجان مصفوفتين متقاربتين جدًا رياضيًا، بينما وجهان لشخصين
 * مختلفين ينتجان مصفوفتين متباعدتين - وهذا أساس عملية "التعرّف".
 *
 * هذا هو المعادل التقني لـ"قالب البصمة" (Fingerprint Template) في جهاز ZK -
 * نفس الفكرة بالضبط، لكن للوجه بدل الإصبع.
 */
class FaceEmbedder(context: Context) {

    companion object {
        // حجم الصورة المطلوب كمدخل للموديل (يعتمد على الموديل المستخدم -
        // MobileFaceNet القياسي يتوقع 112×112). إن استخدمت موديلاً مختلفًا،
        // عدّل هذا الرقم ليطابق مواصفاته بالضبط.
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192

        private const val PREFS = "face_attend_prefs"
        private const val KEY_MATCH_THRESHOLD = "match_threshold"
        const val DEFAULT_MATCH_THRESHOLD = 1.0f

        /** حد التطابق الحالي - قابل للتعديل من شاشة "النظام" (يطابق قسم
         * "System > Fingerprint > 1:N match threshold" في دليل الجهاز
         * الأصلي وظيفيًا). قيمة أصغر = تشديد أكثر (رفض أكبر لأشخاص غير
         * مطابقين، لكن قد يرفض نفس الشخص أحيانًا)؛ قيمة أكبر = العكس. */
        fun getMatchThreshold(context: Context): Float {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_MATCH_THRESHOLD, DEFAULT_MATCH_THRESHOLD)
        }

        fun setMatchThreshold(context: Context, value: Float) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_MATCH_THRESHOLD, value).apply()
        }
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, "mobilefacenet.tflite")
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            // الموديل غير موجود بعد - راجع README لتحميله يدويًا ووضعه في
            // app/src/main/assets/mobilefacenet.tflite
            interpreter = null
        }
    }

    fun isReady(): Boolean = interpreter != null

    private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
        val fd = context.assets.openFd(assetName)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    /** يحوّل صورة وجه مقصوصة (Bitmap) إلى مصفوفة Embedding. يرجّع null لو
     * الموديل غير محمّل بعد. */
    fun getEmbedding(faceBitmap: Bitmap): FloatArray? {
        val interp = interpreter ?: return null

        val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        interp.run(inputBuffer, output)
        return normalize(output[0])
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // تطبيع قيم الألوان من [0, 255] إلى [-1, 1] - نطاق الإدخال
            // المعتاد لمعظم موديلات التعرّف على الوجوه المدرَّبة على FaceNet
            val r = (pixel shr 16 and 0xFF) / 127.5f - 1f
            val g = (pixel shr 8 and 0xFF) / 127.5f - 1f
            val b = (pixel and 0xFF) / 127.5f - 1f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        return buffer
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        for (v in vector) sumSquares += v * v
        val norm = sqrt(sumSquares).coerceAtLeast(1e-6f)
        return FloatArray(vector.size) { vector[it] / norm }
    }

    /** المسافة الإقليدية بين مصفوفتين - كلما قلّت، كلما كان الوجهان أقرب
     * لبعض. تُستخدم لمقارنة وجه جديد بكل الموظفين المسجّلين. */
    fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
