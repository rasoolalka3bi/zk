package com.faceattend.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/** role: "user" (موظف عادي - حضور بس، مفيش دخول للإعدادات)
 *        "admin" (مسؤول - دخول كامل للقوائم والإعدادات)
 *        "super_admin" (مسؤول رئيسي - نفس صلاحيات admin حاليًا، مفصولة
 *        كتصنيف منفصل لاستخدامات مستقبلية مثل إدارة المسؤولين أنفسهم) */
data class Employee(
    val id: String,
    val name: String,
    val embedding: FloatArray,
    val role: String = "user"
) {
    fun isAdmin(): Boolean = role == "admin" || role == "super_admin"
}

/**
 * تخزين بسيط قائم على ملف JSON واحد - نفس فلسفة تطبيق إدارة أجهزة البصمة
 * تمامًا (devices.json هناك، employees.json هنا). كافٍ تمامًا لعدد معقول من
 * الموظفين (عشرات إلى بضع مئات)؛ لعدد أكبر بكثير، الأفضل الانتقال لقاعدة
 * بيانات حقيقية (Room/SQLite).
 */
class EmployeeRepository(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, "employees.json")

    private val gson = Gson()

    fun getAll(): List<Employee> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Employee>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(name: String, embedding: FloatArray, role: String = "user"): Employee {
        val employee = Employee(id = UUID.randomUUID().toString().take(8), name = name, embedding = embedding, role = role)
        val all = getAll().toMutableList()
        all.add(employee)
        save(all)
        return employee
    }

    /** هل يوجد مسؤول واحد على الأقل مسجَّل؟ يُستخدم أثناء الإعداد الأول
     * للجهاز - قبل تسجيل أي مسؤول، يُسمح بتسجيل أول مسؤول بحرية (بالضبط
     * مثل سلوك أجهزة ZK الحقيقية في وضع المصنع). */
    fun hasAnyAdmin(): Boolean = getAll().any { it.isAdmin() }

    fun delete(id: String) {
        val all = getAll().filter { it.id != id }
        save(all)
    }

    private fun save(list: List<Employee>) {
        file.writeText(gson.toJson(list))
    }

    /** يقارن مصفوفة وجه جديدة بكل الموظفين المسجّلين، ويرجّع أقرب تطابق
     * (أو null لو أقرب مسافة لسه أكبر من الحد المسموح به - يعني "شخص غير
     * معروف"). */
    fun findClosestMatch(embedding: FloatArray, embedder: FaceEmbedder): Pair<Employee, Float>? {
        val all = getAll()
        if (all.isEmpty()) return null

        var best: Employee? = null
        var bestDistance = Float.MAX_VALUE

        for (emp in all) {
            val d = embedder.distance(embedding, emp.embedding)
            if (d < bestDistance) {
                bestDistance = d
                best = emp
            }
        }

        return if (best != null && bestDistance <= FaceEmbedder.getMatchThreshold(context)) {
            best to bestDistance
        } else {
            null
        }
    }
}
