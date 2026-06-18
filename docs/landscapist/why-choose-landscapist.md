# Why Choose Landscapist?

## 1. Minimal Footprint for SDKs and Libraries

Landscapist Core is **exceptionally lightweight** compared to other image loading libraries, making it the ideal choice for SDK and library developers who need to minimize their dependency footprint. When building SDKs or libraries that require image loading capabilities, the size of your dependencies directly impacts your users' APK size.

**Core engine AAR size (release build), for the versions this repo depends on:**

| Library | Module | Release AAR | vs landscapist-core |
|---------|--------|-------------|---------------------|
| **landscapist-core** | `landscapist-core` | **313 KiB** | baseline |
| Coil3 | `coil-core` 3.5.0 | 468 KiB | +50% |
| Glide | `glide` 5.0.7 | 693 KiB | +121% |
| Fresco | core pipeline artifacts | ~1.0 MiB | roughly 3.3x |

Reproduce with `./gradlew :landscapist-core:assembleRelease`. This measures a single module's AAR, not the full transitive footprint (landscapist-core also pulls in Ktor, Okio, coroutines, and atomicfu). What ends up in your APK depends on R8 shrinking and the features you use.

### Why this matters for SDKs

- **User Impact**: Every KB in your SDK adds to your users' APK size. Landscapist Core keeps your SDK lean.
- **Adoption Rate**: Developers are more likely to adopt lightweight SDKs that do not bloat their apps.
- **Multiple SDK Scenario**: When apps use multiple SDKs, each using lightweight dependencies keeps the total smaller.
- **Enterprise Requirements**: Many enterprises have strict APK size budgets. Landscapist Core helps you stay within limits.

## 2. Cross-Platform from the Start

Write your image loading code once and deploy it everywhere. `LandscapistImage` works identically across all Compose Multiplatform targets without platform-specific workarounds or conditional code. Share your image loading logic, caching configuration, and UI components across your entire application.

## 3. Full Control Over the Pipeline

Unlike wrapper libraries that hide implementation details, `LandscapistImage` exposes the entire image loading pipeline. Configure network timeouts, cache policies, image transformations, and loading priorities at both the global and per-request levels. You're not locked into preset behaviors—customize everything to match your app's specific needs.

## 4. Performance Optimized

On Android, `landscapist-core` downsamples images to the display size at decode time (two-pass `inSampleSize`), uses hardware bitmaps for opaque images on API 26+, and caches decoded bitmaps in a byte-bounded LRU with a weak reference second tier plus an Okio-based disk cache. Concurrent loads of the same image are coalesced into a single fetch and decode. The memory cache trims on system memory pressure once you register the Android memory-pressure handler.

See the [Performance Comparison](performance-comparison.md) for the benchmark methodology and how to reproduce it on your own device.

## 5. Plugin Ecosystem

Leverage the full Landscapist plugin ecosystem including shimmer placeholders, crossfade animations, blur transformations, palette extraction, and zoomable images. All plugins work seamlessly with `LandscapistImage` and can be combined in any way you need.

## See Also

- [Performance Comparison](performance-comparison.md) - Detailed performance benchmarks
- [Landscapist Core](landscapist-core.md) - Core image loading engine
- [Landscapist Image](landscapist-image.md) - Compose UI component
