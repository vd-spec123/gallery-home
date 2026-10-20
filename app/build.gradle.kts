plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vernon.galleryhome"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vernon.galleryhome"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // A fixed key checked into the repo so every build can update the last one in place.
        create("home") {
            storeFile = file("gallery-home.jks")
            storePassword = "galleryhome"
            keyAlias = "galleryhome"
            keyPassword = "galleryhome"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("home")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
