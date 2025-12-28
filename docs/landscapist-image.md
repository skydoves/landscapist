# Landscapist Image

**Compose Multiplatform from day one.** The `landscapist-image` module provides a powerful, production-ready Compose Multiplatform UI component for loading and displaying images using the `landscapist-core` engine. Unlike platform-specific solutions like GlideImage (Android-only) or FrescoImage (Android-only), LandscapistImage is built from the ground up for Kotlin Multiplatform and Compose Multiplatform, enabling you to write your image loading code once and deploy it across Android, iOS, Desktop, and Web platforms.

Built on top of the standalone `landscapist-core` image loading engine, LandscapistImage gives you complete control over the entire image loading pipeline—from network requests to caching strategies to image transformations—while maintaining seamless compatibility with all Landscapist plugins. This means you get the power and flexibility of a custom image loader combined with the convenience of a high-level Compose API.

## Installation

### Gradle (Android)

Add the dependency below to your **module**'s `build.gradle` file:

```gradle
dependencies {
    implementation("com.github.skydoves:landscapist-image:$version")

    // Optional: Add plugins you want to use
    implementation("com.github.skydoves:landscapist-placeholder:$version")
    implementation("com.github.skydoves:landscapist-animation:$version")
}
```

> **Note**: This module depends on `landscapist-core`, which includes Ktor client automatically. No need to add Ktor dependencies separately.

### Kotlin Multiplatform

Add to your **module**'s `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.skydoves:landscapist-image:$version")

            // Optional plugins
            implementation("com.github.skydoves:landscapist-placeholder:$version")
            implementation("com.github.skydoves:landscapist-animation:$version")
        }
    }
}
```

## Basic Usage

### Simple Image Loading

The simplest way to load and display an image is to provide an image model (typically a URL) and a modifier specifying the size. LandscapistImage will automatically fetch the image from the network, cache it in memory and on disk, decode it to fit the specified dimensions, and display it in your UI.

```kotlin
import com.skydoves.landscapist.image.LandscapistImage

LandscapistImage(
    imageModel = { "https://example.com/image.jpg" },
    modifier = Modifier.size(200.dp)
)
```

**What happens behind the scenes:**
1. **Size Calculation**: The modifier's size constraints are measured during composition
2. **Cache Check**: Memory cache is checked first for an existing bitmap at the requested size
3. **Disk Cache**: If not in memory, disk cache is checked for the downloaded image data
4. **Network Fetch**: If not cached, the image is downloaded via Ktor HTTP client
5. **Decoding**: The image is decoded and downsampled to match the display size (reducing memory usage)
6. **Caching**: The decoded bitmap is stored in memory cache, and raw data is stored in disk cache
7. **Display**: The image is rendered to the screen

### With ImageOptions

ImageOptions provides fine-grained control over how the loaded image is displayed. This is separate from the loading pipeline and purely affects the visual presentation once the image is ready.

```kotlin
import com.skydoves.landscapist.ImageOptions
import androidx.compose.ui.layout.ContentScale

LandscapistImage(
    imageModel = { imageUrl },
    modifier = Modifier
        .fillMaxWidth()
        .height(250.dp),
    imageOptions = ImageOptions(
        contentScale = ContentScale.Crop,        // How to scale the image within bounds
        alignment = Alignment.Center,             // Where to position the image
        contentDescription = "Profile picture",   // Accessibility description
        colorFilter = ColorFilter.tint(Color.Red), // Apply color filter
        alpha = 0.8f                               // Image opacity (0.0 to 1.0)
    )
)
```

**ContentScale options and their use cases:**
- **ContentScale.Crop**: Fills the container completely, cropping the image if needed (best for hero images, thumbnails)
- **ContentScale.Fit**: Scales the image to fit within the container without cropping (best for full image viewing)
- **ContentScale.FillWidth**: Fills the width of the container, may crop height (best for banners)
- **ContentScale.FillHeight**: Fills the height of the container, may crop width (best for vertical layouts)
- **ContentScale.Inside**: Scales down only if the image is larger than the container (best for variable-sized images)
- **ContentScale.None**: Displays the image at its original size (best for pixel-perfect graphics)

## Using Plugins

Plugins are modular, composable components that extend LandscapistImage's functionality without modifying its core behavior. They operate on the image loading lifecycle, allowing you to add placeholders, animations, transformations, and other effects. Multiple plugins can be combined and are applied in the order they're added.

### Shimmer Placeholder

The ShimmerPlugin displays an animated shimmer effect while your image loads, providing visual feedback to users that content is loading. This creates a more polished experience compared to blank spaces or static placeholders. The shimmer animation is highly customizable with control over colors, animation duration, dropoff intensity, and tilt angle.

```kotlin
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin

LandscapistImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        +ShimmerPlugin(
            baseColor = Color.DarkGray,          // Background color of the shimmer
            highlightColor = Color.LightGray,    // Color of the shimmer highlight
            durationMillis = 500,                 // Animation duration (lower = faster)
            dropOff = 0.65f,                      // How quickly the highlight fades (0.0-1.0)
            tilt = 20f                            // Angle of the shimmer effect in degrees
        )
    }
)
```

**Customization tips:**
- Use brand colors for `baseColor` and `highlightColor` to match your app's theme
- Reduce `durationMillis` (e.g., 350-400ms) for a snappier feel on fast networks
- Increase `dropOff` (e.g., 0.8-0.9) for a more subtle, gentle shimmer
- Adjust `tilt` (0°-45°) to change the shimmer's direction—0° is horizontal, 90° is vertical

### Crossfade Animation

Smoothly crossfade between placeholder and loaded image:

```kotlin
import com.skydoves.landscapist.animation.crossfade.CrossfadePlugin

LandscapistImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        +CrossfadePlugin(
            duration = 550 // milliseconds
        )
    }
)
```

### Circular Reveal Animation

Reveal the image with a circular animation:

```kotlin
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin

LandscapistImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        +CircularRevealPlugin(
            duration = 800 // milliseconds
        )
    }
)
```

### Palette Extraction

Extract dominant colors from the image:

```kotlin
import com.skydoves.landscapist.palette.PalettePlugin

var palette by remember { mutableStateOf<Palette?>(null) }

LandscapistImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        +PalettePlugin { extractedPalette ->
            palette = extractedPalette
        }
    }
)

// Use extracted colors
palette?.let {
    val dominantColor = Color(it.dominantSwatch?.rgb ?: 0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(dominantColor)
    )
}
```

### Blur Transformation

Apply blur effect to images:

```kotlin
import com.skydoves.landscapist.transformation.blur.BlurTransformationPlugin

LandscapistImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        +BlurTransformationPlugin(
            radius = 15 // blur radius
        )
    }
)
```

### Combining Multiple Plugins

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        // Plugins are applied in order
        +ShimmerPlugin()
        +CrossfadePlugin(duration = 550)
        +PalettePlugin { palette -> /* ... */ }
        +CircularRevealPlugin()
    }
)
```

## Custom Loading States

### Loading Composable

Show a custom composable while the image loads:

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    loading = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colors.primary
            )
        }
    }
)
```

### Success Composable

Customize how the loaded image is displayed:

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    success = { state, painter ->
        Image(
            painter = painter,
            contentDescription = state.imageBitmap?.let { "Image loaded" },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    }
)
```

### Failure Composable

Show a custom error state:

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    failure = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "Failed to load",
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "Failed to load image",
                    color = Color.Gray,
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
)
```

### All States Combined

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    modifier = Modifier
        .fillMaxWidth()
        .height(200.dp),
    loading = {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    },
    success = { _, painter ->
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
    },
    failure = {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.LightGray)
        ) {
            Text(
                text = "Error",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
)
```

## Image State Changes

Monitor state changes with a callback:

```kotlin
var currentState by remember { mutableStateOf<LandscapistImageState>(LandscapistImageState.None) }
var loadTime by remember { mutableStateOf(0L) }
val startTime = remember { System.currentTimeMillis() }

LandscapistImage(
    imageModel = { imageUrl },
    onImageStateChanged = { state ->
        currentState = state
        if (state is LandscapistImageState.Success) {
            loadTime = System.currentTimeMillis() - startTime
        }
    }
)

// Display state information
when (currentState) {
    is LandscapistImageState.None -> Text("Ready to load")
    is LandscapistImageState.Loading -> Text("Loading...")
    is LandscapistImageState.Success -> {
        val success = currentState as LandscapistImageState.Success
        Text("Loaded in ${loadTime}ms from ${success.dataSource}")
    }
    is LandscapistImageState.Failure -> {
        val failure = currentState as LandscapistImageState.Failure
        Text("Failed: ${failure.reason.message}")
    }
}
```

## Custom Landscapist Instance

