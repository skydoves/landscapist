# Custom Composable

Landscapist offers a powerful way to load images using your custom Composable functions. This capability allows you to tailor your own loading placeholders, success images, or fallbacks for handling loading failures. With such flexibility, you can create a personalized and seamless image loading experience tailored to your specific app's needs.

## Build Your Own Composable

You can execute your own composable functions based on the three image states below:

- **loading**: Executes while the image is being loaded.
- **success**: Executes upon successful image loading.
- **failure**: Executes when there is a failure to load the image (e.g., network error, incorrect destination).

=== "Glide"

    ```kotlin
    GlideImage(
      // displays an indicator while loading an image.
      loading = {
        Box(modifier = Modifier.matchParentSize()) {
          CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center)
         ) 
        }
      },
      // displays an error fallback if fails to load an image.
      failure = {
        Text(text = "image request failed.")
     },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      // displays an indicator while loading an image.
      loading = {
        Box(modifier = Modifier.matchParentSize()) {
          CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center)
         ) 
        }
      },
      // displays an error fallback if fails to load an image.
      failure = {
        Text(text = "image request failed.")
     },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      // displays an indicator while loading an image.
      loading = {
        Box(modifier = Modifier.matchParentSize()) {
          CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center)
         ) 
        }
      },
      // displays an error fallback if fails to load an image.
      failure = {
        Text(text = "image request failed.")
     },
      ..
    )
    ```

With the above custom loading composable sample, you'll see the result below:

![Custom Loading Composable](https://user-images.githubusercontent.com/24237865/94174882-d6e1db00-fed0-11ea-86ec-671b5039b1b9.gif)


## Render Your Own Image Composable

You can also render your own Composable function with the success image state.

=== "Glide"

    ```kotlin
    GlideImage(
      success = { imageState ->
        imageState.imageBitmap?.let {
          Image(
            bitmap = it,
            modifier = Modifier.size(128.dp) // draw a resized image.
          )
        }
      },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      success = { imageState ->
        imageState.imageBitmap?.let {
          Image(
            bitmap = it,
            modifier = Modifier.size(128.dp) // draw a resized image.
          )
        }
      },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      success = { imageState ->
        imageState.imageBitmap?.let {
          Image(
            bitmap = it,
            modifier = Modifier.size(128.dp) // draw a resized image.
          )
        }
      },
      ..
    )
    ```

As you can see in the above example, you're able to render your image composable inside the `success` lambda parameter, which provides the image state and painter.