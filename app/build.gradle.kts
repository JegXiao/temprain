import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 和风天气 API Key 从 local.properties（已被 .gitignore）读取，绝不进源码/仓库。
// 拉取本项目后：在 local.properties 中加一行 QWEATHER_KEY=你的key 再编译。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val qweatherKey: String = localProps.getProperty("QWEATHER_KEY") ?: ""

// release 签名从项目属性注入（本地 -P 参数 / CI 环境变量转 -P），keystore 与密码永不入库。
// 未提供时 release 包不签名（本地调试用 debug 包即可）。
val releaseStoreFile: String? = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseStorePassword: String? = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias: String? = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword: String? = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull

android {
    namespace = "me.jegxiao.temprain"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.jegxiao.temprain"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "QWEATHER_KEY", "\"$qweatherKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}