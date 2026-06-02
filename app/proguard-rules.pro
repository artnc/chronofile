# Android
# https://stackoverflow.com/a/5553290
-assumenosideeffects class android.util.Log {
  public static *** d(...);
  public static *** i(...);
  public static *** v(...);
  public static *** w(...);
}

# kotlinx.serialization
# https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep `Companion` object fields of serializable classes
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
  static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects of serializable classes
-if @kotlinx.serialization.Serializable class ** {
  static **$* *;
}
-keepclassmembers class <2>$<3> {
  kotlinx.serialization.KSerializer serializer(...);
}

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
