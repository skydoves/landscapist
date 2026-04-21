# Image Gallery

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

<img src="https://github.com/skydoves/landscapist/raw/main/preview/gallery.gif" align="right" width="32%"/>

The `landscapist-image-gallery` package provides high level, Compose Multiplatform UI components for building photo gallery experiences on top of Landscapist:

- **`ImageGallery`**: a thumbnail grid built on `LazyVerticalGrid` with optional multi select, header and footer slots, and a custom content slot.
- **`ImageViewer`**: a full screen pager with pinch to zoom, swipe to dismiss, and top bar, bottom bar, and page indicator overlays.
- **`ImageSharedTransitionConfig`**: drop in configuration for shared element transitions between gallery items and viewer pages.

By default, both components render their images using [`LandscapistImage`][landscapist-image], so they work on Android, iOS, Desktop, and Web without extra setup. You can swap in Glide, Coil, Fresco, or any custom loader through the `content` slot.

[landscapist-image]: landscapist/landscapist-image.md

To use the gallery components, add the dependency below:

```kotlin
dependencies {
    implementation("com.github.skydoves:landscapist-image-gallery:$version")
}
```

For Kotlin Multiplatform:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.skydoves:landscapist-image-gallery:$version")
        }
    }
}
```

> **Note**: `landscapist-image-gallery` transitively exposes `landscapist`, `landscapist-image`, and `landscapist-zoomable`.

## ImageGallery

`ImageGallery` displays a list of image models in a `LazyVerticalGrid`. The list items can be anything accepted by Landscapist (URL strings, `Uri`, `File`, `ByteArray`, drawable resources, and so on), as long as they have a stable `equals`/`hashCode` for grid keying.

```kotlin
import com.skydoves.landscapist.gallery.ImageGallery

ImageGallery(
  images = imageUrls,
  onImageClick = { index, imageModel ->
    navController.navigate("viewer/$index")
  },
)
```

### Grid layout

Tune the grid with `columns`, `contentPadding`, and arrangements. `aspectRatio` controls the shape of each cell. The default is `1f`, a square.

```kotlin
ImageGallery(
  images = imageUrls,
  columns = GridCells.Adaptive(minSize = 120.dp),
  contentPadding = PaddingValues(4.dp),
  horizontalArrangement = Arrangement.spacedBy(4.dp),
  verticalArrangement = Arrangement.spacedBy(4.dp),
  aspectRatio = 1f,
)
```

### Plugins and ImageOptions

Because each item is rendered by `LandscapistImage` under the hood, the full Landscapist plugin ecosystem is available. Pass an `ImageComponent` and an `ImageOptions`:

```kotlin
ImageGallery(
  images = imageUrls,
  imageOptions = ImageOptions(contentScale = ContentScale.Crop),
  component = rememberImageComponent {
    +ShimmerPlugin(
      shimmer = Shimmer.Resonate(
        baseColor = Color.DarkGray,
        highlightColor = Color.LightGray,
      ),
    )
  },
)
```

### Selection mode

Enable multi select by flipping `selectable` and tracking the selected indices in your own state. Long pressing any item enters selection mode, tapping items while in selection mode toggles them, and `onImageClick` is **not** fired during an active selection.

```kotlin
var selectedIndices by remember { mutableStateOf(emptySet<Int>()) }

ImageGallery(
  images = imageUrls,
  selectable = true,
  selectedIndices = selectedIndices,
  onSelectionChanged = { selectedIndices = it },
  selectionOverlay = { _, selected ->
    if (selected) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.TopEnd,
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colors.primary,
          modifier = Modifier.padding(6.dp).size(24.dp).clip(CircleShape),
        )
      }
    }
  },
)
```

### Header and footer slots

Both `header` and `footer` span the full grid width (`GridItemSpan(maxLineSpan)`), making them a good place to drop a `TopAppBar`, section title, or load more button.

```kotlin
ImageGallery(
  images = imageUrls,
  header = {
    TopAppBar(title = { Text("Gallery") })
  },
  footer = {
    TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
      Text("Load more")
    }
  },
)
```

### Custom content (Glide, Coil, Fresco)

Override the default renderer by providing a `content` lambda. When `content` is set, `component` and `imageOptions` are ignored, and you own the rendering.

```kotlin
ImageGallery(
  images = imageUrls,
  content = { index, imageModel ->
    GlideImage(
      imageModel = { imageModel },
      modifier = Modifier.fillMaxSize(),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
    )
  },
)
```

### ImageGalleryState

`rememberImageGalleryState()` wraps a `LazyGridState` so you can observe scroll state, scroll programmatically, or share a grid state across recompositions:

```kotlin
val galleryState = rememberImageGalleryState()

