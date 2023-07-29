Landscapist provides multiple ways to customize the requests.

### Custom ImageRequest
You can load images with your own [ImageRequest](https://frescolib.org/docs/image-requests.html), which provides some necessary information for loading images like decoding strategies and resizing.

```kotlin
val imageRequest = ImageRequestBuilder
  .newBuilderWithSource(uri)
  .setImageDecodeOptions(decodeOptions)
  .setLocalThumbnailPreviewsEnabled(true)
  .setLowestPermittedRequestLevel(RequestLevel.FULL_FETCH)
  .setProgressiveRenderingEnabled(false)
  .setResizeOptions(ResizeOptions(width, height))
  .build()

FrescoImage(
  imageUrl = stringImageUrl,
  imageRequest = { imageRequest }
)
```

### LocalFrescoImageRequest
You can pass the same instance of your `imageRequestBuilder` down through the Composition in your composable hierarchy as following the example below:

```kotlin
// customize the ImageRequest as needed
val imageRequestBuilder = ImageRequestBuilder
  .newBuilderWithSource(uri)
  .setImageDecodeOptions(decodeOptions)
  .setLocalThumbnailPreviewsEnabled(true)
  .setLowestPermittedRequestLevel(RequestLevel.FULL_FETCH)
  .setProgressiveRenderingEnabled(false)
  .setResizeOptions(ResizeOptions(width, height))

CompositionLocalProvider(LocalFrescoImageRequest provides imageRequestBuilder) {
  // This will automatically use the value of current ImageRequest in the hierarchy.
  FrescoImage(
    imageUrl = ...
  )
}
```