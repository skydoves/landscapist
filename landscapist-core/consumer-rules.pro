# Landscapist Core ProGuard Rules (Android)

# Keep enum classes with full metadata (prevents "not an enum class" errors)
-keep enum com.skydoves.landscapist.core.model.** { *; }

-keepclassmembers,allowoptimization enum com.skydoves.landscapist.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    <fields>;
}

# Keep model and cache classes
-keep class com.skydoves.landscapist.core.model.** { *; }
-keep class com.skydoves.landscapist.core.cache.** { *; }
-keep class com.skydoves.landscapist.core.ImageRequest { *; }
-keep class com.skydoves.landscapist.core.ImageRequest$* { *; }
