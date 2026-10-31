package me.jegxiao.temprain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Calendar

/**
 * 8x8 灯板渲染器，逐行复刻 灯板显示气温降水.cpp：
 *  - 上 5 行（像素 0-39）：3x5 字体温度数字
 *  - 下 3 行（像素 40-63）：24 小时逐格降水/降雪时间轴，当前小时高亮
 *  - 数据未获取时：外圈旋转等待圈（静态单帧）
 */
object LedRenderer {

    // ==================== 1号灯频闪（颜色在调色盘第10槽，时长三档：500/800/1000ms 亮暗等长） ====================

    /** 当前时刻像素0是否处于"亮"相位 */
    fun strobeIsOn(s: WeatherState, nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs % (s.strobeMs * 2L) < s.strobeMs

    /** 距下一次亮/灭切换的毫秒数（供服务精准排程） */
    fun nextStrobeSwitchDelay(s: WeatherState, nowMs: Long = System.currentTimeMillis()): Long {
        val period = s.strobeMs * 2L
        val t = nowMs % period
        return if (t < s.strobeMs) s.strobeMs - t else period - t
    }

    // ==================== 颜色（运行时从 state.colors 取，调色盘可改） ====================
    private val K = Color.BLACK

    private val TEMP_THRESHOLD_HOT = 28
    private val TEMP_THRESHOLD_COLD = 10
    private val TEMP_THRESHOLD_FREEZING = 0

    // 3x5 紧凑数字字库（抄自 cpp font3x5）
    private val font3x5 = intArrayOf(
        0b111101101101111, // 0
        0b010010010010010, // 1
        0b111001111100111, // 2
        0b111001111001111, // 3
        0b101101111001001, // 4
        0b111100111001111, // 5
        0b111100111101111, // 6
        0b111001001001001, // 7
        0b111101111101111, // 8
        0b111101111001111  // 9
    )

    // 旋转等待圈外圈顺序（抄自 cpp outerRing）
    private val outerRing = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7,
        15, 23, 31, 39, 47, 55, 63,
        62, 61, 60, 59, 58, 57, 56,
        48, 40, 32, 24, 16, 8
    )

    /** 计算当前小时的格色数组（64 项 ARGB）。animFrame 仅在 loading 状态生效 */
    fun computeCells(s: WeatherState, animFrame: Int = 0): IntArray {
        val cells = IntArray(64)
        if (s.testMode || s.currentTemp != 999) {
            drawTemperature(s, cells)
        } else {
            drawLoading(animFrame, cells)
        }
        drawStrobe(s, cells)   // 1号灯频闪覆盖像素0（复刻 cpp drawStatusLights 的无条件覆盖）
        return cells
    }

    private fun drawStrobe(s: WeatherState, cells: IntArray) {
        cells[0] = if (strobeIsOn(s)) s.colors[ColorSlots.STROBE] else K
    }

    // ==================== 温度数字（抄自 drawTemperature） ====================
    private fun drawTemperature(s: WeatherState, cells: IntArray) {
        java.util.Arrays.fill(cells, K)
        val temp = if (s.testMode) s.testTemp else s.currentTemp
        val color = temperatureColor(s, temp)
        val isNegative = temp < 0
        val absTemp = Math.abs(temp)
        val tens = absTemp / 10
        val ones = absTemp % 10

        if (isNegative) {
            if (absTemp >= 10) {
                cells[2 * 8 + 0] = color
                cells[2 * 8 + 1] = color
                drawNumber3x5(2, 0, tens, color, cells)
                drawNumber3x5(5, 0, ones, color, cells)
            } else {
                cells[2 * 8 + 1] = color
                cells[2 * 8 + 2] = color
                drawNumber3x5(4, 0, ones, color, cells)
            }
        } else {
            if (absTemp >= 10) {
                drawNumber3x5(1, 0, tens, color, cells)
                drawNumber3x5(5, 0, ones, color, cells)
            } else {
                drawNumber3x5(3, 0, ones, color, cells)
            }
        }
        drawHourlyTimeline(s, cells)
    }

    // ==================== 变色规则（抄自 getTemperatureColor） ====================
    private fun temperatureColor(s: WeatherState, temp: Int): Int {
        val isRain: Boolean
        val isSnow: Boolean
        if (s.testMode) {
            isRain = s.testRain
            isSnow = s.testSnow
        } else {
            isRain = s.nowText.contains("雨")
            isSnow = s.nowText.contains("雪")
        }
        if (isRain) return s.colors[ColorSlots.RAINY]
        if (isSnow) return s.colors[ColorSlots.SNOWY]
        return when {
            temp >= TEMP_THRESHOLD_HOT -> s.colors[ColorSlots.TEMP_HOT]
            temp < TEMP_THRESHOLD_FREEZING -> s.colors[ColorSlots.TEMP_FREEZING]
            temp < TEMP_THRESHOLD_COLD -> s.colors[ColorSlots.TEMP_COLD]
            else -> s.colors[ColorSlots.TEMP_COMFORT]
        }
    }

