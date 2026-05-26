# UE5 Asset Analyzer ProGuard Rules

# ===== Data Models (Room + JSON serialization) =====
-keep class com.example.ue5analyzer.model.** { *; }
-keep class com.example.ue5analyzer.data.database.** { *; }
-keep class com.example.ue5analyzer.data.selection.** { *; }

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * extends androidx.room.Dao {
    <methods>;
}

# ===== Kotlin Serialization =====
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ue5analyzer.model.**$$serializer { *; }
-keepclassmembers class com.example.ue5analyzer.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.ue5analyzer.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== Enum classes =====
-keepclassmembers enum com.example.ue5analyzer.model.AssetType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.example.ue5analyzer.model.OrphanRiskLevel {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== WebView JavascriptInterface (3D Preview) =====
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.ue5analyzer.ui.screens.ObjPreviewScreen* { *; }

# ===== Retrofit =====
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    <methods>;
}
-keep interface com.example.ue5analyzer.data.network.JsonPlaceholderApi { *; }

# ===== Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===== Compose =====
-dontwarn androidx.compose.**

# ===== General Android =====
-keepattributes SourceFile,LineNumberTable
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.**