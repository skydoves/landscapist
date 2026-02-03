# Landscapist Core ProGuard Rules

# Keep all enum classes with full enum metadata (required for Kotlin enum reflection and R8/ProGuard optimization)
-keepclassmembers,allowoptimization enum com.skydoves.landscapist.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    <fields>;
}

# Keep enum classes themselves (prevents "not an enum class" errors after optimization)
-keep enum com.skydoves.landscapist.core.model.** { *; }

# Keep model and cache classes
-keep class com.skydoves.landscapist.core.model.** { *; }
-keep class com.skydoves.landscapist.core.cache.** { *; }
-keep class com.skydoves.landscapist.core.ImageRequest { *; }
-keep class com.skydoves.landscapist.core.ImageRequest$* { *; }

# Keep Ktor HTTP client classes (required for network image loading on Desktop/JVM)
# These are used by the Landscapist core engine and must not be stripped
-keep class io.ktor.client.** { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }
-keep class io.ktor.util.** { *; }
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# Keep ServiceLoader resources (required for Ktor engine discovery)
-keepnames class io.ktor.client.HttpClientEngineContainer
-keepnames class io.ktor.client.engine.cio.CIOEngineContainer

# Prevent issues with Okio (used by Ktor)
-dontwarn okio.**
-keep class okio.** { *; }
