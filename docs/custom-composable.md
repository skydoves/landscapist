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

## Sizing & Modifier Propagation

A common source of confusion is that the `modifier` you pass to `GlideImage`, `CoilImage`, or `FrescoImage` is **not** automatically forwarded to the `loading`, `success`, or `failure` slots. Understanding why will help you build state slots that align with the size you originally requested.

### How the outer `modifier` is applied

Internally, every Landscapist image composable wraps its content in a `BoxWithConstraints` and applies your `modifier` to that root container only:

```kotlin
// landscapist/.../ImageLoad.kt (simplified)
BoxWithConstraints(
  modifier = modifier.imageSemantics(imageOptions),
  propagateMinConstraints = true,
) {
  content(state) // loading / success / failure dispatched here
}
```

This means your `Modifier.size(...)`, `Modifier.fillMaxWidth()`, `Modifier.aspectRatio(...)`, etc. **establish the layout box** for the image. State slots are invoked **inside** that box as `BoxScope` lambdas, so they have access to the parent's size — but only if you opt-in via a child modifier such as `Modifier.matchParentSize()` or `Modifier.fillMaxSize()`.

### The problem: custom slots that ignore parent size

```kotlin
GlideImage(
  imageModel = { url },
  modifier = Modifier.size(200.dp), // outer box is 200x200.dp
  failure = {
    // ❌ This Image will be rendered at its intrinsic size (often tiny),
    //    not at 200x200.dp, because no size modifier is applied here.
    Image(
      painter = painterResource(R.drawable.ic_error),
      contentDescription = null,
    )
  },
)
```

The same applies to `success` when you provide your own composable: if you draw an `Image` without a size modifier, it falls back to the painter's intrinsic size — which is especially noticeable for **small SVGs** placed inside a larger requested box.

### The fix: opt-in to the parent size with `matchParentSize()`

`Modifier.matchParentSize()` is available on `BoxScope` and tells the child to take the same size as the parent **without contributing to parent measurement**. It is the recommended way to make slot content fill the requested box.

=== "Glide"

    ```kotlin
    GlideImage(
      imageModel = { url },
      modifier = Modifier.size(200.dp),
      imageOptions = ImageOptions(contentScale = ContentScale.Fit),
      loading = {
        Box(modifier = Modifier.matchParentSize()) {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
      },
      success = { state, painter ->
        Image(
          painter = painter,
          contentDescription = null,
          modifier = Modifier.matchParentSize(), // ✅ inherits 200x200.dp
          contentScale = ContentScale.Fit,        // ✅ safe for SVGs / small bitmaps
        )
      },
      failure = {
        Image(
          painter = painterResource(R.drawable.ic_error),
          contentDescription = null,
          modifier = Modifier.matchParentSize(), // ✅ fills the requested box
          contentScale = ContentScale.Fit,
        )
      },
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      imageModel = { url },
      modifier = Modifier.size(200.dp),
      imageOptions = ImageOptions(contentScale = ContentScale.Fit),
      loading = {
        Box(modifier = Modifier.matchParentSize()) {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
      },
      success = { state, painter ->
        Image(
          painter = painter,
          contentDescription = null,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Fit,
        )
      },
      failure = {
        Image(
          painter = painterResource(R.drawable.ic_error),
          contentDescription = null,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Fit,
        )
      },
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      imageUrl = url,
      modifier = Modifier.size(200.dp),
      imageOptions = ImageOptions(contentScale = ContentScale.Fit),
      loading = {
        Box(modifier = Modifier.matchParentSize()) {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
      },
      success = { state, painter ->
        Image(
          painter = painter,
          contentDescription = null,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Fit,
        )
      },
      failure = {
        Image(
          painter = painterResource(R.drawable.ic_error),
          contentDescription = null,
          modifier = Modifier.matchParentSize(),
          contentScale = ContentScale.Fit,
        )
      },
    )
    ```

### Working with SVGs and small source images

When the loaded image (especially an SVG or small bitmap) has an intrinsic size **smaller than** the outer `Modifier.size(...)`, the visual result depends entirely on `ContentScale`:

| `ContentScale`           | Behavior with a small SVG inside a larger box        |
|--------------------------|------------------------------------------------------|
| `Crop` (default)         | Image is scaled up and may be cropped at the edges.  |
| `Fit`                    | Image keeps its aspect ratio and is scaled to fit.   |
| `Inside`                 | Image stays at its intrinsic size if it already fits.|
| `None`                   | Image is drawn at its intrinsic size, no scaling.    |

If you want the SVG to be drawn at its intrinsic size and centered inside the requested box (a very common pattern for icon-style fallbacks), combine `Box(matchParentSize())` with `align(Alignment.Center)`:

```kotlin
success = { state, painter ->
  Box(modifier = Modifier.matchParentSize()) {
    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.align(Alignment.Center), // intrinsic-sized & centered
      contentScale = ContentScale.None,
    )
  }
}
```

### Quick checklist

- ✅ The outer `modifier` defines the **box**; state slots run **inside** it as `BoxScope`.
- ✅ Use `Modifier.matchParentSize()` (preferred) or `Modifier.fillMaxSize()` inside slots to inherit the requested size.
- ✅ For SVGs or small bitmaps drawn inside a larger box, switch `ImageOptions.contentScale` from the default `Crop` to `Fit` (or wrap with a `Box` + `align(Alignment.Center)` for intrinsic sizing).
- ❌ Don't assume the outer `Modifier.size(...)` is automatically applied to your custom `success` / `failure` content — it isn't.