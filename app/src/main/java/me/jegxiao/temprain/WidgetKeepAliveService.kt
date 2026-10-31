package me.jegxiao.temprain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * 前台常驻服务：保证 APP 不在前台时小组件依然自动刷新。
 *  - 每分钟（对齐整分+2s）重渲染小组件 → 跨整点小时格即时换位
 *  - now 每 2 分钟 / 24h 每 10 分钟拉取（与灯板一致；通过 lastNowMs/last24hMs
 *    与 APP 前台调度自动去重，不会双倍请求）
 * 通知为最低重要性，静音、不横幅。
 */
class WidgetKeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotice()
        scheduleNextTick()
        handler.post(strobeRunnable) // 频闪灯驱动
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotice()
        scheduleNextTick()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startForegroundWithNotice() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "小组件后台刷新", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        val notif = builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("气象灯板运行中")
            .setContentText("保持桌面小组件实时更新")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    /** 频闪驱动：按亮/灭相位切换的精确时刻推送一帧（前台服务内无节流） */
    private val strobeRunnable = object : Runnable {
        override fun run() {
            var delay = 1000L
            try {
                val state = WeatherStore.load(applicationContext)
                WeatherWidgetProvider.updateAll(applicationContext, allowNetwork = false)
                delay = LedRenderer.nextStrobeSwitchDelay(state)
            } catch (_: Exception) {}
            handler.postDelayed(this, delay)
        }
    }

    private fun scheduleNextTick() {
        val now = SystemClock.elapsedRealtime()
        val next = (now / 60_000 + 1) * 60_000 + 2_000
        handler.postDelayed(tickRunnable, next - now)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            try {
                val state = WeatherStore.load(this@WidgetKeepAliveService)
                val nowMs = System.currentTimeMillis()
                if (nowMs - state.lastNowMs > 600_000L - 5_000) fetchNow()
                if (nowMs - state.last24hMs > 600_000L - 5_000) fetch24h()
                WeatherWidgetProvider.updateAll(this@WidgetKeepAliveService, allowNetwork = false)
            } catch (_: Exception) {}
            scheduleNextTick()
        }
    }

    private fun fetchNow() {
        val cityIdx = WeatherStore.load(this).cityIdx
        Thread {
            try {
                val body = QWeatherClient.get("weather/now", cityIdx, WeatherStore.getKey(this))
                val state = WeatherStore.load(this)
                QWeatherClient.parseNow(body, state)
                state.lastNowMs = System.currentTimeMillis()
                WeatherStore.save(this, state)
                WeatherWidgetProvider.updateAll(this, allowNetwork = false)
            } catch (_: Exception) {}
        }.start()
    }

    private fun fetch24h() {
        val cityIdx = WeatherStore.load(this).cityIdx
        Thread {
            try {
                val body = QWeatherClient.get("weather/24h", cityIdx, WeatherStore.getKey(this))
                val state = WeatherStore.load(this)
                QWeatherClient.parse24h(body, state)
                state.last24hMs = System.currentTimeMillis()
                WeatherStore.save(this, state)
                WeatherWidgetProvider.updateAll(this, allowNetwork = false)
            } catch (_: Exception) {}
        }.start()
    }

    companion object {
        const val CHANNEL_ID = "keepalive"
        const val NOTIF_ID = 42

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, WidgetKeepAliveService::class.java)
            )
        }
    }
}
