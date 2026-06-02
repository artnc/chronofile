import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

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
    isCoreLibraryDesugaringEnabled = true
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
    buildConfig = true
    compose = true
  }

  buildTypes {
    debug {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("config")
    }
    release {
      isDebuggable = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("config")
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.mpandroidchart)
  implementation(libs.play.services.location)

  debugImplementation(libs.androidx.compose.ui.tooling)

  coreLibraryDesugaring(libs.desugar.jdk.libs)
}