### Providing a Custom Instance

Use `LocalLandscapist` to provide a custom Landscapist instance to your composition tree:

```kotlin
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.image.LocalLandscapist
import androidx.compose.runtime.CompositionLocalProvider

// Create custom Landscapist instance
val customLandscapist = Landscapist.builder(context)
    .config(
        LandscapistConfig(
            memoryCacheSize = 128 * 1024 * 1024L, // 128MB
            diskCacheSize = 200 * 1024 * 1024L,    // 200MB
            networkConfig = NetworkConfig(
                connectTimeout = 15.seconds,
                userAgent = "MyApp/1.0"
            )
        )
    )
    .build()

// Provide to composition tree
CompositionLocalProvider(LocalLandscapist provides customLandscapist) {
    // All LandscapistImage composables in this tree will use customLandscapist
    LandscapistImage(
        imageModel = { imageUrl }
    )
}
```

### Per-Request Configuration

Customize individual requests:

```kotlin
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.CachePolicy

LandscapistImage(
    imageModel = { imageUrl },
    requestBuilder = {
        // Customize this specific request
        size(width = 1024, height = 768)
        memoryCachePolicy(CachePolicy.DISABLED)
        addHeader("Authorization", "Bearer token")
        priority(DecodePriority.HIGH)
    }
)
```

## Supported Image Sources

### Android

`LandscapistImage` on Android supports a wide variety of image sources:

```kotlin
// Network URLs
LandscapistImage(imageModel = { "https://example.com/image.jpg" })

// Content URIs
val contentUri = Uri.parse("content://media/external/images/1")
LandscapistImage(imageModel = { contentUri })

// File paths
val file = File("/storage/emulated/0/image.jpg")
LandscapistImage(imageModel = { file })

// Drawable resources
LandscapistImage(imageModel = { R.drawable.profile_placeholder })

// Asset files
LandscapistImage(imageModel = { "file:///android_asset/image.png" })

// Bitmap instances
val bitmap: Bitmap = ...
LandscapistImage(imageModel = { bitmap })

// Byte arrays
val bytes: ByteArray = ...
LandscapistImage(imageModel = { bytes })

// Drawable instances
val drawable: Drawable = ...
LandscapistImage(imageModel = { drawable })
```

### iOS, Desktop, Web

On other platforms, supported sources depend on the platform:

```kotlin
// Network URLs (all platforms)
LandscapistImage(imageModel = { "https://example.com/image.jpg" })

// File paths (Desktop, iOS)
LandscapistImage(imageModel = { "/path/to/image.jpg" })

// Platform-specific models
// Check landscapist-core documentation for details
```

## Sizing Behavior

### Explicit Size

Provide explicit dimensions:

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    modifier = Modifier.size(300.dp)
)
```

### Fill Available Space

Fill the parent container:

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
)
```

### Aspect Ratio

Maintain aspect ratio:

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
)
```

### Placeholder Aspect Ratio

Reserve space before the image loads:

```kotlin
LandscapistImage(
    imageModel = { imageUrl },
    imageOptions = ImageOptions(
        placeholderAspectRatio = 16f / 9f
    ),
    modifier = Modifier.fillMaxWidth()
)
```

## Advanced Usage

### Lazy Column with Images

Efficiently load images in a scrolling list:

```kotlin
LazyColumn {
    items(imageUrls) { url ->
        LandscapistImage(
            imageModel = { url },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(8.dp),
            component = rememberImageComponent {
                +ShimmerPlugin()
                +CrossfadePlugin()
            }
        )
    }
}
```

### Hero Image with Fade-in

```kotlin
var isImageLoaded by remember { mutableStateOf(false) }

Box(modifier = Modifier.fillMaxSize()) {
    LandscapistImage(
        imageModel = { heroImageUrl },
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isImageLoaded) 1f else 0f),
        onImageStateChanged = { state ->
            isImageLoaded = state is LandscapistImageState.Success
        }
    )

    if (!isImageLoaded) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
```

### Profile Picture with Placeholder

```kotlin
LandscapistImage(
    imageModel = { profileImageUrl },
    modifier = Modifier
        .size(80.dp)
        .clip(CircleShape)
        .border(2.dp, MaterialTheme.colors.primary, CircleShape),
    imageOptions = ImageOptions(
        contentScale = ContentScale.Crop
    ),
    component = rememberImageComponent {
        +ShimmerPlugin()
    },
    failure = {
        // Show default avatar on failure
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Default avatar",
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .padding(16.dp),
            tint = Color.Gray
        )
    }
)
```

### Zoomable Image

Combine with ZoomablePlugin for pinch-to-zoom:

```kotlin
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import com.skydoves.landscapist.zoomable.rememberZoomableState

