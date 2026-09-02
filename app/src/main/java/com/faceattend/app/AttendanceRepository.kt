package com.faceattend.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AttendanceRecord(
    val employeeId: String,
    val employeeName: String,
    val timestamp: String
)

class AttendanceRepository(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "attendance.json")

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun getAll(): List<AttendanceRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<AttendanceRecord>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** يسجّل حضور موظف الآن. لمنع تسجيل نفس الشخص مرتين خلال ثوانٍ (لو
     * وقف قدام الكاميرا لفترة)، يتجاهل التسجيل لو آخر تسجيل لنفس الموظف
     * كان قبل أقل من دقيقتين. */
    fun record(employeeId: String, employeeName: String): Boolean {
        val all = getAll().toMutableList()
        val now = Date()

        val last = all.lastOrNull { it.employeeId == employeeId }
        if (last != null) {
            try {
                val lastTime = dateFormat.parse(last.timestamp)
                if (lastTime != null && (now.time - lastTime.time) < 120_000) {
                    return false // تم تسجيله بالفعل خلال آخر دقيقتين
                }
            } catch (e: Exception) {
                // تجاهل خطأ التحليل ونكمل التسجيل
            }
        }

        all.add(AttendanceRecord(employeeId, employeeName, dateFormat.format(now)))
        file.writeText(gson.toJson(all))
        return true
    }
}
