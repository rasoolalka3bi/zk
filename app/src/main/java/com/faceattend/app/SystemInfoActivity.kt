package com.faceattend.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.faceattend.app.databinding.ActivitySystemInfoBinding

/**
 * النظام - يطابق وظيفيًا قسم "System" في دليل الجهاز الأصلي: إحصائيات
 * الجهاز + إعداد حقيقي قابل للتعديل (حد التطابق، يوازي "1:N match
 * threshold" في الدليل الأصلي للبصمة).
 */
class SystemInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySystemInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshInfo()

        binding.etMatchThreshold.setText(FaceEmbedder.getMatchThreshold(this).toString())
        binding.btnSaveThreshold.setOnClickListener {
            saveThreshold()
        }
    }

    private fun refreshInfo() {
        val employees = EmployeeRepository(this).getAll()
        val attendance = AttendanceRepository(this).getAll()
        val admins = employees.count { it.isAdmin() }

        binding.tvInfo.text = buildString {
            append("عدد الموظفين المسجّلين: ${employees.size}\n")
            append("عدد المسؤولين: $admins\n")
            append("إجمالي سجلات الحضور: ${attendance.size}\n")
            append("إصدار التطبيق: 0.6")
        }
    }

    private fun saveThreshold() {
        val text = binding.etMatchThreshold.text?.toString()?.trim()
        val value = text?.toFloatOrNull()
        if (value == null || value <= 0f) {
            Toast.makeText(this, "أدخل رقمًا موجبًا صحيحًا", Toast.LENGTH_SHORT).show()
            return
        }
        FaceEmbedder.setMatchThreshold(this, value)
        Toast.makeText(this, "✓ تم حفظ حد التطابق", Toast.LENGTH_SHORT).show()
    }
}