val zoomableState = rememberZoomableState()

LandscapistImage(
    imageModel = { largeImageUrl },
    modifier = Modifier.fillMaxSize(),
    component = rememberImageComponent {
        +ZoomablePlugin(state = zoomableState)
    }
)
```

## Migration Guide

### From GlideImage/CoilImage/FrescoImage

Migrating from other image loaders is straightforward:

```kotlin
// Before (GlideImage)
GlideImage(
    imageModel = { imageUrl },
    modifier = Modifier.size(200.dp)
)

// After (LandscapistImage)
LandscapistImage(
    imageModel = { imageUrl },
    modifier = Modifier.size(200.dp)
)
```

The API is intentionally similar for easy migration. Most parameters work identically.

### Plugin Migration

All plugins work the same way:

```kotlin
// Before (with GlideImage)
GlideImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        +ShimmerPlugin()
        +CrossfadePlugin()
    }
)

// After (with LandscapistImage)
LandscapistImage(
    imageModel = { imageUrl },
    component = rememberImageComponent {
        +ShimmerPlugin()
        +CrossfadePlugin()
    }
)
```

## Common Patterns and Recipes

### Image Grid with Different Sizes

Load images efficiently in a grid where different items have different dimensions:

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    contentPadding = PaddingValues(8.dp)
) {
    items(imageList) { item ->
        LandscapistImage(
            imageModel = { item.url },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(item.aspectRatio)
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp)),
            imageOptions = ImageOptions(
                contentScale = ContentScale.Crop
            ),
            component = rememberImageComponent {
                +ShimmerPlugin()
                +CrossfadePlugin()
            }
        )
    }
}
```

### Avatar with Loading Indicator and Fallback

Implement a complete avatar component with loading state, error fallback, and circular clipping:

```kotlin
@Composable
fun Avatar(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(modifier = modifier.size(size)) {
        LandscapistImage(
            imageModel = { imageUrl },
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            imageOptions = ImageOptions(
                contentScale = ContentScale.Crop,
                contentDescription = "Avatar for $name"
            ),
            loading = {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(size / 2)
                        .align(Alignment.Center),
                    strokeWidth = 2.dp
                )
            },
            failure = {
                // Show first letter of name as fallback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colors.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.h6
                    )
                }
            }
        )
    }
}
```

### Full-Screen Image Viewer with Pinch-to-Zoom

Create a full-screen image viewer with zoom and pan capabilities:

```kotlin
@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val zoomableState = rememberZoomableState(
        minScale = 1f,
        maxScale = 5f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
    ) {
        LandscapistImage(
            imageModel = { imageUrl },
            modifier = Modifier.fillMaxSize(),
            imageOptions = ImageOptions(
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            ),
            component = rememberImageComponent {
                +ZoomablePlugin(
                    state = zoomableState,
                    enableSubSampling = true  // For very large images
                )
            },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading high resolution image...", color = Color.White)
                    }
                }
            }
        )

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }

        // Zoom indicator
        Text(
            text = "Zoom: ${(zoomableState.scale * 100).toInt()}%",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
```

### Product Image with Palette-Based UI

Extract colors from product images to create dynamic, branded UI:

```kotlin
@Composable
fun ProductCard(product: Product) {
    var palette by remember { mutableStateOf<Palette?>(null) }
    val backgroundColor = remember(palette) {
        palette?.vibrantSwatch?.rgb?.let { Color(it) } ?: Color.LightGray
    }
    val textColor = remember(palette) {
        palette?.vibrantSwatch?.titleTextColor?.let { Color(it) } ?: Color.Black
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        backgroundColor = backgroundColor.copy(alpha = 0.1f)
    ) {
        Column {
            LandscapistImage(
                imageModel = { product.imageUrl },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop
                ),
                component = rememberImageComponent {
                    +PalettePlugin { extractedPalette ->
                        palette = extractedPalette
                    }
                    +CrossfadePlugin()
                }
            )

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.h6,
                    color = textColor
                )
                Text(
                    text = product.price,
                    style = MaterialTheme.typography.body1,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
```

### Infinite Scrolling Feed with Prefetching

