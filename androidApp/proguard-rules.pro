-keep class com.bfbai.superhub.SuperHubApp { *; }
-keep class com.bfbai.superhub.MainActivity { *; }
-keep class com.bfbai.superhub.service.ClipboardAccessibilityService { *; }
-keep class com.bfbai.superhub.service.ForegroundSyncService { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.bfbai.superhub.**$$serializer { *; }
-keepclassmembers class com.bfbai.superhub.** { *** Companion; }
-keepclasseswithmembers class com.bfbai.superhub.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
