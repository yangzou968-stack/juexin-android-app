package com.juexin.assistant

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.juexin.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        binding.btnFloatingPermission.setOnClickListener {
            requestFloatingWindowPermission()
        }

        binding.btnStartService.setOnClickListener {
            if (checkFloatingPermission()) {
                startServices()
                updateStatus(true)
            } else {
                requestFloatingWindowPermission()
            }
        }

        binding.btnStopService.setOnClickListener {
            stopServices()
            updateStatus(false)
        }

        // 初始状态检查
        if (FloatingBallService.isRunning) {
            updateStatus(true)
        }
    }

    private fun checkPermissions() {
        if (!checkFloatingPermission()) {
            showPermissionDialog()
        }
    }

    private fun checkFloatingPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestFloatingWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startServices() {
        val floatingIntent = Intent(this, FloatingBallService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(floatingIntent)
        } else {
            startService(floatingIntent)
        }

        val clipboardIntent = Intent(this, ClipboardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(clipboardIntent)
        } else {
            startService(clipboardIntent)
        }
    }

    private fun stopServices() {
        stopService(Intent(this, FloatingBallService::class.java))
        stopService(Intent(this, ClipboardService::class.java))
    }

    private fun updateStatus(running: Boolean) {
        binding.tvStatus.text = if (running) "● 助手运行中" else "○ 助手已停止"
        binding.btnStartService.isEnabled = !running
        binding.btnStopService.isEnabled = running
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage(
                "觉心助手需要在微信等应用上层显示悬浮球，\n\n" +
                "这样你就能在任何应用中快速生成回复。\n\n" +
                "请点击「去授权」→ 找到「觉心助手」→ 开启允许显示在其他应用上层。"
            )
            .setPositiveButton("去授权") { _, _ -> requestFloatingWindowPermission() }
            .setNegativeButton("稍后", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (checkFloatingPermission()) {
            updateStatus(FloatingBallService.isRunning)
        }
    }
}
