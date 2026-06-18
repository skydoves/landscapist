# Performance Comparison

This page explains how Landscapist compares to other image loading libraries (Coil3, Glide, Fresco) and, just as important, how to reproduce those comparisons yourself. Image loading performance depends heavily on the device, OS version, and network, so this page favors reproducible methodology and verifiable artifact sizes over fixed marketing numbers.

## What this page no longer claims

Earlier versions of this page published fixed "X ms" load times and a "2.6x faster" headline. Those numbers came from a test harness that stopped the timer for Glide, Coil, and Fresco with a fixed `Thread.sleep(...)` while only Landscapist waited for its real completion callback, so the comparison was not measuring the same work for each library. That harness has been removed. The current benchmarks measure every library the same way, and we ask you to run them on your own devices rather than trust a single published figure.

## Binary size (verifiable)

Artifact size is the one comparison that is stable and reproducible, because it does not depend on a device or network. The numbers below are the release AAR size of each library's core engine module, measured from the Gradle cache for the versions this repository depends on.

| Library | Module measured | Release AAR | vs landscapist-core |
|---------|-----------------|-------------|---------------------|
| **landscapist-core** | `landscapist-core` | **313 KiB** | baseline |
| Coil3 | `coil-core` 3.5.0 | 468 KiB | +50% |
| Glide | `glide` 5.0.7 | 693 KiB | +121% |
| Fresco | core pipeline artifacts | ~1.0 MiB | roughly 3.3x |

Reproduce the landscapist-core number (byte size, so it does not vary by filesystem):

```bash
./gradlew :landscapist-core:assembleRelease
ls -l landscapist-core/build/outputs/aar/landscapist-core-release.aar   # 320,771 bytes = 313 KiB
```

The competitor numbers are the cached release AARs under `~/.gradle/caches/modules-2/files-2.1/`. The Fresco figure sums the required network-pipeline artifacts (`imagepipeline`, `fbcore`, `imagepipeline-base`, `ui-common`, `middleware`, `soloader`), which total about 1.0 MiB; a full Fresco setup with native transcoding and animation pulls in more.

Caveats, so the table is not misread:

- This measures a single module's AAR, not the full transitive footprint. landscapist-core pulls in Ktor, Okio, coroutines, and atomicfu; Coil pulls in coroutines, Okio, and a network module; Fresco is split across many artifacts (the facade `fresco` AAR is only 36 KB, but a working network pipeline needs `fbcore`, `imagepipeline`, `imagepipeline-base`, and more, which is why the realistic figure is over 1 MB).
- What ends up in your APK depends on R8 / ProGuard shrinking and which features you use.

The honest summary: landscapist-core has the smallest core artifact and the shortest direct dependency list of the four. That is its main selling point, and it is verifiable.

## Load time and memory (run it yourself)

We do not publish fixed load-time or memory numbers, because they vary too much across devices, OS versions, and networks to be meaningful as a single figure. Instead, two benchmarks are included so you can measure on your own hardware.

### 1. Instrumented load-time benchmark

`app/src/androidTest/.../ImageLibraryBenchmark.kt` measures cold-network load time for all four wrappers using one symmetric method: the timer runs from the moment the image model changes until that library's own `onImageStateChanged` callback reports a terminal state (`Success` or `Failure`). No library uses a fixed sleep, and no success flag is hardcoded. Each measured load uses a distinct URL so it is always cold, and a warmup load per library is discarded.

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.ImageLibraryBenchmark
```

A companion engine-level benchmark, `UnitPerformanceTest.kt`, measures the underlying loaders (Glide `FutureTarget`, Coil `ImageLoader.execute`, Fresco `ImagePipeline`, `Landscapist.load`) without any Compose layer, each blocking on its own real terminal result.

### 2. Macrobenchmark (frame timing and jank)

`:benchmark-landscapist` is an AndroidX Macrobenchmark that scrolls a `LazyColumn` of many images per library and records `FrameTimingMetric`. This is the correct tool for scrolling-list jank and for memory pressure, which a single-image instrumentation test cannot measure reliably.

```bash
./gradlew :benchmark-landscapist:pixel6api31BenchmarkAndroidTest
# or, on a connected device:
./gradlew :benchmark-landscapist:connectedBenchmarkAndroidTest
```

For memory, profile a scrolling run with the Android Studio Memory Profiler. A single decoded-image delta is too noisy to publish as a number.

## Architecture comparison

The core engines are in the same family. landscapist-core is a from-scratch Kotlin Multiplatform loader; the table below states what it implements relative to Coil3 honestly, including the gaps.

| Capability | landscapist-core | Coil3 |
|-----------|------------------|-------|
| Memory cache (LRU, byte-bounded) | Yes, plus a weak reference second tier | Yes |
| Disk cache | Yes (Okio based) | Yes |
| Downsampling at decode (Android) | Yes (two-pass `inSampleSize`) | Yes |
| Hardware bitmaps (Android) | Yes (opaque images, API 26+) | Yes |
| Bitmap pooling / `inBitmap` reuse | Yes (Android) | Dropped (net negative in Coil's testing) |
| Kotlin Multiplatform (Android / iOS / Desktop / Web) | Yes | Yes |
| Cancellation on composable dispose | Yes | Yes |

## When to choose which

Choose **landscapist-core / LandscapistImage** when you want a small dependency footprint, first-class Kotlin Multiplatform support across Android, iOS, Desktop, and Web, and direct control over the loading pipeline without a wrapper layer.

Choose **CoilImage (Coil3)** when you are already invested in the Coil ecosystem or rely on its extensions.

Choose **GlideImage** or **FrescoImage** for Android-only apps that already use those libraries or need their specific features (Glide's transformations and video thumbnails, Fresco's animated formats and progressive JPEG).

## See Also

- [Why Choose Landscapist](why-choose-landscapist.md) - Key benefits and advantages
- [Landscapist Core](landscapist-core.md) - Core image loading engine
- [Landscapist Image](landscapist-image.md) - Compose UI component
