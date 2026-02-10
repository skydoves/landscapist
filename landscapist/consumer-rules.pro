# Landscapist ProGuard Rules (Android)

# Keep enum classes with full metadata (prevents "not an enum class" errors)
-keep enum com.skydoves.landscapist.** { *; }

-keepclassmembers,allowoptimization enum com.skydoves.landscapist.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    <fields>;
}

# Keep public API classes
-keep class com.skydoves.landscapist.DataSource { *; }
-keep class com.skydoves.landscapist.ImageLoadState { *; }
-keep class com.skydoves.landscapist.ImageLoadState$* { *; }
-keep class com.skydoves.landscapist.ImageOptions { *; }
