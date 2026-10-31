package me.jegxiao.temprain

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.RemoteViews

/**
 * 桌面小组件：渲染与灯板一致的 8x8 位图（温度数字 + 24h 降水时间轴）。
 *
 * 刷新机制（三层）：
 *  1. APP 前台拉数据成功后实时推送（now 2min / 24h 10min，与灯板一致）；
 *  2. AlarmManager 对齐整分自动重渲染（无网络，只重画缓存）——保证跨分钟/跨整点
 *     时当前小时格第一时间换位；灭屏期间闹钟不触发，亮屏后立刻补刷一帧；
 *  3. updatePeriodMillis 30 分钟系统兜底。
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        for (id in ids) renderWidget(context, awm, id)
        scheduleNextTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // 处理系统 APPWIDGET_UPDATE
        if (intent.action == ACTION_AUTO_REFRESH) {
            // 只重渲染缓存，不碰网络（轻量，分钟级安全）
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            for (id in ids) renderWidget(context, awm, id, allowNetwork = false)
            scheduleNextTick(context) // 续订下一分钟
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // 最后一个小组件被移除时停止闹钟
        val awm = AppWidgetManager.getInstance(context)
        val remaining = awm.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
        if (remaining.isEmpty()) cancelTick(context)
    }

    companion object {
        const val ACTION_AUTO_REFRESH = "me.jegxiao.temprain.ACTION_AUTO_REFRESH"
        private const val REQUEST_CODE = 1001

        fun renderWidget(
            context: Context, awm: AppWidgetManager, appWidgetId: Int,
            allowNetwork: Boolean = true
        ) {
            val state = WeatherStore.load(context)
            // 缓存超过 30 分钟且配置了 key：后台线程尝试同步拉一次实时数据
            val stale = System.currentTimeMillis() - state.lastNowMs > 30 * 60_000
            if (allowNetwork && stale && WeatherStore.getKey(context).isNotBlank()) {
                try {
                    val t = Thread {
                        try {
                            val body = QWeatherClient.get("weather/now", state.cityIdx, WeatherStore.getKey(context))
                            QWeatherClient.parseNow(body, state)
                            state.lastNowMs = System.currentTimeMillis()
                            WeatherStore.save(context, state)
                        } catch (_: Exception) {}
                    }
                    t.start(); t.join(8000)
                } catch (_: Exception) {}
            }

            val bmp: Bitmap = LedRenderer.renderWidget(320, state)
            val views = RemoteViews(context.packageName, R.layout.widget_weather).apply {
                setImageViewBitmap(R.id.widget_image, bmp)
                setOnClickPendingIntent(R.id.widget_root, mainActivityIntent(context))
            }
            awm.updateAppWidget(appWidgetId, views)
        }

        private fun mainActivityIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** APP 拉到新数据后调用：刷新桌面上所有小组件实例 */
        fun updateAll(context: Context, allowNetwork: Boolean = true) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            for (id in ids) renderWidget(context, awm, id, allowNetwork)
        }

        private fun tickPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, WeatherWidgetProvider::class.java).setAction(ACTION_AUTO_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** 安排下一个整分（+2s 余量）的自动刷新闹钟。非唤醒：灭屏不触发，亮屏补刷 */
        fun scheduleNextTick(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = SystemClock.elapsedRealtime()
            val next = (now / 60_000 + 1) * 60_000 + 2_000
            val canExact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            try {
                if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, next, tickPendingIntent(context))
                else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, next, tickPendingIntent(context))
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, next, tickPendingIntent(context))
            }
        }

        fun cancelTick(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(tickPendingIntent(context))
        }
    }
}
