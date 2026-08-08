package com.juexin.assistant.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.juexin.assistant.R
import com.juexin.assistant.network.AppConfig
import com.juexin.assistant.network.LlmClient
import com.juexin.assistant.network.ScriptRepository
import com.juexin.assistant.ReplyGenerator
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var tvScriptVersion: TextView
    private lateinit var tvLastSync: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etApiUrl = findViewById(R.id.et_api_url)
        etApiKey = findViewById(R.id.et_api_key)
        etModel = findViewById(R.id.et_model)
        tvScriptVersion = findViewById(R.id.tv_script_version)
        tvLastSync = findViewById(R.id.tv_last_sync)

        // 加载当前配置
        loadCurrentConfig()

        // 保存 LLM 配置
        findViewById<Button>(R.id.btn_save_llm).setOnClickListener {
            saveLlmConfig()
        }

        // 测试 API
        findViewById<Button>(R.id.btn_test_llm).setOnClickListener {
            testLlmConnection()
        }

        // 同步话术库
        findViewById<Button>(R.id.btn_sync_now).setOnClickListener {
            syncScripts()
        }
    }

    private fun loadCurrentConfig() {
        etApiUrl.setText(LlmClient.apiUrl)
        etApiKey.setText(LlmClient.apiKey)
        etModel.setText(LlmClient.model)

        val version = ScriptRepository.getCachedVersion(this)
        tvScriptVersion.text = if (version > 0) "v$version（云端）" else "v0（本地）"

        val lastSync = ScriptRepository.getLastSyncTime(this)
        tvLastSync.text = if (lastSync > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(lastSync))
        } else {
            "从未同步"
        }
    }

    private fun saveLlmConfig() {
        val url = etApiUrl.text.toString().trim()
        val key = etApiKey.text.toString().trim()
        val model = etModel.text.toString().trim()

        scope.launch {
            AppConfig.saveLlmConfig(this@SettingsActivity, url, key, model)
            Toast.makeText(this@SettingsActivity, "✅ 配置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testLlmConnection() {
        val url = etApiUrl.text.toString().trim()
        val key = etApiKey.text.toString().trim()

        if (key.isBlank()) {
            Toast.makeText(this, "请先填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        // 临时应用配置以测试
        LlmClient.apiUrl = url
        LlmClient.apiKey = key
        LlmClient.model = etModel.text.toString().trim()

        Toast.makeText(this, "⏳ 正在测试连接...", Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ReplyGenerator.generateReply(this@SettingsActivity, "测试")
                }
                Toast.makeText(
                    this@SettingsActivity,
                    "✅ 连接成功！来源: ${
                        when (result.source) {
                            com.juexin.assistant.ReplySource.LLM -> "AI生成"
                            com.juexin.assistant.ReplySource.REMOTE_SCRIPT -> "云端话术库"
                            com.juexin.assistant.ReplySource.LOCAL_FALLBACK -> "本地话术库"
                        }
                    }",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ 连接失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun syncScripts() {
        Toast.makeText(this, "⏳ 正在同步话术库...", Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    ReplyGenerator.forceSync(this@SettingsActivity)
                }
                if (success) {
                    val version = ReplyGenerator.getLibraryVersion()
                    tvScriptVersion.text = "v$version（云端）"
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    tvLastSync.text = sdf.format(java.util.Date())
                    Toast.makeText(this@SettingsActivity, "✅ 话术库已更新到 v$version", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "⚠️ 同步失败，使用本地话术库", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "❌ 同步出错: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
