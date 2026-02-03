# Landscapist Image ProGuard Rules for Desktop/JVM
# https://github.com/skydoves/landscapist
#
# These rules are required when building Desktop releases with ProGuard/R8.

# ============================================================================
# Landscapist Image State Classes
# ============================================================================
-keep class com.skydoves.landscapist.image.LandscapistImageState { *; }
-keep class com.skydoves.landscapist.image.LandscapistImageState$* { *; }

# Keep image converter classes (used via expect/actual)
-keep class com.skydoves.landscapist.image.ImageBitmapConverterKt { *; }

# Keep painter classes
-keep class com.skydoves.landscapist.image.LandscapistPainterKt { *; }

# ============================================================================
# Kotlin Metadata (Required for reflection)
# ============================================================================
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
