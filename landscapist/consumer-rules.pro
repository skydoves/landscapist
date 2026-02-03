# Landscapist ProGuard Rules

# Keep all enum classes with full enum metadata (required for Kotlin enum reflection and R8/ProGuard optimization)
-keepclassmembers,allowoptimization enum com.skydoves.landscapist.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    <fields>;
}

# Keep enum classes themselves (prevents "not an enum class" errors after optimization)
-keep enum com.skydoves.landscapist.** { *; }

# Keep public API classes
-keep class com.skydoves.landscapist.DataSource { *; }
-keep class com.skydoves.landscapist.ImageLoadState { *; }
-keep class com.skydoves.landscapist.ImageLoadState$* { *; }
-keep class com.skydoves.landscapist.ImageOptions { *; }

# Keep Kotlin metadata (required for reflection)
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# Prevent stripping of Kotlin coroutines/flow internals
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.flow.** { *; }
