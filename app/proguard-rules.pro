# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.juexin.assistant.model.** { *; }

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep the ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    *;
}

# Keep the service classes
-keep class com.juexin.assistant.FloatingBallService { *; }
-keep class com.juexin.assistant.ClipboardService { *; }
-keep class com.juexin.assistant.ReplyGenerator { *; }
-keep class com.juexin.assistant.ui.** { *; }
-keep class com.juexin.assistant.network.** { *; }
