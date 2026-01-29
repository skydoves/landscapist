# Landscapist Core ProGuard Rules

# Keep enum classes (required for Kotlin enum reflection)
-keepclassmembers enum com.skydoves.landscapist.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep model and cache classes
-keep class com.skydoves.landscapist.core.model.** { *; }
-keep class com.skydoves.landscapist.core.cache.** { *; }
