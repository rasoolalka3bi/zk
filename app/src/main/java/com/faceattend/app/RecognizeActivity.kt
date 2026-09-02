package com.faceattend.app

import android.graphics.Bitmap
import android.os.Bundle
import com.faceattend.app.databinding.ActivityRecognizeBinding

class RecognizeActivity : FaceCameraActivity() {

    private lateinit var binding: ActivityRecognizeBinding
    private lateinit var embedder: FaceEmbedder
    private lateinit var employeeRepo: EmployeeRepository
    private lateinit var attendanceRepo: AttendanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecognizeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindPreview(binding.previewView)

        embedder = FaceEmbedder(this)
        employeeRepo = EmployeeRepository(this)
        attendanceRepo = AttendanceRepository(this)

        if (!embedder.isReady()) {
            binding.tvStatus.text = "⚠️ موديل التعرّف غير موجود - راجع README لتحميله"
        } else if (employeeRepo.getAll().isEmpty()) {
            binding.tvStatus.text = "لا يوجد موظفون مسجّلون بعد"
        }

        requestCameraAndStart()
    }

    override fun onFaceDetected(faceBitmap: Bitmap) {
        if (!embedder.isReady()) return

        val embedding = embedder.getEmbedding(faceBitmap) ?: return
        val match = employeeRepo.findClosestMatch(embedding, embedder)

        runOnUiThread {
            if (match != null) {
                val (employee, distance) = match
                val recorded = attendanceRepo.record(employee.id, employee.name)
                binding.tvStatus.text = if (recorded) {
                    "✓ تم تسجيل حضور: ${employee.name}"
                } else {
                    "مرحبًا ${employee.name} (تم تسجيلك بالفعل مؤخرًا)"
                }
            } else {
                binding.tvStatus.text = "⚠️ وجه غير معروف - غير مسجَّل"
            }
        }
    }

    override fun onNoFaceDetected() {
        runOnUiThread {
            binding.tvStatus.text = "بانتظار وجه أمام الكاميرا..."
        }
    }

    override fun onDetectionError(e: Exception) {
        runOnUiThread {
            binding.tvStatus.text = "⚠️ تعذّر الكشف: ${e.message ?: "خطأ غير معروف"}"
        }
    }

    override fun onDestroy() {
        embedder.close()
        super.onDestroy()
    }
}
