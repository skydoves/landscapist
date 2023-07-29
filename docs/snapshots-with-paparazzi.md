# Snapshots With Paparazzi

[Paparazzi](https://github.com/cashapp/paparazzi) empowers you to capture snapshot images of your Composable functions without the need to run them on physical devices. With Paparazzi, you can easily take proper snapshot images of your Composables, providing a visual representation of your app's UI.

By utilizing Paparazzi, you can efficiently validate your app's UI states, ensuring consistent and visually appealing designs.

```kotlin hl_lines="2"
paparazzi.snapshot {
  CompositionLocalProvider(LocalInspectionMode provides true) {

    GlideImage(
      modifier = Modifier.fillMaxSize(),
      previewPlaceholder = R.drawable.placeholder,
      ..
    )
  }
}
```

