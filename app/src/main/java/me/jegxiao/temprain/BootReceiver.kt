package me.jegxiao.temprain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启 + 系统时间变更响应：
 *  - BOOT_COMPLETED：启动常驻前台服务，恢复分钟级刷新
 *  - TIME_SET：手动改系统时间时立即重渲染一帧（读缓存，秒级换格）
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                WidgetKeepAliveService.start(context)
                WeatherWidgetProvider.scheduleNextTick(context)
            }
            Intent.ACTION_TIME_CHANGED -> {
                // 后台限制下不从此处启动前台服务，只做即时重渲染
                WeatherWidgetProvider.updateAll(context, allowNetwork = false)
                WeatherWidgetProvider.scheduleNextTick(context)
            }
        }
    }
}
