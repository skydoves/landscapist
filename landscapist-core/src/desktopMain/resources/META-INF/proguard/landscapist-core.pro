# Landscapist Core ProGuard Rules for Desktop/JVM
# https://github.com/skydoves/landscapist

# ============================================================================
# Landscapist Enum Classes
# ============================================================================
# Keep enum classes with full metadata (prevents "not an enum class" errors)
-keep enum com.skydoves.landscapist.core.model.** { *; }
-keep enum com.skydoves.landscapist.** { *; }

-keepclassmembers,allowoptimization enum com.skydoves.landscapist.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    <fields>;
}

-keepclassmembers,allowoptimization enum com.skydoves.landscapist.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    <fields>;
}

# ============================================================================
# Landscapist Model and Cache Classes
# ============================================================================
-keep class com.skydoves.landscapist.core.model.** { *; }
-keep class com.skydoves.landscapist.core.cache.** { *; }
-keep class com.skydoves.landscapist.core.ImageRequest { *; }
-keep class com.skydoves.landscapist.core.ImageRequest$* { *; }

# ============================================================================
# Ktor HTTP Client (Required for network image loading)
# ============================================================================
# Keep all Ktor client classes
-keep class io.ktor.** { *; }
-keep class io.ktor.client.** { *; }
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.client.engine.cio.** { *; }
-keep class io.ktor.util.** { *; }

# Keep Ktor service loader classes (required for engine discovery)
# This is critical - ProGuard optimization can break service loader mechanisms
-keep class io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.client.engine.cio.CIOEngineContainer { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }

# Keep Ktor service loader targets (similar to Coil's approach)
-keep class io.ktor.utils.io.** { *; }
-keepnames class io.ktor.** implements java.io.Serializable

# Preserve Ktor volatile fields
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# Keep ServiceLoader configuration files
-keepresourcexmlelements META-INF/services/**

# ============================================================================
# Okio (Used by Ktor)
# ============================================================================
-dontwarn okio.**
-keep class okio.** { *; }
-keep class okio.Buffer { *; }

# ============================================================================
# Kotlin Coroutines and Flow
# ============================================================================
# Prevent stripping of coroutine/flow internals that may cause Flow exception transparency violations
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.flow.** { *; }
-keepclassmembers class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.flow.internal.** { *; }

# ============================================================================
# Kotlin Metadata (Required for reflection)
# ============================================================================
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ============================================================================
# Serialization (if used)
# ============================================================================
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# SLF4J (Optional logging)
# ============================================================================
-dontwarn org.slf4j.**
