# Landscapist Core

**Kotlin Multiplatform from day one.** The `landscapist-core` module is a complete, standalone image loading engine designed for Kotlin Multiplatform. Unlike the Glide, Coil, or Fresco integrations, this module provides a lightweight, self-contained solution with no external image loading library dependencies, working seamlessly across Android, iOS, Desktop, and Web.

> **See Also**: Learn more about [Why Choose Landscapist](why-choose-landscapist.md) for detailed benefits and comparisons, and [Performance Comparison](performance-comparison.md) for comprehensive benchmark results.

## Key Features

- **Built on Landscapist Core**: Powered by the standalone, platform-agnostic landscapist-core image loading engine with built-in network fetching, memory/disk caching, and image decoding
- **Full Plugin Support**: Compatible with all Landscapist plugins (Shimmer, Crossfade, CircularReveal, Blur, Palette, Zoomable, etc.) with support for combining multiple plugins
- **Compose Multiplatform**: First-class support for Android, iOS, Desktop, and Web with identical APIs and behavior across all platforms
- **Flexible Sizing**: Automatic size calculation from Compose layout constraints with support for explicit dimensions, fill strategies, and aspect ratio preservation
- **Loading States**: Built-in composable slots for loading, success, and failure states with full customization support
- **Custom Landscapist Instance**: Provide your own configured Landscapist instance via CompositionLocal for fine-grained control over caching, networking, and decoding behavior
- **Image State Callbacks**: Monitor and react to loading state changes in real-time with detailed information about data sources, load times, and errors
- **Progressive Loading**: Automatic support for progressive JPEG loading that displays low-resolution previews while the full image downloads
- **Multiple Image Sources**: Support for network URLs, local files, content URIs, drawable resources, bitmaps, byte arrays, and platform-specific sources
- **Request Customization**: Per-request configuration of cache policies, headers, priorities, transformations, and size constraints
- **Memory Efficient**: Automatic image downsampling, LRU caching with weak reference pooling, and memory pressure handling

## Installation

### Gradle (Android)

Add the dependency below to your **module**'s `build.gradle` file:

```gradle
dependencies {
    implementation("com.github.skydoves:landscapist-core:$version")
}
```

> **Note**: Ktor client dependencies are included automatically. The module bundles:
> - `ktor-client-core` for all platforms
> - `ktor-client-okhttp` for Android
> - `ktor-client-darwin` for iOS/macOS
> - `ktor-client-cio` for Desktop (JVM)
> - `ktor-client-js` for Web (Wasm)

### Kotlin Multiplatform

Add to your **module**'s `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.skydoves:landscapist-core:$version")
        }
    }
}
```

All platform-specific Ktor engines are included automatically based on your target platforms.

## Basic Usage

### Creating a Landscapist Instance

#### Android

On Android, use the builder with a Context to automatically configure disk caching. You can create an instance with default settings or customize cache sizes and other options to suit your app's needs.

```kotlin
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig

// Create with default configuration
val landscapist = Landscapist.builder(context).build()

// Or with custom configuration
val landscapist = Landscapist.builder(context)
    .config(
        LandscapistConfig(
            memoryCacheSize = 64 * 1024 * 1024L, // 64MB
            diskCacheSize = 100 * 1024 * 1024L,   // 100MB
        )
    )
    .build()
```

#### Other Platforms (iOS, Desktop, Web)

For iOS, Desktop, and Web platforms, use the singleton instance which provides a pre-configured loader. Note that disk caching requires manual directory setup on non-Android platforms.

```kotlin
import com.skydoves.landscapist.core.Landscapist

// Get the singleton instance with default configuration
val landscapist = Landscapist.getInstance()

// Or create a custom instance
val landscapist = Landscapist.builder()
    .config(
        LandscapistConfig(
            memoryCacheSize = 64 * 1024 * 1024L,
            // Note: Disk cache requires platform-specific setup
        )
    )
    .build()
```

### Loading Images

Load images by creating an ImageRequest and collecting the Flow of results. The loader automatically handles caching, downsampling based on target size, and delivers results through different states (Loading, Success, Failure).

```kotlin
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.collect

