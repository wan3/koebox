import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 签名信息从 local.properties 读取（该文件不进仓库），
// 也可以用环境变量 KOEBOX_STORE_PASSWORD / KOEBOX_KEY_PASSWORD 提供
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.koebox.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.koebox.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 11
        versionName = "1.9"
    }

    signingConfigs {
        create("releaseSelf") {
            val ksPath = localProps.getProperty("keystore.path") ?: "../keystore/koebox.jks"
            val ksPass = localProps.getProperty("keystore.password")
                ?: System.getenv("KOEBOX_STORE_PASSWORD")
            val keyPass = localProps.getProperty("keystore.keyPassword")
                ?: System.getenv("KOEBOX_KEY_PASSWORD")
                ?: ksPass
            if (ksPass != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = localProps.getProperty("keystore.alias") ?: "koebox"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingConfigs.getByName("releaseSelf").storeFile != null) {
                signingConfigs.getByName("releaseSelf")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
}
