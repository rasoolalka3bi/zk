package com.faceattend.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.faceattend.app.databinding.ActivityUsbManagerBinding
import com.google.gson.Gson
import java.io.File

/**
 * إدارة USB - يطابق قسم "USB Manager > Download Options" في دليل الجهاز
 * الأصلي وظيفيًا: اختيار محتوى محدد للتصدير (بيانات الموظفين و/أو بيانات
 * الحضور) بدل تصدير كل شيء دائمًا، بنفس فلسفة الجهاز الحقيقي.
 */
class UsbManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsbManagerBinding
    private val gson = Gson()

    private data class BackupData(
        val employees: List<Employee>?,
        val attendance: List<AttendanceRecord>?
    )

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) performExport(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) performImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsbManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnExport.setOnClickListener {
            if (!binding.cbExportEmployees.isChecked && !binding.cbExportAttendance.isChecked) {
                Toast.makeText(this, "اختر بيانات الموظفين و/أو بيانات الحضور أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exportLauncher.launch("face_attendance_backup.json")
        }
        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    private fun performExport(uri: Uri) {
        try {
            val data = BackupData(
                employees = if (binding.cbExportEmployees.isChecked) EmployeeRepository(this).getAll() else null,
                attendance = if (binding.cbExportAttendance.isChecked) AttendanceRepository(this).getAll() else null
            )
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(gson.toJson(data).toByteArray())
            }
            Toast.makeText(this, "✓ تم تصدير النسخة الاحتياطية بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل التصدير: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun performImport(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val data = gson.fromJson(text, BackupData::class.java)

            // يستعيد فقط الفئات الموجودة فعليًا داخل الملف نفسه - لو كان
            // الملف مُصدَّرًا ببيانات الموظفين فقط مثلاً، لن يمسح سجل الحضور
            // الحالي عن طريق الخطأ بقيمة فارغة
            if (data.employees != null) {
                File(filesDir, "employees.json").writeText(gson.toJson(data.employees))
            }
            if (data.attendance != null) {
                File(filesDir, "attendance.json").writeText(gson.toJson(data.attendance))
            }

            Toast.makeText(this, "✓ تم استرجاع النسخة الاحتياطية بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل الاسترجاع: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
