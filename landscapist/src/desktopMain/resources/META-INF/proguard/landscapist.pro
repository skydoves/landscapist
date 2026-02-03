# Landscapist ProGuard Rules for Desktop/JVM
# https://github.com/skydoves/landscapist
#
# These rules are required when building Desktop releases with ProGuard/R8.

# ============================================================================
# Landscapist Enum Classes
# ============================================================================
# Keep enum classes with full metadata (prevents "not an enum class" errors)
-keep enum com.skydoves.landscapist.** { *; }

-keepclassmembers,allowoptimization enum com.skydoves.landscapist.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    <fields>;
}

# ============================================================================
# Landscapist Public API Classes
# ============================================================================
-keep class com.skydoves.landscapist.DataSource { *; }
-keep class com.skydoves.landscapist.ImageLoadState { *; }
-keep class com.skydoves.landscapist.ImageLoadState$* { *; }
-keep class com.skydoves.landscapist.ImageOptions { *; }
-keep class com.skydoves.landscapist.ImageOptions$* { *; }

# Keep plugin interfaces and implementations
-keep class com.skydoves.landscapist.plugins.** { *; }
-keep class com.skydoves.landscapist.components.** { *; }

# ============================================================================
# Kotlin Metadata (Required for reflection)
# ============================================================================
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
