package com.faceattend.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.faceattend.app.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUserMgmt.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.btnComm.setOnClickListener {
            startActivity(Intent(this, CommActivity::class.java))
        }
        binding.btnSystem.setOnClickListener {
            startActivity(Intent(this, SystemInfoActivity::class.java))
        }
        binding.btnUsbManager.setOnClickListener {
            startActivity(Intent(this, UsbManagerActivity::class.java))
        }
        binding.btnAttendanceSearch.setOnClickListener {
            startActivity(Intent(this, AttendanceLogActivity::class.java))
        }
        binding.btnSystemInfo.setOnClickListener {
            startActivity(Intent(this, SystemInfoActivity::class.java))
        }
    }
}
