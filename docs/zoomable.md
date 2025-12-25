# Zoomable

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

<img src="https://github.com/skydoves/landscapist/raw/main/preview/sample0.gif" align="right" width="32%"/>

The `landscapist-zoomable` package provides a `ZoomablePlugin` that enables zoom and pan gestures for images. This plugin supports both Android and Kotlin Multiplatform (iOS, Desktop).

To use zoomable supports, add the dependency below:

```kotlin
dependencies {
    implementation("com.github.skydoves:landscapist-zoomable:$version")
}
```

## ZoomablePlugin

You can implement zoom and pan gestures by adding `ZoomablePlugin` to your image component:

=== "Glide"

    ```kotlin
    GlideImage(
      imageModel = { imageUrl },
      component = rememberImageComponent {
        +ZoomablePlugin()
      }
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      imageModel = { imageUrl },
      component = rememberImageComponent {
        +ZoomablePlugin()
      }
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      imageUrl = imageUrl,
      component = rememberImageComponent {
        +ZoomablePlugin()
      }
    )
    ```

## ZoomableState

You can create and remember a `ZoomableState` with `rememberZoomableState` to customize the zoom behavior and access the current transformation state:

```kotlin
val zoomableState = rememberZoomableState(
  config = ZoomableConfig(
    minZoom = 1f,           // Minimum zoom scale (default: 1f)
    maxZoom = 4f,           // Maximum zoom scale (default: 4f)
    doubleTapZoom = 2f,     // Zoom scale on double-tap (default: 2f)
    enableDoubleTapZoom = true,  // Enable double-tap to zoom (default: true)
  )
)

GlideImage(
  imageModel = { imageUrl },
  component = rememberImageComponent {
    +ZoomablePlugin(state = zoomableState)
  }
)

// Access current zoom state
val currentScale = zoomableState.transformation.scale
val currentOffset = zoomableState.transformation.offset
```

You can also use `resetKey` parameter to automatically reset the zoom state when the image changes:

```kotlin
val zoomableState = rememberZoomableState(resetKey = imageUrl)

GlideImage(
  imageModel = { imageUrl },
  component = rememberImageComponent {
    +ZoomablePlugin(state = zoomableState)
  }
)
```

### ZoomableConfig Options

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minZoom` | Float | 1f | The minimum zoom scale |
| `maxZoom` | Float | 4f | The maximum zoom scale |
| `doubleTapZoom` | Float | 2f | The zoom scale to apply when double-tapping |
| `enableDoubleTapZoom` | Boolean | true | Whether double-tap to zoom gesture is enabled |
| `enableSubSampling` | Boolean | false | Whether sub-sampling for large images is enabled |
| `subSamplingConfig` | SubSamplingConfig | SubSamplingConfig() | Configuration for sub-sampling behavior |

## Gestures

The `ZoomablePlugin` supports the following gestures:

- **Pinch to zoom**: Use two fingers to zoom in/out
- **Double-tap to zoom**: Double-tap to toggle between original and zoomed state
- **Pan**: Drag to pan around when zoomed in
- **Fling**: Quick swipe to fling with momentum

## Sub-Sampling (Tiling)

For very large images, Landscapist supports sub-sampling (tiled rendering) to efficiently display high-resolution images without running out of memory. This feature loads only the visible tiles at the appropriate resolution.

### Enabling Sub-Sampling

```kotlin
val zoomableState = rememberZoomableState(
  config = ZoomableConfig(
    enableSubSampling = true,
    subSamplingConfig = SubSamplingConfig(
      tileSize = 512.dp,     // Size of each tile (default: 512.dp)
      threshold = 2000.dp,   // Minimum image dimension to enable sub-sampling
    )
  )
)

GlideImage(
  imageModel = { imageUrl },
  component = rememberImageComponent {
    +ZoomablePlugin(state = zoomableState)
  }
)
```

### SubSamplingConfig Options

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `tileSize` | Dp | 512.dp | The size of each tile. Larger tiles mean fewer tiles but more memory per tile |
| `threshold` | Dp | 2000.dp | The minimum image dimension to enable sub-sampling. Images smaller than this will be rendered normally |

### Sub-Sampling Support by Image Loader

| Image Loader | Android | iOS/Desktop |
|--------------|---------|-------------|
| **Coil3** | Supported (network + local) | Supported (network + local) |
| **Glide** | Supported (network + local) | N/A (Android only) |
| **Fresco** | Supported (local files only) | N/A (Android only) |

!!! note
    Sub-sampling requires the image source to support region decoding. For network images, the image is first cached to disk before sub-sampling can be used.

## Kotlin Multiplatform Support

The `ZoomablePlugin` supports Kotlin Multiplatform:

- **Android**: Full support with sub-sampling
- **iOS**: Full support with sub-sampling (using CGImageSource)
- **Desktop (JVM)**: Full support with sub-sampling

Add the dependency to your common source set:

```kotlin
sourceSets {
    val commonMain by getting {
        dependencies {
            implementation("com.github.skydoves:landscapist-zoomable:$version")
        }
    }
}
```
