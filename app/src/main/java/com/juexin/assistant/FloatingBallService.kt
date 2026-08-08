package com.juexin.assistant

import android.app.*
import android.content.*
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.juexin.assistant.network.ScriptRepository
import kotlinx.coroutines.*

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var inputPanel: View? = null
    private var resultPanel: View? = null

    // UI 组件
    private var etInput: EditText? = null
    private var tvCompassion: TextView? = null
    private var tvKarma: TextView? = null
    private var tvAction: TextView? = null
    private var tvSource: TextView? = null
    private var tvStatus: TextView? = null
    private var copyActionBtn: Button? = null
    private var copyCompassionBtn: Button? = null
    private var copyKarmaBtn: Button? = null

    private var savedCompassion: String = ""
    private var savedKarma: String = ""
    private var savedAction: String = ""

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var generateJob: Job? = null
    private lateinit var clipboardReceiver: BroadcastReceiver

    companion object {
        const val CHANNEL_ID = "floating_ball_channel"
        const val NOTIFICATION_ID = 1
        const val ERROR_NOTIFICATION_ID = 999
        const val ACTION_STOP = "com.juexin.assistant.STOP"
        const val ACTION_SHOW_REPLIES = "com.juexin.assistant.SHOW_REPLIES"
        const val EXTRA_CLIPBOARD_TEXT = "clipboard_text"
        val TAG = FloatingBallService::class.java.simpleName

        private var instance: FloatingBallService? = null
        fun getInstance(): FloatingBallService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 初始化 ReplyGenerator + 后台同步话术库
        serviceScope.launch {
            ReplyGenerator.init(this@FloatingBallService)
            // 静默同步远程话术库
            ScriptRepository.syncInBackground(
                this@FloatingBallService,
                serviceScope
            )
        }

        // 注册剪贴板广播接收器
        clipboardReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra(EXTRA_CLIPBOARD_TEXT) ?: return
                showInputPanelWithText(text)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                clipboardReceiver,
                IntentFilter(ACTION_SHOW_REPLIES),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                clipboardReceiver,
                IntentFilter(ACTION_SHOW_REPLIES)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        showFloatingBall()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        removeAllViews()
        try { unregisterReceiver(clipboardReceiver) } catch (_: Exception) {}
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ERROR_NOTIFICATION_ID)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    // ==================== 悬浮球 ====================

    private fun showFloatingBall() {
        removeAllViews()

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null).apply {
            findViewById<ImageView>(R.id.iv_icon).setOnClickListener {
                showInputPanel()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 300
        }

        windowManager.addView(floatingView, params)

        // 拖拽
        floatingView?.findViewById<ImageView>(R.id.iv_icon)?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (windowManager.defaultDisplay.width - event.rawX).toInt()
                    params.y = event.rawY.toInt() - 100
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(floatingView, params)
                    if (Math.abs(event.rawX - (event.rawX)) < 5 &&
                        Math.abs(event.rawY - (event.rawY)) < 5
                    ) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ==================== 输入面板 ====================

    private fun showInputPanel() {
        try {
            inputPanel?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
            inputPanel = null
            resultPanel?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
            resultPanel = null

            // 悬浮球变暗，表示已按下
            floatingView?.findViewById<ImageView>(R.id.iv_icon)?.alpha = 0.4f

            inputPanel = LayoutInflater.from(this).inflate(R.layout.panel_input, null).apply {
                etInput = findViewById(R.id.et_input)
                findViewById<Button>(R.id.btn_generate).setOnClickListener { onGenerate() }
                findViewById<Button>(R.id.btn_paste).setOnClickListener { onPaste() }
                findViewById<ImageView>(R.id.iv_close).setOnClickListener { closeInputPanel() }
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                this.type = type
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.BOTTOM
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            windowManager.addView(inputPanel, params)

            // 不自动弹键盘，由用户点击输入框触发
            etInput?.postDelayed({
                etInput?.requestFocus()
            }, 200)
        } catch (e: Exception) {
            Log.e(TAG, "面板打开失败", e)
            floatingView?.findViewById<ImageView>(R.id.iv_icon)?.alpha = 1.0f
            inputPanel = null
            // Toast 在 Service 中可能不显示，用 Notification 替代
            showErrorNotification("面板打开失败，请检查悬浮窗权限")
        }
    }

    /**
     * 从剪贴板监听到文本后，自动弹出输入面板并预填内容
     */
    private fun showInputPanelWithText(text: String) {
        showInputPanel()
        etInput?.post { etInput?.setText(text) }
        // 自动触发生成
        etInput?.postDelayed({ onGenerate() }, 500)
    }

    private fun onPaste() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            etInput?.setText(clip.getItemAt(0).text)
        }
    }

    private fun onGenerate() {
        val userMessage = etInput?.text?.toString()?.trim() ?: ""
        if (userMessage.isEmpty()) {
            Toast.makeText(this, "请输入信众的对话内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 取消之前的任务
        generateJob?.cancel()

        // 显示加载状态
        tvStatus?.text = "⏳ 正在生成回复..."

        generateJob = serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ReplyGenerator.generateReply(this@FloatingBallService, userMessage)
                }

                savedCompassion = result.compassion
                savedKarma = result.karma
                savedAction = result.action

                withContext(Dispatchers.Main) {
                    showResultPanel(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus?.text = "❌ 生成失败: ${e.message}"
                }
            }
        }
    }

    // ==================== 结果面板 ====================

    private fun showResultPanel(result: ReplyResult) {
        inputPanel?.let { windowManager.removeView(it) }
        inputPanel = null
        resultPanel?.let { windowManager.removeView(it) }

        resultPanel = LayoutInflater.from(this).inflate(R.layout.panel_reply, null).apply {
            tvCompassion = findViewById(R.id.tv_compassion)
            tvKarma = findViewById(R.id.tv_karma)
            tvAction = findViewById(R.id.tv_action)
            tvSource = findViewById(R.id.tv_source)
            tvStatus = findViewById(R.id.tv_status)
            copyActionBtn = findViewById(R.id.btn_copy_action)
            copyCompassionBtn = findViewById(R.id.btn_copy_compassion)
            copyKarmaBtn = findViewById(R.id.btn_copy_karma)

            findViewById<ImageView>(R.id.iv_close).setOnClickListener { closeResultPanel() }
            findViewById<Button>(R.id.btn_new).setOnClickListener { showInputPanel() }

            tvCompassion?.text = result.compassion
            tvKarma?.text = result.karma
            tvAction?.text = result.action

            // 显示来源
            when (result.source) {
                ReplySource.REMOTE_SCRIPT -> tvSource?.text = "📡 云端话术库 (v${ReplyGenerator.getLibraryVersion()})"
                ReplySource.LLM -> tvSource?.text = "🤖 AI 智能生成"
                ReplySource.LOCAL_FALLBACK -> tvSource?.text = "📋 本地话术库"
            }

            tvStatus?.text = "✅ 生成完成"

            // 复制按钮
            copyCompassionBtn?.setOnClickListener {
                copyToClipboard(result.compassion)
                Toast.makeText(this@FloatingBallService, "已复制【悲悯共情】", Toast.LENGTH_SHORT).show()
            }
            copyKarmaBtn?.setOnClickListener {
                copyToClipboard(result.karma)
                Toast.makeText(this@FloatingBallService, "已复制【因果开示】", Toast.LENGTH_SHORT).show()
            }
            copyActionBtn?.setOnClickListener {
                copyToClipboard(result.action)
                Toast.makeText(this@FloatingBallService, "已复制【法药指引】", Toast.LENGTH_SHORT).show()
            }

            // 长按复制全文
            tvCompassion?.setOnLongClickListener {
                val full = "${result.compassion}\n\n${result.karma}\n\n${result.action}"
                copyToClipboard(full)
                Toast.makeText(this@FloatingBallService, "已复制全文", Toast.LENGTH_SHORT).show()
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        windowManager.addView(resultPanel, params)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("juexin", text))
    }

    private fun closeInputPanel() {
        inputPanel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        inputPanel = null
        floatingView?.findViewById<ImageView>(R.id.iv_icon)?.alpha = 1.0f
    }

    private fun showErrorNotification(msg: String) {
        try {
            val channelId = "error_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "错误通知", NotificationManager.IMPORTANCE_HIGH)
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
            val n = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("觉心助手")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(ERROR_NOTIFICATION_ID, n)
        } catch (_: Exception) {}
    }

    private fun closeResultPanel() {
        resultPanel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        resultPanel = null
    }

    private fun removeAllViews() {
        floatingView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        inputPanel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        resultPanel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        floatingView = null; inputPanel = null; resultPanel = null
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "觉心佛法助手",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮球服务运行中"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingBallService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("觉心佛法助手")
            .setContentText("点击悬浮球开始对话")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .build()
    }
}
