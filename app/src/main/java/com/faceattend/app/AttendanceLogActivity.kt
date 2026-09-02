package com.faceattend.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.faceattend.app.databinding.ActivityAttendanceLogBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * البحث في السجلات - يطابق قسم "Attendance Search" في دليل الجهاز الأصلي
 * وظيفيًا: بحث بالاسم و/أو فترة زمنية محددة (من - إلى)، وليس مجرد قائمة
 * سرد كاملة بدون فلترة كما كانت النسخة الأولى.
 */
class AttendanceLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceLogBinding
    private var allRecords: List<AttendanceRecord> = emptyList()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        allRecords = AttendanceRepository(this).getAll().reversed()

        binding.btnSearch.setOnClickListener { applyFilter() }
        binding.btnClearFilter.setOnClickListener {
            binding.etSearchName.text?.clear()
            binding.etDateFrom.text?.clear()
            binding.etDateTo.text?.clear()
            renderRecords(allRecords)
        }

        renderRecords(allRecords)
    }

    private fun applyFilter() {
        val nameQuery = binding.etSearchName.text?.toString()?.trim()?.lowercase() ?: ""
        val fromStr = binding.etDateFrom.text?.toString()?.trim()
        val toStr = binding.etDateTo.text?.toString()?.trim()

        val fromDate = if (!fromStr.isNullOrEmpty()) parseDateOnly(fromStr) else null
        val toDate = if (!toStr.isNullOrEmpty()) parseDateOnly(toStr, endOfDay = true) else null

        val filtered = allRecords.filter { record ->
            val nameMatches = nameQuery.isEmpty() || record.employeeName.lowercase().contains(nameQuery)
            val recordDate = try { dateFormat.parse(record.timestamp) } catch (e: Exception) { null }
            val afterFrom = fromDate == null || (recordDate != null && !recordDate.before(fromDate))
            val beforeTo = toDate == null || (recordDate != null && !recordDate.after(toDate))
            nameMatches && afterFrom && beforeTo
        }
        renderRecords(filtered)
    }

    private fun parseDateOnly(text: String, endOfDay: Boolean = false): java.util.Date? {
        return try {
            val date = dateOnlyFormat.parse(text) ?: return null
            if (endOfDay) java.util.Date(date.time + 24 * 60 * 60 * 1000 - 1) else date
        } catch (e: Exception) {
            null
        }
    }

    private fun renderRecords(records: List<AttendanceRecord>) {
        binding.tvResultCount.text = "${records.size} سجل"
        binding.tvLog.text = if (records.isEmpty()) {
            "لا توجد سجلات مطابقة"
        } else {
            records.joinToString("\n\n") { "${it.employeeName}\n${it.timestamp}" }
        }
    }
}
