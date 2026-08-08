package com.juexin.assistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.juexin.assistant.network.ScriptRepository
import com.juexin.assistant.ui.SettingsActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btn_start_service)
        val btnStop = findViewById<Button>(R.id.btn_stop_service)
        val btnSettings = findViewById<Button>(R.id.btn_settings)
        val tvStatus = findViewById<TextView>(R.id.tv_service_status)
        val tvScriptInfo = findViewById<TextView>(R.id.tv_script_info)

        updateUI(btnStart, btnStop, tvStatus)

        btnStart.setOnClickListener {
            if (!checkOverlayPermission()) {
                requestOverlayPermission()
                return@setOnClickListener
            }
            val intent = Intent(this, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "悬浮球已启动", Toast.LENGTH_SHORT).show()
            updateUI(btnStart, btnStop, tvStatus)
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, FloatingBallService::class.java)
            intent.action = FloatingBallService.ACTION_STOP
            startService(intent)
            updateUI(btnStart, btnStop, tvStatus)
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 初始化并加载话术库信息
        scope.launch {
            try {
                ReplyGenerator.init(this@MainActivity)
                val version = ScriptRepository.getCachedVersion(this@MainActivity)
                if (version > 0) {
                    tvScriptInfo.text = "云端话术库 v$version"
                } else {
                    tvScriptInfo.text = "本地话术库（点击设置同步云端）"
                }
            } catch (_: Exception) {
                tvScriptInfo.text = "本地话术库"
            }
        }
    }

    private fun updateUI(btnStart: Button, btnStop: Button, tvStatus: TextView) {
        val isRunning = FloatingBallService.getInstance() != null
        btnStart.isEnabled = !isRunning
        btnStop.isEnabled = isRunning
        tvStatus.text = if (isRunning) "● 服务运行中" else "○ 服务未启动"
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1001)
        }
    }

    override fun onResume() {
        super.onResume()
        val btnStart = findViewById<Button>(R.id.btn_start_service)
        val btnStop = findViewById<Button>(R.id.btn_stop_service)
        val tvStatus = findViewById<TextView>(R.id.tv_service_status)
        updateUI(btnStart, btnStop, tvStatus)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