suspend fun loadImage(url: String) {
    val request = ImageRequest.builder()
        .model(url)
        .size(width = 800, height = 600)
        .build()

    landscapist.load(request).collect { result ->
        when (result) {
            is ImageResult.Loading -> {
                // Image is loading
                println("Loading...")
            }
            is ImageResult.Success -> {
                // Image loaded successfully
                val imageBitmap = result.data
                val dataSource = result.dataSource // MEMORY, DISK, or NETWORK
                println("Loaded from: $dataSource")
            }
            is ImageResult.Failure -> {
                // Failed to load image
                val error = result.reason
                println("Error: ${error.message}")
            }
        }
    }
}
```

## Configuration

### LandscapistConfig

The `LandscapistConfig` class provides extensive configuration options:

```kotlin
val config = LandscapistConfig(
    // Memory cache configuration
    memoryCacheSize = 64 * 1024 * 1024L, // 64MB (default)
    memoryCache = null, // Use custom memory cache implementation

    // Disk cache configuration
    diskCacheSize = 100 * 1024 * 1024L, // 100MB (default)
    diskCache = null, // Use custom disk cache implementation

    // Network configuration
    networkConfig = NetworkConfig(
        connectTimeout = 10.seconds,
        readTimeout = 30.seconds,
        userAgent = "MyApp/1.0",
        defaultHeaders = mapOf("Accept" to "image/*"),
        followRedirects = true,
        maxRedirects = 5
    ),

    // Image decoding configuration
    maxBitmapSize = 4096, // Maximum bitmap dimension
    allowRgb565 = true, // Use RGB_565 for images without alpha (saves memory)

    // Performance options
    weakReferencesEnabled = true, // Keep weak references to evicted cache entries

    // Event listener for monitoring
    eventListenerFactory = EventListener.Factory { request ->
        object : EventListener {
            override fun onStart(request: ImageRequest) {
                println("Started: ${request.model}")
            }

            override fun onSuccess(request: ImageRequest, result: ImageResult.Success) {
                println("Success: ${result.dataSource}")
            }

            override fun onFailure(request: ImageRequest, reason: Throwable) {
                println("Failed: ${reason.message}")
            }
        }
    },

    // Request/response interceptors
    interceptors = listOf(
        // Custom interceptor for modifying requests/responses
    )
)
```

### NetworkConfig

Configure HTTP client behavior:

```kotlin
val networkConfig = NetworkConfig(
    connectTimeout = 15.seconds,  // Connection timeout
    readTimeout = 60.seconds,     // Read timeout
    userAgent = "MyApp/2.0",      // User-Agent header
    defaultHeaders = mapOf(       // Default headers for all requests
        "Accept" to "image/*",
        "Accept-Encoding" to "gzip"
    ),
    followRedirects = true,       // Follow HTTP redirects
    maxRedirects = 5              // Maximum redirect hops
)
```

## Image Requests

### ImageRequest.Builder

Build customized image requests:

```kotlin
val request = ImageRequest.builder()
    // Source model (URL, Uri, File, etc.)
    .model("https://example.com/image.jpg")

    // Target size for downsampling
    .size(width = 1024, height = 768)

    // Cache policies
    .memoryCachePolicy(CachePolicy.ENABLED) // READ_ONLY, WRITE_ONLY, ENABLED, DISABLED
    .diskCachePolicy(CachePolicy.ENABLED)

    // Request-specific HTTP headers
    .addHeader("Authorization", "Bearer token")
    .headers(mapOf("Custom-Header" to "value"))

    // Image transformations
    .addTransformation(BlurTransformation(radius = 10))
    .transformations(listOf(transformation1, transformation2))

    // Priority (HIGH, NORMAL, LOW)
    .priority(DecodePriority.HIGH)

    // Progressive loading (emit intermediate results)
    .progressiveEnabled(true)

    // Tag for request management
    .tag("profile-image")

    .build()
```

### Supported Model Types (Android)

On Android, `landscapist-core` supports various image source types:

```kotlin
// Network URL (String)
ImageRequest.builder().model("https://example.com/image.jpg")

// Content URI
ImageRequest.builder().model(Uri.parse("content://media/external/images/1"))

// File
ImageRequest.builder().model(File("/path/to/image.jpg"))

// Drawable resource
ImageRequest.builder().model(R.drawable.image)

// Bitmap
ImageRequest.builder().model(bitmap)

// ByteArray
ImageRequest.builder().model(byteArray)

// ByteBuffer
ImageRequest.builder().model(byteBuffer)

// Drawable
ImageRequest.builder().model(drawable)
```

## Caching

### Memory Cache

The memory cache uses an LRU (Least Recently Used) eviction policy:

```kotlin
// Access the memory cache
val memoryCache = landscapist.config.memoryCache

// Clear the cache
memoryCache?.clear()

// Trim to specific size
memoryCache?.trimToSize(32 * 1024 * 1024L) // 32MB

// Get cache stats
val size = memoryCache?.size
val maxSize = memoryCache?.maxSize
```

### Disk Cache

Persistent disk cache for offline access:

```kotlin
// Disk cache is managed automatically
// Images are cached to disk on successful network loads

// Clear disk cache
val diskCache = landscapist.config.diskCache
diskCache?.clear()
```

### Cache Policies

Control caching behavior per request:

```kotlin
// Read and write to both caches
CachePolicy.ENABLED

// Only read from cache, never write
CachePolicy.READ_ONLY

// Only write to cache, never read
CachePolicy.WRITE_ONLY

// Disable cache completely
CachePolicy.DISABLED

// Example usage
val request = ImageRequest.builder()
    .model(url)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.READ_ONLY)
    .build()
