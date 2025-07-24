plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.xlab.Player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xlab.Player"
        minSdk = 24
        targetSdk = 34
        versionCode = 13
        versionName = "4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 빌드 시간 정보 추가
        val buildTime = System.currentTimeMillis()
        buildConfigField("long", "BUILD_TIME", "${buildTime}L")
        buildConfigField("String", "BUILD_DATE", "\"빌드 시간: ${buildTime}\"")
        buildConfigField("String", "BUILD_VERSION", "\"빌드 #${versionCode}\"")
        
        // NDK 설정 (네이티브 디코딩 지원용)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable.add("NotificationPermission")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // ExoPlayer for H.265 support (최신 호환 버전)
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-rtsp:2.19.1")
    // MediaCodec support
    implementation("androidx.media:media:1.6.0")
    // LibVLC Android for alternative RTSP playback
    implementation("org.videolan.android:libvlc-all:3.6.0-eap5")
    // Kotlin Coroutines for PTZ control
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
// AAR 발행 설정은 데모 실행을 위해 임시 제거