Implement an efficient infinite scroll feed that prefetches images:

```kotlin
@Composable
fun ImageFeed(
    items: List<FeedItem>,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    val landscapist = LocalLandscapist.current

    // Prefetch next images when user is near the end
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= items.size - 3) {
                    onLoadMore()
                    // Prefetch next images
                    items.drop(lastVisibleIndex + 1).take(5).forEach { item ->
                        landscapist.load(
                            ImageRequest.builder()
                                .model(item.imageUrl)
                                .size(width = 1080, height = 1080)
                                .build()
                        ).collect { /* Prefetch only */ }
                    }
                }
            }
    }

    LazyColumn(state = listState) {
        items(items) { item ->
            LandscapistImage(
                imageModel = { item.imageUrl },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                component = rememberImageComponent {
                    +ShimmerPlugin()
                }
            )
        }
    }
}
```

## Performance Optimization

### Sizing Best Practices

Always provide explicit size constraints to enable proper image downsampling and prevent memory waste:

```kotlin
// ✅ GOOD: Explicit size allows downsampling
LandscapistImage(
    imageModel = { hugeImageUrl },
    modifier = Modifier.size(200.dp)  // Image downsampled to ~200x200 pixels
)

// ❌ BAD: No size constraints, loads full resolution
LandscapistImage(
    imageModel = { hugeImageUrl }
    // May load 4000x4000 image into memory!
)

// ✅ GOOD: fillMaxWidth with aspectRatio
LandscapistImage(
    imageModel = { imageUrl },
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)  // Size can be calculated
)
```

### Memory Optimization for Large Lists

Configure aggressive caching policies for scrolling lists to prevent memory issues:

```kotlin
val customLandscapist = remember {
    Landscapist.builder(context)
        .config(
            LandscapistConfig(
                memoryCacheSize = 50 * 1024 * 1024L,  // 50MB - smaller for lists
                weakReferencesEnabled = true,          // Enable weak reference pool
                allowRgb565 = true                     // Use 16-bit color (saves 50% memory)
            )
        )
        .build()
}

CompositionLocalProvider(LocalLandscapist provides customLandscapist) {
    LazyColumn {
        items(1000) { index ->
            LandscapistImage(
                imageModel = { items[index].url },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                requestBuilder = {
                    // Disable memory cache for list items that scroll off screen
                    memoryCachePolicy(CachePolicy.READ_ONLY)
                }
            )
        }
    }
}
```

### Network Performance

Optimize network loading with custom Ktor configuration:

```kotlin
val landscapist = Landscapist.builder(context)
    .config(
        LandscapistConfig(
            networkConfig = NetworkConfig(
                connectTimeout = 5.seconds,       // Fast fail for poor connections
                readTimeout = 30.seconds,         // Reasonable timeout for large images
                followRedirects = true,
                maxRedirects = 3,                 // Prevent redirect loops
                defaultHeaders = mapOf(
                    "Accept" to "image/webp,image/jpeg,image/png,image/*",
                    "Accept-Encoding" to "gzip, deflate"
                )
            )
        )
    )
    .build()
```

### Progressive Loading for Better UX

Enable progressive loading for large JPEG images to show previews while downloading:

```kotlin
LandscapistImage(
    imageModel = { largeImageUrl },
    modifier = Modifier.fillMaxSize(),
    requestBuilder = {
        progressiveEnabled(true)  // Show progressive previews
        priority(DecodePriority.HIGH)  // Prioritize this image
    },
    onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !state.isComplete) {
            // Showing progressive preview
            Log.d("Image", "Progressive preview loaded")
        }
    }
)
```

## Troubleshooting

### Images Not Loading

