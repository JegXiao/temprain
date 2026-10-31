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

android {
    namespace = "me.jegxiao.temprain"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.jegxiao.temprain"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "QWEATHER_KEY", "\"$qweatherKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
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