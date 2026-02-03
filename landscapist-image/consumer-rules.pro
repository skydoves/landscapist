# Landscapist Image ProGuard Rules

# Keep state classes
-keep class com.skydoves.landscapist.image.LandscapistImageState { *; }
-keep class com.skydoves.landscapist.image.LandscapistImageState$* { *; }

# Keep image converter classes (used via expect/actual)
-keep class com.skydoves.landscapist.image.ImageBitmapConverterKt { *; }

# Keep painter classes
-keep class com.skydoves.landscapist.image.LandscapistPainterKt { *; }
