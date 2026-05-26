# UE5 Asset Analyzer ProGuard Rules

# Keep data models (used by Room + JSON serialization)
-keep class com.example.ue5analyzer.model.** { *; }

# Keep Room entities and DAOs
-keep class * extends androidx.room.Entity { *; }
-keep class * extends androidx.room.Dao { *; }
-keep @androidx.room.Entity class * { *; }

# Keep enum classes used in serialization
-keepclassmembers enum com.example.ue5analyzer.model.AssetType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.example.ue5analyzer.model.OrphanRiskLevel {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-dontwarn androidx.compose.**
