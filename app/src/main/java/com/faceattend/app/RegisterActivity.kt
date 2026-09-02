package com.faceattend.app

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import com.faceattend.app.databinding.ActivityRegisterBinding

class RegisterActivity : FaceCameraActivity() {

    companion object {
        const val EXTRA_BOOTSTRAP = "bootstrap"
    }

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var embedder: FaceEmbedder
    private var lastFaceBitmap: Bitmap? = null
    private var isBootstrap = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindPreview(binding.previewView)

        // وضع "أول تسجيل" - لا يوجد أي مسؤول بعد، فنقفل اختيار الصلاحية
        // ونسجّل هذا الشخص كمسؤول رئيسي تلقائيًا. أي دخول لاحق لهذه الشاشة
        // (بدون هذا الوضع) لازم يكون قد مرّ بالفعل عبر التحقق الإداري.
        isBootstrap = intent.getBooleanExtra(EXTRA_BOOTSTRAP, false)
        if (isBootstrap) {
            binding.radioGroupRole.visibility = android.view.View.GONE
            binding.tvBootstrapNotice.visibility = android.view.View.VISIBLE
        }

        embedder = FaceEmbedder(this)
        if (!embedder.isReady()) {
            binding.tvStatus.text = "⚠️ موديل التعرّف غير موجود - راجع README لتحميله"
        }

        binding.btnCapture.setOnClickListener {
            saveEmployee()
        }

        requestCameraAndStart()
    }

    override fun onFaceDetected(faceBitmap: Bitmap) {
        lastFaceBitmap = faceBitmap
        runOnUiThread {
            if (embedder.isReady()) {
                binding.tvStatus.text = "تم رصد وجه - اكتب الاسم واضغط حفظ"
                binding.btnCapture.isEnabled = true
            }
        }
    }

    override fun onNoFaceDetected() {
        lastFaceBitmap = null
        runOnUiThread {
            binding.tvStatus.text = "قرّب وجهك من الكاميرا..."
            binding.btnCapture.isEnabled = false
        }
    }

    override fun onDetectionError(e: Exception) {
        runOnUiThread {
            binding.tvStatus.text = "⚠️ تعذّر الكشف: ${e.message ?: "خطأ غير معروف"}"
            binding.btnCapture.isEnabled = false
        }
    }

    private fun saveEmployee() {
        val name = binding.etName.text?.toString()?.trim()
        if (name.isNullOrEmpty()) {
            Toast.makeText(this, "أدخل اسم الموظف أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val face = lastFaceBitmap
        if (face == null) {
            Toast.makeText(this, "لم يتم رصد وجه بعد", Toast.LENGTH_SHORT).show()
            return
        }

        val embedding = embedder.getEmbedding(face)
        if (embedding == null) {
            Toast.makeText(this, "تعذّر استخراج بصمة الوجه - تأكد من وجود ملف الموديل", Toast.LENGTH_LONG).show()
            return
        }

        // في وضع "أول تسجيل" الصلاحية مقفولة على مسؤول رئيسي بغض النظر عن
        // أي شيء آخر - لا يوجد خيار آخر ظاهر للمستخدم أصلاً في هذا الوضع
        val role = if (isBootstrap) {
            "super_admin"
        } else {
            when (binding.radioGroupRole.checkedRadioButtonId) {
                binding.radioAdmin.id -> "admin"
                binding.radioSuperAdmin.id -> "super_admin"
                else -> "user"
            }
        }

        EmployeeRepository(this).add(name, embedding, role)
        Toast.makeText(this, "✓ تم تسجيل ($name) بنجاح", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        embedder.close()
        super.onDestroy()
    }
}
