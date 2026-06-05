# ZenPaper ProGuard Rules
# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Jetpack Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep WallpaperManager
-keep class android.app.WallpaperManager { *; }

# Keep MediaStore
-keep class android.provider.MediaStore { *; }

# Keep our application classes
-keep class com.zenpaper.** { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable
-keep class * implements java.io.Serializable { *; }

# Keep Enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# OkHttp / Network
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson / JSON (if used)
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Coil (image loading)
-keep class coil3.** { *; }
-dontwarn coil3.**

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keepline numbering for stack traces
-keepattributes SourceFile,LineNumberTable

# Optimize
-optimizationpasses 5
-allowaccessmodification
-mergelocals
-optimizations !code/simplification/cast,!code/simplification/arithmetic,!field/*,!class/merging/*
