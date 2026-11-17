plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Bilang ng build mula sa GitHub Actions; 1 kapag sa sariling computer
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.videoketana.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.videoketana.app"
        minSdk = 23
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // Pansariling susi para iisa ang pirma ng bawat build (para ma-update ang app nang hindi binubura).
    // Para sa Play Store, palitan ito ng pribadong susi na nasa GitHub Secrets.
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
