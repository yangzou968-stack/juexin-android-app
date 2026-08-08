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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.view.animation.OvershootInterpolator
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
    private val handler = Handler(Looper.getMainLooper())
    private var clipboardManager: ClipboardManager? = null
    private var lastClipText = ""

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
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        createFloatingBall()
        registerClipboardReceiver()
        // 启动剪贴板轮询（Android 10+ 后台无法用 listener，改用轮询）
        handler.post(object : Runnable {
            override fun run() {
                checkClipboard()
                handler.postDelayed(this, 1500)
            }
        })
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
        handler.removeCallbacksAndMessages(null)
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
            .setContentText("复制消息后点悬浮球生成回复")
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

    private fun checkClipboard() {
        try {
            val clip = clipboardManager?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text == lastClipText || text.isBlank()) return
            if (text.length < 4) return  // 至少4字
            lastClipText = text
            showReplyPanel(text)
        } catch (_: Exception) {}
    }

    private fun readClipboardText(): String? {
        return try {
            clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (_: Exception) { null }
    }

    // ========== 悬浮球 ==========

    private fun createFloatingBall() {
        val ball = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(0xDD8B1A1A.toInt())
            setPadding(20, 20, 20, 20)
            alpha = 0.85f
        }

        val screenWidth = windowManager?.defaultDisplay?.width ?: 1080
        val screenHeight = windowManager?.defaultDisplay?.height ?: 1920
        val ballSize = dpToPx(48)

        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 使用 TOP|START 绝对定位，不再用 END 导致坐标方向混乱
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - ballSize - dpToPx(8)
            y = screenHeight / 3
        }

        var downTime = 0L
        var downRawX = 0f
        var downRawY = 0f
        var startX = params.x
        var startY = params.y
        var hasMoved = false

        ball.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        hasMoved = true
                    }
                    if (hasMoved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager?.updateViewLayout(ball, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - downTime
                    if (!hasMoved && duration < 400) {
                        // 点击：读剪贴板显示回复
                        onFloatingBallClicked()
                    }
                    if (hasMoved) {
                        // 拖拽结束：吸附到最近边缘
                        snapToEdge(ball, params, screenWidth, screenHeight)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(ball, params)
        floatingBall = ball
    }

    private fun snapToEdge(
        ball: View,
        params: WindowManager.LayoutParams,
        screenWidth: Int,
        screenHeight: Int
    ) {
        val centerX = params.x + ball.width / 2
        val targetX = if (centerX > screenWidth / 2) {
            screenWidth - ball.width - dpToPx(8)
        } else {
            dpToPx(8)
        }
        params.x = targetX
        params.y = params.y.coerceIn(dpToPx(40), screenHeight - ball.height - dpToPx(40))
        windowManager?.updateViewLayout(ball, params)
    }

    private fun onFloatingBallClicked() {
        val text = readClipboardText()
        if (!text.isNullOrBlank() && text.length >= 2) {
            showReplyPanel(text)
        } else {
            showToast("请先在微信中长按复制佛弟子的消息，再点悬浮球")
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
        panel.translationY = 600f
        panel.animate()
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(0.5f))
            .start()
    }

    private fun copyAndDismiss(text: String) {
        try {
            val clip = ClipData.newPlainText("juexin_reply", text)
            clipboardManager?.setPrimaryClip(clip)
            showToast("已复制，回微信粘贴即可发送")
        } catch (e: Exception) {
            showToast("复制失败，请重试")
        }
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
