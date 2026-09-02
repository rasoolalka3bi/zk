package com.faceattend.app

import com.google.gson.Gson
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * خادم HTTP محلي مبسّط جدًا (بروتوكول خام عبر Socket، بدون أي مكتبة خارجية
 * أو أي فئة قد لا تكون مدعومة في أندرويد) - يسمح لتطبيق إدارة أجهزة البصمة
 * (أو أي أداة أخرى على نفس الشبكة) بسحب بيانات الموظفين وسجل الحضور عن بعد،
 * بنفس فلسفة السحب عن بعد في أجهزة ZK الحقيقية.
 *
 * نقطتان فقط، للقراءة فقط (GET):
 *   GET /employees  -> قائمة الموظفين (بدون بصمات الوجه نفسها لأسباب حجم
 *                       وخصوصية - الاسم والمعرّف والصلاحية فقط)
 *   GET /attendance -> سجل الحضور الكامل
 */
class LocalApiServer(
    private val port: Int,
    private val employeeRepo: EmployeeRepository,
    private val attendanceRepo: AttendanceRepository,
    private val apiKey: String
) {
    private val gson = Gson()
    private val executor = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (running.get()) return
        running.set(true)
        executor.execute {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket
                while (running.get()) {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                // توقف الخادم (طبيعي عند stop()) أو تعذّر فتح المنفذ
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
        }
    }

    fun isRunning(): Boolean = running.get()

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val input = s.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return
                val fullPath = requestLine.split(" ").getOrNull(1) ?: "/"
                val path = fullPath.substringBefore("?")
                val queryParams = parseQuery(fullPath.substringAfter("?", ""))

                // كل الطلبات تتطلب مفتاح الوصول الصحيح كمعامل استعلام
                // (?key=...) - بدون هذا التحقق، أي جهاز على نفس الشبكة كان
                // يقدر يسحب بيانات الموظفين والحضور بدون أي حماية
                if (queryParams["key"] != apiKey) {
                    respond(s, "401 Unauthorized", """{"error":"مفتاح الوصول غير صحيح أو مفقود"}""")
                    return
                }

                val (status, body) = when {
                    path.startsWith("/employees") -> {
                        val list = employeeRepo.getAll().map {
                            mapOf("id" to it.id, "name" to it.name, "role" to it.role)
                        }
                        "200 OK" to gson.toJson(list)
                    }
                    path.startsWith("/attendance") -> {
                        "200 OK" to gson.toJson(attendanceRepo.getAll())
                    }
                    else -> {
                        "404 Not Found" to """{"error":"غير موجود"}"""
                    }
                }
                respond(s, status, body)
            }
        } catch (e: Exception) {
            // تجاهل فشل عميل واحد ولا يوقف الخادم
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").mapNotNull { part ->
            val idx = part.indexOf("=")
            if (idx == -1) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
    }

    private fun respond(socket: Socket, status: String, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
        socket.getOutputStream().write(bodyBytes)
        socket.getOutputStream().flush()
    }
}
