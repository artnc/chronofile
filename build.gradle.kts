buildscript {
  val kotlinVersion = "2.0.21"

  repositories {
    google()
    mavenCentral()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:8.7.2")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
  }
}

allprojects {
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}
