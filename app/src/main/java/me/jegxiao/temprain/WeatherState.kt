package me.jegxiao.temprain

import android.content.Context

/** 城市（与灯板 cpp 的 CITIES 一致） */
data class City(val name: String, val lat: Float, val lon: Float)

val CITIES = listOf(
    City("哈尔滨市南岗区", 45.75f, 126.65f),
    City("大连市甘井子区", 38.98f, 121.53f)
)

/**
 * 灯板状态（对应 cpp 里的全局变量），每次数据刷新/测试切换后写回 SharedPreferences，
 * 小组件读缓存渲染。
 */
data class WeatherState(
    var currentTemp: Int = 999,      // 999 = 未获取
    var nowText: String = "未知",     // 当前天气文字（如 小雨）
    var currentRainNow: Float = 0f,
    var precip: FloatArray = FloatArray(24),  // 按实际小时(0-23)索引，>0.1 表示有雨
    var snow: FloatArray = FloatArray(24),    // 按实际小时(0-23)索引，>0.1 表示有雪
    var cityIdx: Int = 0,
    var lastNowMs: Long = 0,         // 上次实时天气拉取时间
    var last24hMs: Long = 0,
    // 调色盘（10 色，索引见 COLOR_NAMES），ARGB
    var colors: IntArray = defaultColors(),
    // 频闪灯单段时长（亮暗等长），仅 500/800/1000 三档
    var strobeMs: Int = 500,
    // 测试模式（不持久化，仅 APP 内存）
    var testMode: Boolean = false,
    var testTemp: Int = 25,
    var testRain: Boolean = false,
    var testSnow: Boolean = false
) {
    // 含 IntArray，需重写 equals/hashCode
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/** 颜色索引：前 9 项与灯板 cpp 的 C0..C8 协议顺序一致，9 为频闪灯（APP 专属） */
object ColorSlots {
    const val TEMP_HOT = 0
    const val TEMP_COMFORT = 1
    const val TEMP_COLD = 2
    const val TEMP_FREEZING = 3
    const val RAINY = 4
    const val SNOWY = 5
    const val CUR_Y = 6
    const val CUR_G = 7
    const val CUR_P = 8
    const val STROBE = 9
}

/** 调色盘名称（顺序即存储顺序） */
val COLOR_NAMES = listOf(
    "高温 (≥28°C)", "舒适温度", "寒冷 (<10°C)", "极寒 (<0°C)",
    "下雨", "下雪", "当前小时(无雨雪)", "当前小时(有雨)", "当前小时(有雪)", "频闪灯"
)

/** 默认调色盘（全满色值：通道取 0 / 80 / FF） */
fun defaultColors() = intArrayOf(
    0xFFFF0000.toInt(), // 高温 -> 红
    0xFF00FF00.toInt(), // 舒适 -> 绿
    0xFF00FFFF.toInt(), // 寒冷 -> 青
    0xFFFF00FF.toInt(), // 极寒 -> 紫
    0xFF0000FF.toInt(), // 下雨 -> 蓝
    0xFFFFFFFF.toInt(), // 下雪 -> 白
    0xFFFF8000.toInt(), // 当前小时无雨雪 -> 橙（全红半绿）
    0xFF00FF00.toInt(), // 当前小时有雨 -> 绿
    0xFFFFFF00.toInt(), // 当前小时有雪 -> 黄
    0xFFFFFFFF.toInt()  // 频闪灯 -> 白
)

object WeatherStore {
    private const val SP = "weather"

    fun save(ctx: Context, s: WeatherState) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().apply {
            putInt("temp", s.currentTemp)
            putString("text", s.nowText)
            putFloat("rainNow", s.currentRainNow)
            putString("precip", s.precip.joinToString(",") { if (it > 0.1f) "1" else "0" })
            putString("snow", s.snow.joinToString(",") { if (it > 0.1f) "1" else "0" })
            putInt("cityIdx", s.cityIdx)
            putLong("lastNow", s.lastNowMs)
            putLong("last24h", s.last24hMs)
            putString("colors", s.colors.joinToString(",") { it.toLong().toString() })
            putInt("strobeMs", s.strobeMs)
            apply()
        }
    }

    fun load(ctx: Context): WeatherState {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val s = WeatherState()
        s.currentTemp = sp.getInt("temp", 999)
        s.nowText = sp.getString("text", "未知") ?: "未知"
        s.currentRainNow = sp.getFloat("rainNow", 0f)
        val p = sp.getString("precip", null)?.split(",")
        val n = sp.getString("snow", null)?.split(",")
        p?.forEachIndexed { i, v -> if (i < 24 && v == "1") s.precip[i] = 1f }
        n?.forEachIndexed { i, v -> if (i < 24 && v == "1") s.snow[i] = 1f }
        s.cityIdx = sp.getInt("cityIdx", 0).coerceIn(0, CITIES.size - 1)
        s.lastNowMs = sp.getLong("lastNow", 0)
        s.last24hMs = sp.getLong("last24h", 0)
        val c = sp.getString("colors", null)?.split(",")
        if (c != null && (c.size == 9 || c.size == 10)) {
            // 兼容旧 9 色缓存：已有槽位填入，缺省槽（频闪灯）保持默认
            c.forEachIndexed { i, v -> if (i < 10) s.colors[i] = v.toLong().toInt() }
        }
        s.strobeMs = when (sp.getInt("strobeMs", s.strobeMs)) {
            500, 800, 1000 -> sp.getInt("strobeMs", s.strobeMs)
            else -> 500
        }
        return s
    }

    /** 编译期注入的 Key（来自 local.properties → BuildConfig），作为输入框预填与兜底 */
    val BUILD_KEY: String get() = BuildConfig.QWEATHER_KEY

    /** 生效 Key：APP 内保存的优先，其次编译期注入的（可能为空） */
    fun getKey(ctx: Context): String =
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .getString("qkey", null)?.takeIf { it.isNotBlank() } ?: BUILD_KEY

    /** APP 内保存 Key；传空串清除，回退到编译期注入的 Key */
    fun setKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
            .putString("qkey", key.trim())
            .apply()
    }
}