ImageGallery(
  images = imageUrls,
  state = galleryState,
)

// Scroll to an index:
LaunchedEffect(targetIndex) {
  galleryState.lazyGridState.animateScrollToItem(targetIndex)
}
```

### ImageGalleryDefaults

| Property | Default | Description |
|----------|---------|-------------|
| `Columns` | `GridCells.Fixed(3)` | Grid column configuration |
| `AspectRatio` | `1f` | Cell aspect ratio (width / height) |
| `HorizontalSpacing` | `2.dp` | Gap between columns |
| `VerticalSpacing` | `2.dp` | Gap between rows |

## ImageViewer

`ImageViewer` is a full screen pager for viewing images one at a time. It ships with:

- Horizontal paging via `HorizontalPager`
- Pinch and double tap to zoom and pan via `ZoomablePlugin`
- Swipe to dismiss with a configurable threshold and background alpha animation
- Overlay slots for a top bar, bottom bar, and page indicator

```kotlin
import com.skydoves.landscapist.gallery.ImageViewer
import com.skydoves.landscapist.gallery.rememberImageViewerState

val viewerState = rememberImageViewerState(
  initialPage = startIndex,
  pageCount = { imageUrls.size },
)

ImageViewer(
  images = imageUrls,
  state = viewerState,
  onDismiss = { navController.popBackStack() },
  onPageChanged = { page -> currentPage = page },
)
```

### Zoom configuration

`ImageViewer` routes its `zoomableConfig` to the underlying `ZoomablePlugin`, so all [Zoomable](zoomable.md) options apply: `minZoom`, `maxZoom`, `doubleTapZoom`, sub sampling, and more.

```kotlin
ImageViewer(
  images = imageUrls,
  zoomableConfig = ZoomableConfig(
    minZoom = 1f,
    maxZoom = 5f,
    doubleTapZoom = 2f,
    enableSubSampling = true,
  ),
)
```

### Top bar, bottom bar, and indicator

All three overlay slots receive `(currentPage, totalPages)` so you can build position aware UI. The `indicator` is stacked above `bottomBar`.

```kotlin
ImageViewer(
  images = imageUrls,
  topBar = { current, total ->
    TopAppBar(
      backgroundColor = Color.Black.copy(alpha = 0.5f),
      title = { Text("${current + 1} / $total", color = Color.White) },
    )
  },
  indicator = { current, total ->
    PageIndicator(current = current, total = total)
  },
  bottomBar = { _, _ ->
    BottomAppBar(backgroundColor = Color.Black.copy(alpha = 0.5f)) {
      // actions...
    }
  },
)
```

### Swipe to dismiss

Vertical swipe to dismiss is enabled by default. It is automatically disabled while the current page is zoomed, so zoom and pan gestures are not interrupted.

```kotlin
ImageViewer(
  images = imageUrls,
  enableSwipeToDismiss = true,
  dismissThreshold = 0.25f,  // fraction of screen height
  onDismiss = { onClose() },
)
```

### Custom content (Glide, Coil, Fresco)

Use the `content` slot to render each page with your image loader of choice:

```kotlin
ImageViewer(
  images = imageUrls,
  content = { page, imageModel ->
    CoilImage(
      imageModel = { imageModel },
      modifier = Modifier.fillMaxSize(),
      imageOptions = ImageOptions(contentScale = ContentScale.Fit),
      component = rememberImageComponent {
        +ShimmerPlugin(shimmer = Shimmer.Flash())
      },
    )
  },
)
```

Zoom, paging, swipe to dismiss, and shared transition registration are still provided for you. The `content` slot only replaces the image rendering.

### ImageViewerState

`rememberImageViewerState` returns an `ImageViewerState` that exposes the underlying `PagerState`, along with helpers:

```kotlin
val viewerState = rememberImageViewerState(
  initialPage = 0,
  pageCount = { imageUrls.size },
)