**Problem**: Images don't appear, no error shown
**Solutions**:
1. Check internet permission in AndroidManifest.xml:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```
2. Verify the URL is accessible (check CORS for web)
3. Add an `onImageStateChanged` callback to see exact errors:
   ```kotlin
   LandscapistImage(
       imageModel = { url },
       onImageStateChanged = { state ->
           when (state) {
               is LandscapistImageState.Failure -> {
                   Log.e("Image", "Failed to load: ${state.reason}")
               }
               else -> {}
           }
       }
   )
   ```

### Out of Memory Errors

**Problem**: App crashes with OutOfMemoryError when loading large images
**Solutions**:
1. Always specify size constraints:
   ```kotlin
   modifier = Modifier.size(300.dp)  // Not Modifier.fillMaxSize()!
   ```
2. Reduce memory cache size:
   ```kotlin
   LandscapistConfig(memoryCacheSize = 32 * 1024 * 1024L)
   ```
3. Enable RGB_565 for images without transparency:
   ```kotlin
   LandscapistConfig(allowRgb565 = true)
   ```

### Slow Loading in Lists

**Problem**: Images load slowly when scrolling through a list
**Solutions**:
1. Use appropriate cache policies:
   ```kotlin
   requestBuilder = {
       memoryCachePolicy(CachePolicy.ENABLED)
       diskCachePolicy(CachePolicy.ENABLED)
   }
   ```
2. Prefetch images before they're visible (see "Infinite Scrolling Feed" example above)
3. Use smaller thumbnail URLs when available:
   ```kotlin
   imageModel = { item.thumbnailUrl ?: item.fullUrl }
   ```

### Images Pixelated or Blurry

**Problem**: Images appear low quality despite being high resolution
**Solutions**:
1. Check that size constraints match the display size:
   ```kotlin
   // If displaying at 500x500dp, request that size
   modifier = Modifier.size(500.dp)
   ```
2. Disable downsampling for specific images:
   ```kotlin
   requestBuilder = {
       size(width = Int.MAX_VALUE, height = Int.MAX_VALUE)
   }
   ```
3. Use appropriate ContentScale:
   ```kotlin
   imageOptions = ImageOptions(contentScale = ContentScale.Fit)
   ```

### Crossfade Animation Not Working

**Problem**: CrossfadePlugin doesn't animate
**Solutions**:
1. Ensure the image isn't already in cache (cache hits skip the animation)
2. Clear cache for testing:
   ```kotlin
   landscapist.config.memoryCache?.clear()
   ```
3. Increase duration to make it visible:
   ```kotlin
   +CrossfadePlugin(duration = 1000)  // 1 second
   ```

## Best Practices

1. **Always Provide Image Size**: Specify size constraints using modifiers to enable automatic downsampling and prevent excessive memory usage. This is the single most important optimization.

2. **Use Plugins Wisely**: While plugins are powerful, each adds processing overhead. Only use plugins that provide value to your specific use case. Avoid combining more than 3-4 plugins on a single image.

3. **Handle All States**: Always provide loading and failure composables for better UX. Users should never see blank spaces or wonder if something is wrong.

4. **Reuse Components**: Use `rememberImageComponent` outside of loops when the same plugin configuration is used for multiple images. This prevents unnecessary recomposition.

5. **Configure Caching Appropriately**: Customize memory and disk cache sizes based on your app's needs. A typical app might use 64MB memory + 150MB disk, but adjust based on your image sizes and quantities.

6. **Monitor Performance**: Use `onImageStateChanged` to track loading times, cache hit rates, and failures. Log this data in development to identify optimization opportunities.

7. **Optimize for Lists**: When displaying images in scrolling lists, use smaller cache sizes, enable weak references, and consider READ_ONLY cache policy to prevent cache thrashing.

8. **Test on Low-End Devices**: Always test image loading on devices with limited memory and slow networks to ensure a good experience for all users.

9. **Use ContentDescription**: Always provide accessibility descriptions via `ImageOptions.contentDescription` for screen reader support.

10. **Consider Progressive Loading**: For large images (>1MB), enable progressive loading to improve perceived performance by showing low-resolution previews quickly.

## Limitations

- **Progressive loading**: Only works with progressive JPEG images from network sources. PNG, WebP, and local images don't support progressive loading.
- **Animated images**: GIF animation support is platform-dependent. Android uses native decoders, while other platforms may only show the first frame. WebP animation support varies by platform and OS version.
- **Platform differences**: Some features are platform-specific:
  - Content URIs: Android only
  - Drawable resources: Android only
  - Asset files: Platform-specific paths
  - SubSampling (for very large images): Best support on Android
- **Memory constraints**: Very large images (>4096x4096) may fail to load on some devices due to GPU texture size limits or memory constraints

## See Also

- [Landscapist Core](landscapist-core.md) - The underlying image loading engine
- [Image Options](image-options.md) - Configure image display options
- [Image Component and Plugins](image-component-and-plugin.md) - Plugin system details
- [Placeholder](placeholder.md) - Placeholder options
- [Animation](animation.md) - Animation plugins
- [Zoomable](zoomable.md) - Zoomable image support
- [Palette](palette.md) - Color extraction
