import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin 支持，不需要 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Release 签名。
 *
 * 口令放在项目根的 `keystore.properties`（**不在版本库、不要外传**）。
 * 文件不存在时回落到 debug 签名 —— 保证 clone 下来的人也能 `assembleRelease`
 * 跑通流程，而不是卡在一个"缺密钥"的报错上。
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

/*
 * CI 用环境变量提供同一份密钥。
 *
 * 为什么要固定密钥：debug 签名每次构建都会重新生成 —— GitHub Actions 的 runner
 * 是干净的，每跑一次就是一个新证书。结果就是"上一版装不上，必须先卸载"。
 * 固定成一把 release 密钥之后，同一个包名的后续版本都能直接覆盖安装。
 *
 * 密钥本体放在仓库 Secret（`KEYSTORE_BASE64`），不进口版本库：
 * 公开仓库里放密钥等于允许任何人给这个包名签一个"更新"。
 */
val envStoreFile: String? = System.getenv("SIMXZ_KEYSTORE_FILE")
val hasEnvSigning = !envStoreFile.isNullOrBlank() &&
    !System.getenv("SIMXZ_KEYSTORE_PASSWORD").isNullOrBlank() &&
    !System.getenv("SIMXZ_KEY_ALIAS").isNullOrBlank() &&
    rootProject.file(envStoreFile).exists()

val hasReleaseSigning = keystorePropertiesFile.exists() || hasEnvSigning

android {
    namespace = "com.simplechat.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.simxz.chat"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "1.0.4"
        testInstrumentationRunner = "com.simplechat.app.DeviceRegression"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                if (keystorePropertiesFile.exists()) {
                    storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                } else {
                    storeFile = rootProject.file(envStoreFile!!)
                    storePassword = System.getenv("SIMXZ_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("SIMXZ_KEY_ALIAS")
                    keyPassword = System.getenv("SIMXZ_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 「关于」页直接读 BuildConfig.VERSION_NAME，避免手写版本号跟 build.gradle 漂移
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)
    implementation(libs.highlights)
    implementation(libs.latex.renderer)
    implementation(libs.latex.renderer.fonts)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}




