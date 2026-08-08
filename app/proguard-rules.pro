# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

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
