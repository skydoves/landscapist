# Landscapist Core ProGuard Rules
# https://github.com/skydoves/landscapist

# Keep Landscapist enum classes (required for Kotlin enum reflection)
-keepclassmembers enum com.skydoves.landscapist.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Landscapist data classes used in state management
-keep class com.skydoves.landscapist.core.model.** { *; }
-keep class com.skydoves.landscapist.core.cache.** { *; }

# Ktor engine service loader (required for HTTP client initialization)
-keep class io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }

# Keep Ktor service provider implementations
-keep class * implements io.ktor.client.HttpClientEngineContainer

# Ktor serialization (if used)
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
