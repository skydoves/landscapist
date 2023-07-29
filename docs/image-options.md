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

### Adjust Requesting Size

To prevent potential [Out of Memory Exceptions]((https://developer.android.com/reference/java/lang/OutOfMemoryError)) when loading large-sized images, you can set the explicit request size of your image using the `requestSize` property, as shown below:

```kotlin
GlideImage(
  ..,
  imageOptions = ImageOptions(requestSize = IntSize(800, 600)),
)
```

Landscapist will fetch the image with the explicit size from the network first, ensuring the image is rendered with the appropriate dimensions without consuming excessive memory. If you don't specify a specific `requestSize` parameter, the image will be automatically adjusted based on its original dimensions.