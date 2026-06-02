plugins {
  alias(libs.plugins.android.application) apply false
  // Declared but not applied: AGP's built-in Kotlin support picks the Kotlin Gradle plugin up off
  // the classpath, so we never apply org.jetbrains.kotlin.android directly
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}
