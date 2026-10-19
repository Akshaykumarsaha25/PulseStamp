plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.instabeats.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.instabeats.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    // One fixed debug key for every CI build, so a new APK installs over the last one.
    // Each GitHub runner otherwise makes a fresh key and Android refuses the update.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    // Stable Health Connect SDK (AAR requires compileSdk 36 + AGP 8.9.1)
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Jetpack Compose & AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // Frosted glass: real blur on Android 12+, tinted fallback below
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // Samsung Health Data SDK: reads GPS routes that Samsung Health keeps out of Health Connect.
    // Samsung's license forbids sharing the SDK, so it isn't in this repo. CI copies it from the
    // private instabeats-sdk repo (see .github/workflows/build-apk.yml); locally, put the AAR in app/libs.
    implementation(files("libs/samsung-health-data-api-1.1.0.aar"))
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.24")

    testImplementation("junit:junit:4.13.2")
}
