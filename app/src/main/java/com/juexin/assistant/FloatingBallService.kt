package com.juexin.assistant

import android.app.*
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.juexin.assistant.databinding.PanelReplyBinding

class FloatingBallService : LifecycleService() {

    private var windowManager: WindowManager? = null
    private var floatingBall: View? = null
    private var replyPanel: View? = null
    private var replyPanelBinding: PanelReplyBinding? = null

    private val replyGenerator = ReplyGenerator()

    companion object {
        var isRunning = false
        private set
        const val CHANNEL_ID = "juexin_floating_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW_REPLIES = "com.juexin.SHOW_REPLIES"
        const val EXTRA_CLIPBOARD_TEXT = "clipboard_text"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingBall()
        registerClipboardReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 从剪贴板广播接收器触发时，显示回复面板
        intent?.getStringExtra(EXTRA_CLIPBOARD_TEXT)?.let { text ->
            showReplyPanel(text)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        removeFloatingBall()
        removeReplyPanel()
        unregisterReceiver(clipboardReceiver)
        super.onDestroy()
    }

    // ========== 通知 ==========

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "觉心助手悬浮服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮球在后台运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("觉心助手")
            .setContentText("悬浮球已就绪 · 长按复制消息后自动弹出回复")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ========== 剪贴板广播接收 ==========

    private val clipboardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(EXTRA_CLIPBOARD_TEXT) ?: return
            showReplyPanel(text)
        }
    }

    private fun registerClipboardReceiver() {
        val filter = IntentFilter(ACTION_SHOW_REPLIES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(clipboardReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(clipboardReceiver, filter)
        }
    }

    // ========== 悬浮球 ==========

    private fun createFloatingBall() {
        val ball = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(0xDD8B1A1A.toInt())
            setPadding(16, 16, 16, 16)
            alpha = 0.85f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        // 拖拽悬浮球
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        ball.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    params.x = initialX - dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(ball, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 点击悬浮球 → 读取剪贴板并显示回复
                        onFloatingBallClicked()
                    }
                    // 吸附边缘
                    val screenWidth = windowManager?.defaultDisplay?.width ?: 1080
                    params.x = if (params.x + ball.width / 2 > screenWidth / 2) 0 else screenWidth - ball.width
                    windowManager?.updateViewLayout(ball, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(ball, params)
        floatingBall = ball
    }

    private fun onFloatingBallClicked() {
        // 读取剪贴板内容
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank()) {
                showReplyPanel(text)
            } else {
                showToast("请先在微信中复制佛弟子的消息")
            }
        } else {
            showToast("请先在微信中复制佛弟子的消息")
        }
    }

    private fun removeFloatingBall() {
        floatingBall?.let { windowManager?.removeView(it) }
        floatingBall = null
    }

    // ========== 回复面板 ==========

    private fun showReplyPanel(clipboardText: String) {
        removeReplyPanel()

        val panel = LayoutInflater.from(this).inflate(R.layout.panel_reply, null)
        val binding = PanelReplyBinding.bind(panel)
        replyPanelBinding = binding
        replyPanel = panel

        // 显示原始消息
        binding.tvOriginalMessage.text = clipboardText

        // 加载状态
        binding.tvReply1.text = "师父正在感应..."
        binding.tvReply2.text = "师父正在感应..."
        binding.tvReply3.text = "师父正在感应..."

        // 生成3条回复
        val replies = replyGenerator.generate(clipboardText)
        binding.tvReply1.text = replies[0]
        binding.tvReply2.text = replies[1]
        binding.tvReply3.text = replies[2]

        // 点击复制
        binding.cardReply1.setOnClickListener { copyAndDismiss(replies[0]) }
        binding.cardReply2.setOnClickListener { copyAndDismiss(replies[1]) }
        binding.cardReply3.setOnClickListener { copyAndDismiss(replies[2]) }

        // 关闭按钮
        binding.btnClose.setOnClickListener { removeReplyPanel() }

        // 编辑按钮
        binding.btnEdit1.setOnClickListener { editAndCopy(replies[0]) }
        binding.btnEdit2.setOnClickListener { editAndCopy(replies[1]) }
        binding.btnEdit3.setOnClickListener { editAndCopy(replies[2]) }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }

        windowManager?.addView(panel, params)

        // 入场动画
        panel.translationY = panel.height.toFloat()
        panel.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun copyAndDismiss(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("juexin_reply", text)
        clipboard.setPrimaryClip(clip)
        showToast("已复制，回微信粘贴即可发送")
        removeReplyPanel()
    }

    private fun editAndCopy(text: String) {
        // 先复制，提示用户可编辑
        copyAndDismiss(text)
    }

    private fun removeReplyPanel() {
        replyPanel?.let {
            it.animate()
                .translationY(it.height.toFloat())
                .setDuration(200)
                .withEndAction {
                    try { windowManager?.removeView(it) } catch (_: Exception) {}
                }
                .start()
        }
        replyPanel = null
        replyPanelBinding = null
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
