package com.faceattend.app

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import com.faceattend.app.databinding.ActivityAdminAuthBinding

/**
 * بوابة إجبارية قبل أي دخول للقوائم/الإعدادات - بنفس فلسفة أجهزة ZK
 * الحقيقية تمامًا: مستخدم عادي (role = "user") ممنوع من الوصول للقائمة
 * الرئيسية، والدخول مسموح فقط لمن كان دوره "admin" أو "super_admin".
 */
class AdminAuthActivity : FaceCameraActivity() {

    private lateinit var binding: ActivityAdminAuthBinding
    private lateinit var embedder: FaceEmbedder
    private lateinit var employeeRepo: EmployeeRepository
    private var isChecking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindPreview(binding.previewView)

        embedder = FaceEmbedder(this)
        employeeRepo = EmployeeRepository(this)

        binding.tvStatus.text = "امسح وجهك للتحقق من صلاحية الدخول"
        requestCameraAndStart()
    }

    override fun onFaceDetected(faceBitmap: Bitmap) {
        if (isChecking || !embedder.isReady()) return
        isChecking = true

        val embedding = embedder.getEmbedding(faceBitmap)
        if (embedding == null) {
            isChecking = false
            return
        }

        val match = employeeRepo.findClosestMatch(embedding, embedder)
        runOnUiThread {
            if (match == null) {
                binding.tvStatus.text = "⚠️ وجه غير معروف - غير مصرَّح"
                isChecking = false
                return@runOnUiThread
            }

            val (employee, _) = match
            if (employee.isAdmin()) {
                binding.tvStatus.text = "✓ مرحبًا ${employee.name} - جارِ الدخول..."
                startActivity(android.content.Intent(this, MenuActivity::class.java))
                finish()
            } else {
                binding.tvStatus.text = "⚠️ (${employee.name}) ليس لديه صلاحية دخول القوائم"
                Toast.makeText(this, "هذا المستخدم غير مصرَّح له بالدخول للإعدادات", Toast.LENGTH_LONG).show()
                isChecking = false
            }
        }
    }

    override fun onNoFaceDetected() {
        if (!isChecking) {
            runOnUiThread { binding.tvStatus.text = "امسح وجهك للتحقق من صلاحية الدخول" }
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
