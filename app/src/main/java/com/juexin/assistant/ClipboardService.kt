package com.juexin.assistant

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

/**
 * 剪贴板监听服务
 * 持续监控剪贴板变化，当检测到新文本时通知悬浮窗服务
 */
class ClipboardService : LifecycleService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastClipText: String = ""

    companion object {
        const val CHANNEL_ID = "juexin_clipboard_service"
        const val NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startClipboardMonitoring()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "剪贴板监听",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "监听剪贴板以快速生成回复"
                setShowBadge(false)
                // 静默通知，不打扰用户
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("觉心助手")
            .setContentText("剪贴板监听中")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun startClipboardMonitoring() {
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip ?: return@addPrimaryClipChangedListener
            if (clip.itemCount == 0) return@addPrimaryClipChangedListener

            val text = clip.getItemAt(0).text?.toString() ?: return@addPrimaryClipChangedListener
            if (text == lastClipText || text.isBlank()) return@addPrimaryClipChangedListener

            lastClipText = text

            // 简单判断是否为佛弟子消息（至少10个字符，避免误触）
            if (text.length < 8) return@addPrimaryClipChangedListener

            // 通知悬浮窗服务显示回复
            val intent = Intent(FloatingBallService.ACTION_SHOW_REPLIES).apply {
                putExtra(FloatingBallService.EXTRA_CLIPBOARD_TEXT, text)
            }
            sendBroadcast(intent)
        }
    }
}
