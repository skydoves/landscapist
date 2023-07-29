# ImageComponent and ImagePlugin

One of the most versatile fatures of Landscapist is the `ImageComponent` and `ImagePlugin`:

- **ImageComponent**: The `ImageComponent` is a flexible interface that acts as a container for a collection of `ImagePlugins`.
- **ImagePlugin**: The `ImagePlugin` is an executable and pluggable Compose interface, triggered based on specific image states.

With Landscapist, you have the flexibility to compose supported image plugins, or you can even implement your own custom image plugin that will be executed based on the image loading state. This powerful feature empowers you to tailor the image loading behavior according to your specific needs and preferences.

- **PainterPlugin**: A pinter plugin interface to be composed with the given `Painter`.
- **LoadingStatePlugin**: A pluggable state plugin that will be composed while the state is `ImageLoadState.Loading`.
- **SuccessStatePlugin**: A pluggable state plugin that will be composed when the state is `ImageLoadState.Success`.
- **FailureStatePlugin**: A pluggable state plugin that will be composed when the state is `ImageLoadState.Failure`.

Whether you choose from the existing plugins or create your own, Landscapist offers a seamless and customizable image loading experience for your app.

As an example, you can implement your own LoadingStatePlugin that will be composed specifically while loading an image, as demonstrated below:

```kotlin
data class LoadingPlugin(val source: Any?) : ImagePlugin.LoadingStatePlugin {

  // this composable function will be executed while loading an image.
  @Composable
  override fun compose(
    modifier: Modifier,
    imageOptions: ImageOptions,
    executor: @Composable (IntSize) -> Unit,
  ): ImagePlugin = apply {
    if (source != null && imageOptions != null) {
      ImageBySource(
        source = source,
        modifier = modifier,
        alignment = imageOptions.alignment,
        contentDescription = imageOptions.contentDescription,
        contentScale = imageOptions.contentScale,
        colorFilter = imageOptions.colorFilter,
        alpha = imageOptions.alpha
      )
    }
  }
}
```

By creating a custom `LoadingStatePlugin`, you can define unique behavior tailored to the loading state of the image. This gives you the freedom to handle loading scenarios in a way that best suits your application's requirements.

Now you can add your own image plugin into the image component like so:

=== "Glide"

    ```kotlin
    GlideImage(
        component = rememberImageComponent {
            add(CircularRevealPlugin())
            add(LoadingPlugin(source))
        },
        ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
        component = rememberImageComponent {
            add(CircularRevealPlugin())
            add(LoadingPlugin(source))
        },
        ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
        component = rememberImageComponent {
            add(CircularRevealPlugin())
            add(LoadingPlugin(source))
        },
        ..
    )
    ```

or you can just add plugins by using the **+** expression like the below:

=== "Glide"

    ```kotlin
    GlideImage(
        component = rememberImageComponent {
            +CircularRevealPlugin()
            +LoadingPlugin(source)
        },
        ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
        component = rememberImageComponent {
            +CircularRevealPlugin()
            +LoadingPlugin(source)
        },
        ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
        component = rememberImageComponent {
            +CircularRevealPlugin()
            +LoadingPlugin(source)
        },
        ..
    )
    ```

### LocalImageComponent

You can easily share the same `ImageComponent` instance throughout your composable hierarchy by utilizing the `imageComponent` extension and `LocalImageComponent`, as demonstrated below:

```kotlin
val component = imageComponent {
  +CrossfadePlugin()
  +PalettePlugin()
}

CompositionLocalProvider(LocalImageComponent provides component) {
  
  val imageComponent = LocalImageComponent.current

  GlideImage(
    component = imageComponent,
    ..
  )
}
```

By using `LocalImageComponent`, you can ensure that the same `ImageComponent` instance is accessible within the entire composable hierarchy, enabling seamless sharing of the image configuration across various composables. This makes it effortless to maintain consistency and manage image handling efficiently throughout your app.