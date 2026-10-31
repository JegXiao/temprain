# TempRain · 气温降水灯板

> 在手机屏幕和桌面小组件上复刻 WS2812 8×8 灯板天气显示的安卓应用：点阵温度数字 + 24 小时降水时间轴，配色、闪烁、刷新节奏与硬件灯板固件保持一致。

**中文** · [ English](#english)

---

## 功能特性

- **8×8 灯板复刻**：上 5 行用 3×5 点阵字体显示温度（支持负数），下 3 行为 24 小时逐小时降水/降雪时间轴，当前小时格高亮
- **变色规则与灯板固件一致**：当前下雨→蓝色，下雪→白色，否则按温度分档（≥28°C 红 / 0~10°C 青 / <0°C 紫 / 其余绿）；当前小时格按天气显示橙/绿/黄
- **桌面小组件**：黑色圆角面板 + 圆形灯珠，最小 1×1、可任意拉伸，任何形状下保持正方形；点击打开 APP
- **跨整点即时换格**：前台服务每分钟对齐整分重绘小组件，小时高亮格在整点第一时间切换；手动修改系统时间立即响应
- **调色盘**：10 种颜色实时可调（9 种天气状态色 + 频闪灯色），保存后 APP 预览与小组件同步生效，支持恢复默认
- **频闪灯**：左上角像素按固定节奏亮灭交替，三档时长可选（500/800/1000ms，亮暗等长）
- **测试模式**：温度滑块（-40~40°C）+ 雨/雪复选框，随时模拟任意天气组合查看配色效果
- **后台常驻**：前台服务（常驻通知，最低重要性静音）在 APP 退到后台后继续刷新小组件与拉取数据；开机自启
- **刷新频率**：实时天气与 24h 预报均每 10 分钟一次（与配套的 ESP32 灯板固件完全一致，每日约 288 次请求）

## 快速开始

1. 用 Android Studio 打开本项目（AGP 9.x，JDK 17+，minSdk 24 / Android 7.0+）
2. 在项目根目录的 `local.properties` 中加一行（文件不存在就新建，该文件已被 git 忽略）：

   ```properties
   QWEATHER_KEY=你的和风天气Key
   ```

3. Sync 后直接 Run 到手机即可

Key 的申请方法见下方[配置](#配置)。

## 配置

| 配置项 | 所在文件 | 说明 |
|---|---|---|
| `QWEATHER_KEY` | `local.properties`（项目根目录） | 和风天气 API Key，编译期经 `BuildConfig` 注入，源码与 git 仓库中均不出现 |

## 说明

### 获取和风天气 Key（需自行注册）

1. 到 [和风天气开发者控制台](https://console.qweather.com) 注册账号，个人开发者选**免费开发订阅**即可
2. 控制台 → 项目管理 → 创建项目 → 为项目添加 **Web API** 凭据，得到一串 32 位 Key
3. 把 Key 写进 `local.properties` 的 `QWEATHER_KEY=`，然后正常编译
4. 没有 Key 时 APP 可以编译安装，但无法拉取数据（界面会持续显示等待动画）

本应用请求的是 `devapi.qweather.com` 的 `weather/now` 与 `weather/24h` 两个接口，每 10 分钟各一次，免费订阅的额度足够日常使用。

### 修改城市（目前内置两个地区）

城市列表硬编码在 `app/src/main/java/me/jegxiao/temprain/WeatherState.kt`：

```kotlin
val CITIES = listOf(
    City("哈尔滨市南岗区", 45.75f, 126.65f),   // 名称, 纬度, 经度
    City("大连市甘井子区", 38.98f, 121.53f)
)
```

- 经纬度精确到小数点后两位即可；代码里已按和风要求的 `经度,纬度` 顺序拼接参数，只需按"纬度在前"的顺序填写
- 界面上的城市单选按钮是写死的两个，**增删城市还需同步改两处**：
  1. `app/src/main/res/layout/activity_main.xml` —— `city_group` 里的 `RadioButton`（每城市一个，id 依次递增）
  2. `MainActivity.kt` —— 城市切换绑定（`rg.check(...)` 与 `setOnCheckedChangeListener` 里的 id→索引映射）
- 切换城市后会立即重新拉取两种数据，无需等待下一个刷新周期

### 小组件与后台

- 添加：长按桌面空白处 → 小组件 → TempRain，拖到桌面；建议在系统设置里允许本应用「精确闹钟」（Android 13+ 默认关闭）
- 国产 ROM（荣耀/华为等）需在「应用启动管理」中允许自启动与后台活动，APP 内的「后台保活设置」按钮可直接跳转应用详情
- 常驻通知是前台服务的机制要求，无法隐藏；通知渠道已设为最低重要性，不会发声

### 安全边界

- API Key 只存在于你本机的 `local.properties`，编译后进入你自用的 APK；请勿把含 Key 的 APK 分发给他人（APK 可被反编译读出 Key）
- 本仓库的 git 历史经过排查，任何提交中都不含密钥

---

# English

> An Android app that recreates a WS2812 8×8 LED-matrix weather display on your phone screen and home-screen widget: dot-matrix temperature digits plus a 24-hour precipitation timeline, matching the hardware board firmware in colors, strobe and refresh rhythm.

## Features

- **8×8 board recreation**: top 5 rows show temperature with a 3×5 dot-matrix font (negative numbers supported), bottom 3 rows show a 24-hour hourly rain/snow timeline with the current hour highlighted
- **Color rules identical to the firmware**: blue while raining, white while snowing, otherwise temperature bands (≥28°C red / 0–10°C cyan / <0°C purple / otherwise green); the current-hour cell shows orange/green/yellow according to weather
- **Home-screen widget**: black rounded panel with round LEDs, resizable from 1×1 upward, always square at any size; tap to open the app
- **Instant hour rollover**: a foreground service redraws the widget aligned to every minute, so the highlighted hour cell switches right on the hour; manually changing the system time triggers an immediate redraw
- **Palette**: 10 live-adjustable colors (9 weather-state colors + strobe color), synced between the in-app preview and the widget, with a reset-to-default button
- **Strobe LED**: the top-left pixel blinks on/off at a selectable rate (500/800/1000 ms, equal on/off duration)
- **Test mode**: temperature slider (−40~40°C) plus rain/snow checkboxes to preview any weather combination
- **Background persistence**: a foreground service (silent, lowest-importance notification) keeps the widget updated after leaving the app; auto-start on boot
- **Refresh rate**: both `weather/now` and `weather/24h` are polled every 10 minutes, identical to the companion ESP32 board firmware (~288 requests/day)

## Quick Start

1. Open the project in Android Studio (AGP 9.x, JDK 17+, minSdk 24 / Android 7.0+)
2. Add one line to `local.properties` in the project root (create it if missing; the file is git-ignored):

   ```properties
   QWEATHER_KEY=your_qweather_key
   ```

3. Sync and Run on your phone

See [Configuration](#configuration) for how to obtain a key.

## Configuration

| Item | File | Description |
|---|---|---|
| `QWEATHER_KEY` | `local.properties` (project root) | QWeather API key, injected at build time via `BuildConfig`; never appears in source or the repository |

## Notes

### Obtaining a QWeather key (self-registration required)

1. Register at the [QWeather developer console](https://console.qweather.com); the free development subscription is enough
2. Console → Projects → create a project → add a **Web API** credential; you get a 32-character key
3. Put the key in `QWEATHER_KEY=` inside `local.properties`, then build
4. Without a key the app builds and installs fine, but cannot fetch data (the display keeps showing the waiting animation)

The app calls `weather/now` and `weather/24h` on `devapi.qweather.com`, once every 10 minutes each — well within the free tier.

### Changing cities (two built-in regions for now)

The city list is hardcoded in `app/src/main/java/me/jegxiao/temprain/WeatherState.kt`:

```kotlin
val CITIES = listOf(
    City("哈尔滨市南岗区", 45.75f, 126.65f),   // name, latitude, longitude
    City("大连市甘井子区", 38.98f, 121.53f)
)
```

- Two decimal places are enough for the coordinates; the code already joins them as QWeather's `longitude,latitude` format, so just write latitude first
- The radio buttons are hardcoded for two cities; **adding/removing cities requires two more edits**:
  1. `app/src/main/res/layout/activity_main.xml` — the `RadioButton`s inside `city_group` (one per city, ids in sequence)
  2. `MainActivity.kt` — the city switching binding (`rg.check(...)` and the id→index mapping inside `setOnCheckedChangeListener`)
- Switching a city refetches both endpoints immediately, no need to wait for the next cycle

### Widget & background

- Add via long-press on the home screen → Widgets → TempRain; grant "exact alarm" in system settings (off by default on Android 13+)
- On Chinese ROMs (Honor/Huawei etc.), allow auto-start and background activity in "App launch management"; the "keep-alive settings" button inside the app jumps straight to the app info page
- The persistent notification is required by the foreground-service mechanism and cannot be hidden; its channel is lowest-importance and silent

### Security

- The API key lives only in your local `local.properties` and ends up inside your own APK; do not distribute an APK built with your key (it can be extracted by decompiling)
- The git history of this repository has been audited — no commit contains any secret
