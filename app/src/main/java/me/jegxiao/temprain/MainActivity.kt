package me.jegxiao.temprain

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    // 与灯板 cpp 一致的拉取间隔
    private val nowInterval = 600_000L    // 实时天气 now: 每10分钟
    private val h24Interval = 600_000L    // 24h 预报: 每10分钟

    private lateinit var state: WeatherState
    private lateinit var preview: ImageView
    private lateinit var statusText: TextView
    private lateinit var previewBmp: Bitmap
    private lateinit var testPanel: android.view.View

    private val ui = Handler(Looper.getMainLooper())
    private var animFrame = 0

    // 动画循环：100ms/帧（复刻 cpp 节奏）
    private val animLoop = object : Runnable {
        override fun run() {
            animFrame++
            drawPreview()
            ui.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 避让状态栏/灵动岛与手势条（Android 15+ 强制 edge-to-edge）
        val root = findViewById<android.view.View>(R.id.root_scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }

        state = WeatherStore.load(this)
        preview = findViewById(R.id.preview)
        statusText = findViewById(R.id.status_text)
        testPanel = findViewById(R.id.test_panel)
        previewBmp = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        preview.setImageBitmap(previewBmp)

        // 城市选择
        val rg = findViewById<RadioGroup>(R.id.city_group)
        findViewById<RadioButton>(R.id.radio_city_0).text = CITIES[0].name
        findViewById<RadioButton>(R.id.radio_city_1).text = CITIES[1].name
        rg.check(if (state.cityIdx == 0) R.id.radio_city_0 else R.id.radio_city_1)
        rg.setOnCheckedChangeListener { _, id ->
            state.cityIdx = if (id == R.id.radio_city_0) 0 else 1
            WeatherStore.save(this, state)
            fetchNow(true)
            fetch24h(true)
        }

        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            fetchNow(true); fetch24h(true)
        }

        // 测试模式（对应 cpp 的 T / M,1 命令）
        val slider = findViewById<SeekBar>(R.id.slider_test_temp)
        val sliderVal = findViewById<TextView>(R.id.text_test_temp)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                sliderVal.text = "$p °C"
                if (state.testMode) { state.testTemp = p; drawPreview() }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val rainCheck = findViewById<CheckBox>(R.id.check_rain)
        val snowCheck = findViewById<CheckBox>(R.id.check_snow)
        val applyTest = {
            state.testTemp = slider.progress
            state.testRain = rainCheck.isChecked
            state.testSnow = snowCheck.isChecked
            drawPreview()
        }
        rainCheck.setOnCheckedChangeListener { _, _ -> applyTest() }
        snowCheck.setOnCheckedChangeListener { _, _ -> applyTest() }

        findViewById<Button>(R.id.btn_test_toggle).setOnClickListener {
            state.testMode = true
            testPanel.visibility = android.view.View.VISIBLE
            findViewById<Button>(R.id.btn_test_toggle).visibility = android.view.View.GONE
            applyTest()
        }
        findViewById<Button>(R.id.btn_exit_test).setOnClickListener {
            state.testMode = false
            testPanel.visibility = android.view.View.GONE
            findViewById<Button>(R.id.btn_test_toggle).visibility = android.view.View.VISIBLE
            drawPreview()
        }

        // 调色盘
        buildColorGrid()
        findViewById<Button>(R.id.btn_reset_colors).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("恢复默认配色")
                .setMessage("将丢弃全部自定义颜色，回到灯板默认配色。确定继续？")
                .setPositiveButton("确定") { _, _ ->
                    state.colors = defaultColors()
                    WeatherStore.save(this, state)
                    buildColorGrid()
                    drawPreview()
                    WeatherWidgetProvider.updateAll(this)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 频闪灯时长三档（亮暗等长）
        val strobeGroup = findViewById<RadioGroup>(R.id.strobe_group)
        strobeGroup.check(
            when (state.strobeMs) {
                500 -> R.id.strobe_500
                800 -> R.id.strobe_800
                else -> R.id.strobe_1000
            }
        )
        strobeGroup.setOnCheckedChangeListener { _, id ->
            state.strobeMs = when (id) {
                R.id.strobe_500 -> 500
                R.id.strobe_800 -> 800
                else -> 1000
            }
            WeatherStore.save(this, state)
            drawPreview()
            WeatherWidgetProvider.updateAll(this)
        }

        drawPreview()
        ui.post(animLoop)
        WeatherWidgetProvider.scheduleNextTick(this) // 小组件分钟级自动刷新

        // 后台常驻：前台服务（开机由 BootReceiver 自启）
        WidgetKeepAliveService.start(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        // 引导用户开启 ROM 层自启动（荣耀：应用启动管理）
        findViewById<Button>(R.id.btn_battery_guide).setOnClickListener {
            try {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", packageName, null)
                    )
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "请到系统设置手动允许自启动", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== 调色盘 ====================
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildColorGrid() {
        val grid = findViewById<android.widget.LinearLayout>(R.id.color_grid)
        grid.removeAllViews()
        COLOR_NAMES.forEachIndexed { idx, name ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(android.widget.TextView(this).apply {
                text = name
                textSize = 13f
                setTextColor(0xFFC9D1D9.toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(android.view.View(this).apply {
                setBackgroundColor(state.colors[idx])
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(40), dp(28)).apply { marginStart = dp(8) }
                setOnClickListener {
                    showColorPicker(name, state.colors[idx]) { c ->
                        state.colors[idx] = c
                        WeatherStore.save(this@MainActivity, state)
                        buildColorGrid()
                        drawPreview()
                        WeatherWidgetProvider.updateAll(this@MainActivity)
                    }
                }
            })
            grid.addView(row)
        }
    }

    /** 通用取色对话框：RGB 滑条实时预览，确定后回调 */
    private fun showColorPicker(title: String, initial: Int, onConfirm: (Int) -> Unit) {
        var r = (initial shr 16) and 0xFF
        var g = (initial shr 8) and 0xFF
        var b = initial and 0xFF

        val ll = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val preview = android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        }
        ll.addView(preview)

        fun updatePreview() {
            preview.setBackgroundColor(android.graphics.Color.rgb(r, g, b))
        }
        fun channelRow(label: String, init: Int, onNew: (Int) -> Unit): android.view.View {
            val rowL = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            rowL.addView(android.widget.TextView(this).apply {
                text = label; textSize = 13f; setTextColor(0xFF8B949E.toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(20), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            val valText = android.widget.TextView(this).apply {
                text = init.toString(); textSize = 13f; setTextColor(0xFFF0883E.toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(38), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            rowL.addView(android.widget.SeekBar(this).apply {
                max = 255; progress = init
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) }
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        onNew(p); valText.text = p.toString(); updatePreview()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
            rowL.addView(valText)
            return rowL
        }
        ll.addView(channelRow("R", r) { r = it })
        ll.addView(channelRow("G", g) { g = it })
        ll.addView(channelRow("B", b) { b = it })
        updatePreview()

        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ll)
            .setPositiveButton("确定") { _, _ -> onConfirm(android.graphics.Color.rgb(r, g, b)) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        fetchNow(true)
        fetch24h(true)
    }

    override fun onStop() {
        super.onStop()
        ui.removeCallbacks(animLoop)
    }

    /** 立即拉取 now 实时天气，nextDelayMs>=0 时同时排定下一次 */
    private fun fetchNow(scheduleNext: Boolean) {
        val key = WeatherStore.DEFAULT_KEY
        Thread {
            try {
                val body = QWeatherClient.get("weather/now", state.cityIdx, key)
                QWeatherClient.parseNow(body, state)
                state.lastNowMs = System.currentTimeMillis()
                onUi { statusText.text = statusLine(); }
                persistAndSyncWidget()
            } catch (e: Exception) {
                onUi { statusText.text = statusLine() + "\n[实时] " + e.message }
            }
        }.start()
        if (scheduleNext) ui.postDelayed({ if (!isFinishing) fetchNow(true) }, nowInterval)
    }

    private fun fetch24h(scheduleNext: Boolean) {
        val key = WeatherStore.DEFAULT_KEY
        Thread {
            try {
                val body = QWeatherClient.get("weather/24h", state.cityIdx, key)
                QWeatherClient.parse24h(body, state)
                state.last24hMs = System.currentTimeMillis()
                onUi { statusText.text = statusLine() }
                persistAndSyncWidget()
            } catch (e: Exception) {
                onUi { statusText.text = statusLine() + "\n[24h] " + e.message }
            }
        }.start()
        if (scheduleNext) ui.postDelayed({ if (!isFinishing) fetch24h(true) }, h24Interval)
    }

    private fun persistAndSyncWidget() {
        WeatherStore.save(this, state)
        WeatherWidgetProvider.updateAll(this)
        onUi { drawPreview() }
    }

    private fun statusLine(): String {
        val city = CITIES[state.cityIdx].name
        return if (state.currentTemp == 999) "$city | 数据未获取"
        else "$city | ${state.currentTemp}°C ${state.nowText}" +
                " | 更新 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(state.lastNowMs))
    }

    private fun drawPreview() {
        LedRenderer.renderTo(previewBmp, state, animFrame)
        preview.invalidate()
    }

    private fun onUi(f: () -> Unit) { ui.post(f) }
}
