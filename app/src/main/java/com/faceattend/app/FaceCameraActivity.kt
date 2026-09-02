package com.faceattend.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * أساس مشترك لأي شاشة تحتاج كاميرا حية + كشف وجه (التسجيل والتعرّف). يتولى
 * كل التفاصيل المتكررة (صلاحية الكاميرا، إعداد CameraX، تشغيل ML Kit على
 * كل فريم)، ويترك للشاشات الفرعية فقط قرار "ماذا أفعل بالوجه المكتشف؟" عبر
 * onFaceDetected().
 */
abstract class FaceCameraActivity : AppCompatActivity() {

    protected lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var isProcessing = false

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else onCameraPermissionDenied()
    }

    /** الشاشة الفرعية توفّر الـPreviewView بتاعتها هنا (بعد setContentView). */
    protected fun bindPreview(view: PreviewView) {
        previewView = view
    }

    protected fun requestCameraAndStart() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processFrame(imageProxy)
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                onCameraError(e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private var consecutiveFailures = 0

    private fun processFrame(imageProxy: androidx.camera.core.ImageProxy) {
        // نتجاهل الفريم الحالي لو لسه بنعالج فريم سابق - يمنع تراكم المعالجة
        // ويحافظ على سلاسة عرض الكاميرا
        if (isProcessing) {
            imageProxy.close()
            return
        }
        isProcessing = true

        val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
        val mediaImage = imageProxy.image

        if (bitmap == null || mediaImage == null) {
            imageProxy.close()
            isProcessing = false
            registerFailure("تعذّر معالجة صورة الكاميرا")
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                consecutiveFailures = 0
                if (faces.isNotEmpty()) {
                    val box: Rect = faces[0].boundingBox
                    val faceCrop = ImageUtils.cropFace(bitmap, box)
                    if (faceCrop != null) {
                        onFaceDetected(faceCrop)
                    } else {
                        onNoFaceDetected()
                    }
                } else {
                    onNoFaceDetected()
                }
            }
            .addOnFailureListener { e ->
                // هذا كان مفقودًا تمامًا سابقًا - أي فشل داخلي في مكتبة
                // الكشف (وليس فقط "لا يوجد وجه") كان يُتجاهل بصمت تام، بدون
                // أي رسالة خطأ تصل للمستخدم أو حتى تظهر في سجل الأعطال
                onDetectionError(e)
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessing = false
            }
    }

    /** يعدّ فشلًا متتاليًا (تحويل الصورة نفسها، وليس الكشف) - بعد عدد كافٍ
     * من الفشل المتكرر، نُعلم الشاشة الفرعية بمشكلة حقيقية بدل الصمت. */
    private fun registerFailure(message: String) {
        consecutiveFailures++
        if (consecutiveFailures >= 15) {
            onDetectionError(Exception(message))
            consecutiveFailures = 0
        }
    }

    /** يُستدعى في كل مرة يُكتشف فيها وجه في الفريم الحالي، مع صورة الوجه
     * مقصوصة جاهزة للاستخدام (تسجيل أو تعرّف حسب الشاشة). */
    protected abstract fun onFaceDetected(faceBitmap: Bitmap)

    /** يُستدعى عند عدم وجود أي وجه في الفريم الحالي - اختياري التنفيذ. */
    protected open fun onNoFaceDetected() {}

    protected open fun onCameraPermissionDenied() {}
    protected open fun onCameraError(e: Exception) {}

    /** يُستدعى عند فشل حقيقي في عملية الكشف نفسها (وليس مجرد عدم وجود وجه)
     * - إما فشل داخلي من مكتبة ML Kit، أو فشل متكرر في تحويل صور الكاميرا.
     * الافتراضي يطبع الخطأ فقط؛ الشاشات الفرعية يجب أن تعرضه للمستخدم. */
    protected open fun onDetectionError(e: Exception) {
        e.printStackTrace()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        detector.close()
    }
}