    // ==================== 24h 时间轴（抄自 drawHourlyTimeline） ====================
    private fun drawHourlyTimeline(s: WeatherState, cells: IntArray) {
        val currentHour = if (s.testMode) 12
        else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        for (h in 0 until 24) {
            val pixelIdx = 40 + h
            var isRain: Boolean
            var isSnow: Boolean
            var isCurrent = (h == currentHour)

            if (s.testMode) {
                isRain = s.testRain || (h == 4) || (h == 18)
                isSnow = s.testSnow || (h == 8) || (h == 22)
                if (h == 4 || h == 8 || h == 18 || h == 22) isCurrent = false
            } else {
                isRain = s.precip[h] > 0.1f
                isSnow = s.snow[h] > 0.1f
            }

            when {
                isCurrent -> {
                    val curRain = if (s.testMode) s.testRain else s.nowText.contains("雨")
                    val curSnow = if (s.testMode) s.testSnow else s.nowText.contains("雪")
                    cells[pixelIdx] = when {
                        curRain -> s.colors[ColorSlots.CUR_G]
                        curSnow -> s.colors[ColorSlots.CUR_P]
                        else -> s.colors[ColorSlots.CUR_Y]
                    }
                }
                isRain -> cells[pixelIdx] = s.colors[ColorSlots.RAINY]
                isSnow -> cells[pixelIdx] = s.colors[ColorSlots.SNOWY]
                else -> cells[pixelIdx] = K
            }
        }
    }

    // ==================== 3x5 数字（抄自 drawNumber3x5） ====================
    private fun drawNumber3x5(startX: Int, startY: Int, num: Int, color: Int, cells: IntArray) {
        if (num < 0 || num > 9) return
        val glyph = font3x5[num]
        for (r in 0 until 5) {
            for (c in 0 until 3) {
                val bitIdx = 14 - (r * 3 + c)
                if ((glyph shr bitIdx) and 1 == 1) {
                    cells[(startY + r) * 8 + (startX + c)] = color
                }
            }
        }
    }

    // ==================== 等待圈（抄自 drawLoading） ====================
    private fun drawLoading(frame: Int, cells: IntArray) {
        java.util.Arrays.fill(cells, K)
        val i1 = ((frame % 28) + 28) % 28
        val i2 = (i1 + 1) % 28
        val i3 = (i1 + 2) % 28
        cells[outerRing[i1]] = 0xFF003264.toInt()   // (0,50,100)
        cells[outerRing[i2]] = 0xFF0064C8.toInt()   // (0,100,200)
        cells[outerRing[i3]] = 0xFF009BFF.toInt()   // (0,155,255)
    }

    // ==================== 位图绘制 ====================

    /** 在指定矩形区域内画 64 颗灯珠 */
    private fun drawCells(
        canvas: Canvas, s: WeatherState, animFrame: Int,
        left: Float, top: Float, area: Float,
        offCellColor: Int, circular: Boolean
    ) {
        val cells = computeCells(s, animFrame)
        val cs = area / 8f
        val gap = cs * 0.14f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 64) {
            paint.color = if (cells[i] == K) offCellColor else cells[i]
            val row = i / 8
            val col = i % 8
            if (circular) {
                val r = (cs - gap * 2) / 2f
                canvas.drawCircle(left + col * cs + cs / 2f, top + row * cs + cs / 2f, r, paint)
            } else {
                val l = left + col * cs + gap
                val t = top + row * cs + gap
                canvas.drawRoundRect(l, t, l + cs - gap * 2, t + cs - gap * 2, gap, gap, paint)
            }
        }
    }

    /** APP 预览：纯黑底 + 圆角方珠，复用同一张位图反复重绘 */
    fun renderTo(bitmap: Bitmap, s: WeatherState, animFrame: Int = 0) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        drawCells(canvas, s, animFrame, 0f, 0f, bitmap.width.toFloat(), Color.BLACK, false)
    }

    /**
     * 小组件：黑色半透明圆角面板画进正方形位图本身，
     * 配合 fitCenter 无论小组件拉到几乘几都显示为内切正方形。
     */
    fun renderWidget(size: Int, s: WeatherState): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val inset = size * 0.012f
        val radius = size * 0.075f
        // 圆角面板底
        paint.style = Paint.Style.FILL
        paint.color = PANEL_BG
        canvas.drawRoundRect(inset, inset, size - inset, size - inset, radius, radius, paint)
        // 面板描边
        val sw = size / 320f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sw
        paint.color = PANEL_BORDER
        val half = sw / 2f
        canvas.drawRoundRect(inset + half, inset + half, size - inset - half, size - inset - half, radius, radius, paint)
        // 灯珠区（面板内留边距），圆形灰底珠
        val pad = size * 0.05f
        drawCells(canvas, s, 0, pad, pad, size - 2 * pad, WIDGET_OFF_COLOR, true)
        return bmp
    }

    /** 小组件面板底色/描边（对应 widget_bg） */
    private val PANEL_BG = 0xE6000000.toInt()
    private val PANEL_BORDER = 0xFF30363D.toInt()

    /** 小组件灭珠底色（暗灰，露出 8x8 轮廓） */
    const val WIDGET_OFF_COLOR = 0xFF30363D.toInt()
}
