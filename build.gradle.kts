buildscript {
  val kotlinVersion = "2.3.21"

  repositories {
    google()
    mavenCentral()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:9.2.0")
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
