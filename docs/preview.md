# Preview on Android Studio

[Android Studio's Composable Preview](https://developer.android.com/jetpack/compose/tooling/previews) feature is an incredibly powerful tool that allows you to develop and preview a specific part of your Composable without the need to build the entire project.

The versatility of Landscapist extends to supporting preview mode for each image library, including Glide, Coil, and Fresco. You can conveniently showcase preview images directly within your editor using the previewPlaceholder parameter, as demonstrated below:

=== "Glide"

    ```kotlin
    GlideImage(
      previewPlaceholder = R.drawable.poster,
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      previewPlaceholder = R.drawable.poster,
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      previewPlaceholder = R.drawable.poster,
      ..
    )
    ```

This feature streamlines your development process, providing a visual representation of the image before building the entire project, saving valuable time and effort.

Now, you can build your preview composable like so:

```kotlin
@Composable
private fun GlideImagePreview() {
    GlideImage(
      previewPlaceholder = R.drawable.poster,
      ..
    )
}
```

Once you compile your preview function, you will see the result displayed below in your Android Studio:

![Preview Composable](https://user-images.githubusercontent.com/24237865/148672035-6a82eba5-900c-44ee-a42c-acbf8038d0ab.png)