Landscapist provides multiple ways to customize the [ImageRequest](https://coil-kt.github.io/coil/image_requests/) and [ImageLoader](https://coil-kt.github.io/coil/image_loaders/).

### Custom ImageRequest and ImageLoader
You can load images with your own [ImageRequest](https://coil-kt.github.io/coil/image_requests/) and [ImageLoader](https://coil-kt.github.io/coil/image_loaders/), which provides all the necessary information for loading images like caching strategies and transformations.

```kotlin
CoilImage(
  imageRequest = {
      ImageRequest.Builder(LocalContext.current)
        .data(poster.poster)
        .crossfade(true)
        .build() },
  imageLoader = {
      ImageLoader.Builder(LocalContext.current)
        .availableMemoryPercentage(0.25)
        .crossfade(true)
        .build() },
  modifier = modifier,
)
```

### LocalCoilImageLoader

You can pass the same instance of your `ImageLoader` down through the Composition in your composable hierarchy as following the example below:

```kotlin
 val imageLoader = ImageLoader.Builder(context).build()
CompositionLocalProvider(LocalCoilImageLoader provides imageLoader) {
  
   // This will automatically use the value of current imageLoader in the hierarchy.
   CoilImage(
     imageModel = ...
   )
 }
```

## Animated Image Supports (GIF, Webp)
You can load animated GIFs and WebP Images with your `ImageLoader`.

 <img src="https://user-images.githubusercontent.com/24237865/131246748-b88903a1-43de-4e6c-9069-3e956a0cf8a6.gif" align="right" width="37%"/>

```kotlin
val context = LocalContext.current
val imageLoader = ImageLoader.Builder(context)
  .components {
    if (SDK_INT >= 28) {
      add(ImageDecoderDecoder.Factory())
    } else {
      add(GifDecoder.Factory())
    }
  }
  .build()

CoilImage(
    imageModel = { poster.gif }, // URL of an animated image.
    imageLoader = { imageLoader },
    shimmerParams = ShimmerParams(
      baseColor = background800,
      highlightColor = shimmerHighLight
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp)
      .height(500.dp)
      .clip(RoundedCornerShape(8.dp))
  )
```