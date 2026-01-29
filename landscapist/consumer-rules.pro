# Landscapist ProGuard Rules

# Keep enum classes (required for Kotlin enum reflection)
-keepclassmembers enum com.skydoves.landscapist.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep public API classes
-keep class com.skydoves.landscapist.DataSource { *; }
-keep class com.skydoves.landscapist.ImageLoadState { *; }
-keep class com.skydoves.landscapist.ImageLoadState$* { *; }
