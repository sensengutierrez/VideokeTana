plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Bilang ng build mula sa GitHub Actions (ito rin ang numero ng release: app-vN); 1 kapag sa sariling computer
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    // GeneralUser GS (SoundFont) kasama na sa app: kinukuha mula sa frontend/sf (iisang kopya sa repo).
    // Hindi kino-compress para mabasa nang diretso (mmap) mula sa APK, walang download at walang kopya.
    sourceSets["main"].assets.srcDir(file("../../frontend/sf"))
    androidResources { noCompress += "sf2" }
    namespace = "com.videoketana.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.videoketana.app"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "2.0.$runNumber"
    }

    // Pansariling susi para iisa ang pirma ng bawat build (para ma-update ang app nang hindi binubura).
    signingConfigs {
        create("personal") {
            storeFile = file("videoke-personal.keystore")
            storePassword = "videoke-tana"
            keyAlias = "videoke"
            keyPassword = "videoke-tana"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
        debug { signingConfig = signingConfigs.getByName("personal") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
