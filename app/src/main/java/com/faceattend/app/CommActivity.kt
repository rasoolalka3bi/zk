package com.faceattend.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.faceattend.app.databinding.ActivityCommBinding

class CommActivity : AppCompatActivity() {

    companion object {
        const val SERVER_PORT = 8090
        // حالة مشتركة على مستوى العملية بأكملها - تبقى الخدمة تعمل في
        // الخلفية حتى لو المستخدم غادر هذه الشاشة (نفس فلسفة الخدمات
        // الأمامية في تطبيق إدارة أجهزة البصمة)
        var server: LocalApiServer? = null
    }

    private lateinit var binding: ActivityCommBinding
    private var currentIp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentIp = NetworkUtils.getLocalIpAddress() ?: "غير متصل بأي شبكة"
        binding.tvIpAddress.text = "عنوان الجهاز: $currentIp"
        binding.tvPort.text = "المنفذ: $SERVER_PORT"
        updateKeyAndEndpoints()

        binding.switchServer.isChecked = server?.isRunning() == true
        updateStatusText()

        binding.switchServer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val newServer = LocalApiServer(
                    SERVER_PORT,
                    EmployeeRepository(applicationContext),
                    AttendanceRepository(applicationContext),
                    ApiKeyManager.getKey(applicationContext)
                )
                newServer.start()
                server = newServer
            } else {
                server?.stop()
                server = null
            }
            updateStatusText()
        }

        binding.btnRegenerateKey.setOnClickListener {
            ApiKeyManager.regenerateKey(applicationContext)
            updateKeyAndEndpoints()
            // أي جهة كانت تستخدم المفتاح القديم لن تعمل بعد الآن - نوقف
            // الخادم الحالي ليعاد تشغيله بالمفتاح الجديد تلقائيًا لو المستخدم
            // شغّله تاني، بدل ما يفضل شغّال بمفتاح قديم غير متاح للعرض
            server?.stop()
            server = null
            binding.switchServer.isChecked = false
            updateStatusText()
        }
    }

    private fun updateKeyAndEndpoints() {
        val key = ApiKeyManager.getKey(applicationContext)
        binding.tvApiKey.text = "مفتاح الوصول: $key"
        binding.tvEndpoints.text =
            "نقاط السحب المتاحة (يجب إرفاق المفتاح في كل طلب):\n" +
            "http://$currentIp:$SERVER_PORT/employees?key=$key\n" +
            "http://$currentIp:$SERVER_PORT/attendance?key=$key"
    }

    private fun updateStatusText() {
        binding.tvServerStatus.text = if (server?.isRunning() == true) {
            "✓ الخادم يعمل - جاهز لاستقبال طلبات السحب"
        } else {
            "الخادم متوقف"
        }
    }
}