```

## Progressive Loading

Progressive loading emits intermediate (blurry) results while decoding large images:

```kotlin
val request = ImageRequest.builder()
    .model(imageUrl)
    .progressiveEnabled(true) // Enable progressive loading
    .build()

landscapist.load(request).collect { result ->
    when (result) {
        is ImageResult.Success -> {
            val bitmap = result.data
            val isComplete = result.isComplete // false for progressive results
            // Update UI with intermediate bitmap
        }
    }
}
```

## Event Listeners

Monitor the image loading lifecycle:

```kotlin
class LoggingEventListener : EventListener {
    override fun onStart(request: ImageRequest) {
        Log.d("Image", "Loading started: ${request.model}")
    }

    override fun onSuccess(request: ImageRequest, result: ImageResult.Success) {
        Log.d("Image", "Loaded from ${result.dataSource} in ${result.loadDuration}ms")
    }

    override fun onFailure(request: ImageRequest, reason: Throwable) {
        Log.e("Image", "Failed to load: ${reason.message}")
    }

    override fun onCancel(request: ImageRequest) {
        Log.d("Image", "Loading cancelled")
    }
}

val config = LandscapistConfig(
    eventListenerFactory = EventListener.Factory { request ->
        LoggingEventListener()
    }
)
```

## Request Management

Cancel and track in-flight requests:

```kotlin
// Cancel all requests with a specific tag
landscapist.requestManager.cancelRequests(tag = "profile-images")

// Cancel all requests
landscapist.requestManager.cancelAll()

// Check if requests are in progress
val hasActiveRequests = landscapist.requestManager.hasActiveRequests()
```

## Memory Pressure Handling

Landscapist automatically handles memory pressure by trimming caches:

```kotlin
// Add custom memory pressure listener
landscapist.memoryPressureManager.addListener(object : MemoryPressureListener {
    override fun onMemoryPressure(level: MemoryPressureLevel) {
        when (level) {
            MemoryPressureLevel.LOW -> { /* no action */ }
            MemoryPressureLevel.MODERATE -> { /* trim some cache */ }
            MemoryPressureLevel.HIGH -> { /* trim more cache */ }
            MemoryPressureLevel.CRITICAL -> { /* clear cache */ }
        }
    }
})
```

## Custom Decoders

Implement custom image decoders for special formats:

```kotlin
class WebPDecoder : ImageDecoder {
    override suspend fun decode(
        data: ByteArray,
        targetWidth: Int?,
        targetHeight: Int?
    ): DecodeResult {
        // Custom decoding logic
        return DecodeResult.Success(imageBitmap)
    }
}

val landscapist = Landscapist.builder(context)
    .decoder(WebPDecoder())
    .build()
```

## Image Transformations

Apply transformations to loaded images:

```kotlin
class CircleCropTransformation : Transformation {
    override val key: String = "circle_crop"

    override suspend fun transform(bitmap: ImageBitmap): ImageBitmap {
        // Apply circular crop
        return croppedBitmap
    }
}

val request = ImageRequest.builder()
    .model(imageUrl)
    .addTransformation(CircleCropTransformation())
    .build()
```

## Data Sources

Track where images are loaded from:

```kotlin
landscapist.load(request).collect { result ->
    if (result is ImageResult.Success) {
        when (result.dataSource) {
            DataSource.MEMORY -> println("Loaded from memory cache")
            DataSource.DISK -> println("Loaded from disk cache")
            DataSource.NETWORK -> println("Downloaded from network")
        }
    }
}
```

## Best Practices

1. **Reuse Landscapist Instance**: Create one instance per app and reuse it
2. **Specify Target Size**: Always provide target width/height to enable downsampling
3. **Use Appropriate Cache Policies**: Disable caching for sensitive data
4. **Handle Memory Pressure**: Monitor memory usage in memory-constrained environments
5. **Cancel Requests**: Cancel unnecessary requests when navigating away
6. **Use Progressive Loading**: For large images to improve perceived performance
7. **Monitor with Event Listeners**: Track performance and errors in production

## Platform-Specific Notes

### Android
- Automatically creates disk cache in app's cache directory
- Supports all Android image sources (URIs, resources, files, etc.)
- Integrates with Android's memory pressure callbacks

### iOS
- Uses native image decoders
- Disk cache requires manual directory setup
- Memory cache is the primary caching mechanism

### Desktop
- Supports file paths and network URLs
- Configure disk cache path manually
- Uses Skia for image decoding

### Web (Wasm)
- Network URLs only
- In-memory caching only (no persistent disk cache)
- Limited transformation support

## See Also

- [Why Choose Landscapist](why-choose-landscapist.md) - Key benefits and advantages
- [Performance Comparison](performance-comparison.md) - Comprehensive benchmark results
- [Landscapist Image](landscapist-image.md) - Compose UI component built on landscapist-core
- [Image Options](../image-options.md) - Configure image display options
- [Image Component and Plugins](../image-component-and-plugin.md) - Plugin system for extending functionality
