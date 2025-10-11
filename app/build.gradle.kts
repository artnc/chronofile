import java.io.FileInputStream
import java.util.Properties

plugins {
  id("com.android.application")
  id("kotlin-android")
}

// https://developer.android.com/studio/publish/app-signing#secure_key
val keystoreProperties = Properties()

keystoreProperties.load(FileInputStream(rootProject.file("keystore.properties")))

android {
  signingConfigs {
    create("config") {
      keyAlias = keystoreProperties["keyAlias"] as String
      keyPassword = keystoreProperties["keyPassword"] as String
      storeFile = file(keystoreProperties["storeFile"] as String)
      storePassword = keystoreProperties["storePassword"] as String
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  namespace = "com.chaidarun.chronofile"

  defaultConfig {
    applicationId = "com.chaidarun.chronofile"
    compileSdk = 35
    minSdk = 21
    targetSdk = 35
    versionCode = 5
    versionName = "1.1.0"
  }

  buildFeatures {
    viewBinding = true
    buildConfig = true
  }

  buildTypes {
    debug {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("config")
    }
    release {
      isDebuggable = false
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("config")
    }
  }
}

dependencies {
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.cardview:cardview:1.0.0")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
  implementation("com.google.android.gms:play-services-location:21.3.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("com.google.code.gson:gson:2.11.0")
  implementation("com.jakewharton.rxrelay2:rxrelay:2.0.0")
  implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
}
