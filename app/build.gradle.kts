plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sanny.builder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sanny.builder"
        minSdk = 21 // 支持 Android 5+
        targetSdk = 29
        versionCode = 1
        versionName = "1.0.0"
        // 只需 armv7 (armeabi-v7a) + arm64；剔除 x86/mips 与 JNA 其余 ABI so
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
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
        isCoreLibraryDesugaringEnabled = false // 代码未用 Java 8+ 核心库；避免离线缺 desugar 包
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("io.github.rosemoe:editor:0.23.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
