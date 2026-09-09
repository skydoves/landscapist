# Image Options

You can provide image options to your Landscapist composable functions by passing an `ImageOptions` instance, as demonstrated in the code below:

=== "Glide"

    ```kotlin
    GlideImage(
      imageOptions = ImageOptions(
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        contentDescription = "profile image",
        colorFilter = null,
        alpha = 1f,
        tag = "user profile image"
      ),
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      imageOptions = ImageOptions(
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        contentDescription = "profile image",
        colorFilter = null,
        alpha = 1f,
        tag = "user profile image"
      ),
      ..
    )    
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      imageOptions = ImageOptions(
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        contentDescription = "profile image",
        colorFilter = null,
        alpha = 1f,
        tag = "user profile image"
      ),
      ..
    )
    ```

### The default options

An image composable you pass no `imageOptions` to falls back to `ImageOptions.Default`, and you can
reach for it yourself when you want the defaults with one field changed:

```kotlin
LandscapistImage(
  imageModel = { url },
  imageOptions = ImageOptions.Default.copy(contentScale = ContentScale.Fit),
)
```

It is one shared instance rather than a fresh `ImageOptions()`. A default argument is re-evaluated
on every composition, so writing `ImageOptions()` there allocated one per image per frame for a
value that is always the same.

### Adjust Requesting Size

To prevent potential [Out of Memory Exceptions]((https://developer.android.com/reference/java/lang/OutOfMemoryError)) when loading large-sized images, you can set the explicit request size of your image using the `requestSize` property, as shown below:

```kotlin
GlideImage(
  ..,
  imageOptions = ImageOptions(requestSize = IntSize(800, 600)),
)
```

Landscapist will fetch the image with the explicit size from the network first, ensuring the image is rendered with the appropriate dimensions without consuming excessive memory. If you don't specify a specific `requestSize` parameter, the image will be automatically adjusted based on its original dimensions.