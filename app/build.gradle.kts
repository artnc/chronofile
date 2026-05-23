import java.io.FileInputStream
import java.util.Properties

plugins { id("com.android.application") }

// https://developer.android.com/studio/publish/app-signing#secure_key
val keystoreProperties = Properties()

keystoreProperties.load(FileInputStream(rootProject.file("keystore.properties")))

android {
  // https://github.com/artnc/chronofile/issues/13#issuecomment-3408303694
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

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
    compileSdk = 36
    minSdk = 23
    targetSdk = 36
    versionCode = 6
    versionName = "1.1.1"
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
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.cardview:cardview:1.0.0")
  implementation("androidx.documentfile:documentfile:1.1.0")
  implementation("androidx.recyclerview:recyclerview:1.4.0")
  implementation("androidx.viewpager2:viewpager2:1.1.0")
  implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
  implementation("com.google.android.gms:play-services-location:21.3.0")
  implementation("com.google.android.material:material:1.14.0")
  implementation("com.google.code.gson:gson:2.14.0")
  implementation("com.jakewharton.rxrelay2:rxrelay:2.1.1")
  implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
}