// Observe state
viewerState.currentPage       // current page index
viewerState.isZoomed          // whether the current page is zoomed in
viewerState.isDismissing      // whether a dismiss drag is in progress

// Drive state
scope.launch { viewerState.animateToPage(3) }
scope.launch { viewerState.resetZoom() }
```

### ImageViewerDefaults

| Property | Default | Description |
|----------|---------|-------------|
| `BackgroundColor` | `Color.Black` | Viewer background color |
| `PageSpacing` | `16.dp` | Gap between pager pages |
| `BeyondViewportPageCount` | `1` | Pages to pre compose beyond the viewport |
| `DismissThreshold` | `0.25f` | Fraction of screen height to trigger dismiss |
| `DismissVelocityThreshold` | `1000f` | Fling velocity (px/s) to trigger dismiss |

## Shared element transition

`ImageSharedTransitionConfig` wires up Compose's shared element transitions, so a tapped thumbnail animates into the full screen viewer and collapses back on dismiss. Pass **the same config** (or one that produces the same keys) to both `ImageGallery` and `ImageViewer`.

```kotlin
SharedTransitionLayout {
  AnimatedContent(
    targetState = showViewer,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "gallery-viewer",
  ) { viewerVisible ->
    val animatedContentScope = this
    if (!viewerVisible) {
      ImageGallery(
        images = imageUrls,
        onImageClick = { index, _ ->
          selectedPage = index
          showViewer = true
        },
        sharedTransition = ImageSharedTransitionConfig(
          sharedTransitionScope = this@SharedTransitionLayout,
          animatedContentScope = animatedContentScope,
        ),
      )
    } else {
      ImageViewer(
        images = imageUrls,
        state = rememberImageViewerState(
          initialPage = selectedPage,
          pageCount = { imageUrls.size },
        ),
        onDismiss = { showViewer = false },
        sharedTransition = ImageSharedTransitionConfig(
          sharedTransitionScope = this@SharedTransitionLayout,
          animatedContentScope = animatedContentScope,
        ),
      )
    }
  }
}
```

The same pattern works with `NavHost`. Use the `AnimatedContentScope` from each `composable { ... }` destination as the `animatedContentScope`.

### Custom key provider

By default, keys are `"landscapist-shared-$index-$imageModel"`. Override `keyProvider` when:

- Gallery indices don't match viewer page indices (for example, filtered lists)
- The same image model appears multiple times and you need another discriminator
- You want to namespace keys across multiple galleries on the same screen

```kotlin
ImageSharedTransitionConfig(
  sharedTransitionScope = this@SharedTransitionLayout,
  animatedContentScope = this,
  keyProvider = { index, imageModel -> "photo-$index-${imageModel.hashCode()}" },
)
```

### Enabling and disabling at runtime

Toggle `enabled` to turn the transition on or off without restructuring the composable tree:

```kotlin
ImageSharedTransitionConfig(
  sharedTransitionScope = scope,
  animatedContentScope = animatedScope,
  enabled = reduceMotionSetting.not(),
)
```

## Kotlin Multiplatform support

`landscapist-image-gallery` is a Compose Multiplatform module, and both `ImageGallery` and `ImageViewer` work on every Compose target supported by Landscapist: Android, iOS, Desktop (JVM), and Web, without any platform specific glue.

## See also

- [Landscapist Image](landscapist/landscapist-image.md): the default renderer backing both components
- [Zoomable](zoomable.md): `ZoomableConfig` options that flow into `ImageViewer`
- [Image Component and Plugin](image-component-and-plugin.md): plugin system overview
- [Placeholder](placeholder.md): shimmer and fallback plugins for gallery tiles
