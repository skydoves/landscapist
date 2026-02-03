# Landscapist Coil3 ProGuard Rules

# Suppress warnings for SLF4J (optional logging dependency)
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep Coil classes
-keep class coil3.** { *; }
-keep class coil3.request.** { *; }
-keep class coil3.decode.** { *; }

# Keep Landscapist Coil integration classes
-keep class com.skydoves.landscapist.coil3.** { *; }
