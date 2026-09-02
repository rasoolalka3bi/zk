package com.faceattend.app

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.faceattend.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة الاستعداد الرئيسية - المعادل المباشر لشاشة جهاز ZK الفعلية وهو
 * "نايم" بانتظار أحد: الكاميرا شغّالة باستمرار للتعرّف التلقائي الفوري
 * (بدون حاجة لفتح شاشة منفصلة)، مع ساعة حية، وزر قائمة (M) في الزاوية
 * يوديك لبوابة التحقق الإدارية قبل أي دخول للإعدادات.
 */
class MainActivity : FaceCameraActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var embedder: FaceEmbedder
    private lateinit var employeeRepo: EmployeeRepository
    private lateinit var attendanceRepo: AttendanceRepository

    private val clockFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ar"))
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            binding.tvClock.text = clockFormat.format(now)
            binding.tvDate.text = dateFormat.format(now)
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindPreview(binding.previewView)

        embedder = FaceEmbedder(this)
        employeeRepo = EmployeeRepository(this)
        attendanceRepo = AttendanceRepository(this)

        binding.btnMenu.setOnClickListener {
            if (employeeRepo.hasAnyAdmin()) {
                // يوجد مسؤول واحد على الأقل بالفعل - أي تسجيل جديد لازم
                // يمر عبر التحقق الإداري، بدون أي استثناء
                startActivity(android.content.Intent(this, AdminAuthActivity::class.java))
            } else {
                // لا يوجد أي مسؤول مسجَّل بعد (إعداد أول مرة للجهاز) - نسمح
                // بتسجيل أول مسؤول رئيسي مباشرة، ثم يُقفل هذا المسار تلقائيًا
                // بمجرد وجود مسؤول واحد فعليًا
                val intent = android.content.Intent(this, RegisterActivity::class.java)
                intent.putExtra(RegisterActivity.EXTRA_BOOTSTRAP, true)
                startActivity(intent)
            }
        }

        requestCameraAndStart()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
        binding.tvEmployeeCount.text = "${employeeRepo.getAll().size} موظف مسجَّل"
    }

    override fun onPause() {
        clockHandler.removeCallbacks(clockRunnable)
        super.onPause()
    }

    override fun onFaceDetected(faceBitmap: Bitmap) {
        if (!embedder.isReady()) return
        val embedding = embedder.getEmbedding(faceBitmap) ?: return
        val match = employeeRepo.findClosestMatch(embedding, embedder)

        runOnUiThread {
            if (match != null) {
                val (employee, _) = match
                val recorded = attendanceRepo.record(employee.id, employee.name)
                binding.tvStatus.text = if (recorded) {
                    "✓ أهلًا ${employee.name} - تم تسجيل الحضور"
                } else {
                    "أهلًا ${employee.name}"
                }
            } else {
                binding.tvStatus.text = "وجه غير مسجَّل"
            }
        }
    }

    override fun onNoFaceDetected() {
        runOnUiThread { binding.tvStatus.text = "" }
    }

    override fun onDestroy() {
        embedder.close()
        super.onDestroy()
    }
}
