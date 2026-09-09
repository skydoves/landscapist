# Performance Comparison

This page explains how Landscapist compares to other image loading libraries (Coil3, Glide, Fresco) and, just as important, how to reproduce those comparisons yourself. Image loading performance depends heavily on the device, OS version, and network, so this page favors reproducible methodology and verifiable artifact sizes over fixed marketing numbers.

## What this page no longer claims

Earlier versions of this page published fixed "X ms" load times and a "2.6x faster" headline. Those numbers came from a test harness that stopped the timer for Glide, Coil, and Fresco with a fixed `Thread.sleep(...)` while only Landscapist waited for its real completion callback, so the comparison was not measuring the same work for each library. That harness has been removed. The current benchmarks measure every library the same way, and we ask you to run them on your own devices rather than trust a single published figure.

## Binary size (verifiable)

Artifact size is the one comparison that is stable and reproducible, because it does not depend on a device or network. The numbers below are the release AAR size of each library's core engine module, measured from the Gradle cache for the versions this repository depends on.

| Library | Module measured | Release AAR | vs landscapist-core |
|---------|-----------------|-------------|---------------------|
| **landscapist-core** | `landscapist-core` | **371 KiB** | baseline |
| Coil3 | `coil-core` 3.6.2 | 469 KiB | +26% |
| Glide | `glide` 5.0.9 | 701 KiB | +89% |
| Fresco | core pipeline artifacts | 1.11 MiB | 3.06x |

Reproduce the landscapist-core number (byte size, so it does not vary by filesystem):

```bash
./gradlew :landscapist-core:assembleRelease
ls -l landscapist-core/build/outputs/aar/landscapist-core-release.aar   # 379,990 bytes = 371 KiB
```

The competitor numbers are the cached release AARs under `~/.gradle/caches/modules-2/files-2.1/`, at the versions this repository pins. The Fresco figure sums the required network-pipeline artifacts (`imagepipeline`, `fbcore`, `imagepipeline-base`, `ui-common`, `middleware`, `soloader`), which total 1,163,224 bytes; a full Fresco setup with native transcoding and animation pulls in more.

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

Two engine-level benchmarks measure the underlying loaders without any Compose layer, each blocking on its own real terminal result. `UnitPerformanceTest.kt` runs against a fixed remote image, and `EngineSpeedBenchmark.kt` runs against a local server (point it at one with `-e baseUrl http://127.0.0.1:PORT` over `adb reverse`) so the timing isolates loader overhead from network noise with nanosecond resolution.

### Decode throughput on Android (qualitative, not a published figure)

These observations come from our own engine-level runs on Android and will differ on your hardware, so we state them as direction rather than numbers:

- For a single cold fetch and decode, landscapist-core is on par with Coil3. Neither is meaningfully faster than the other at a matched bitmap config.
- Glide tends to win on repeated decodes of the same size (the scrolling case), because it reuses bitmap buffers through an `inBitmap` pool. That is a trade of memory for speed that Coil deliberately forgoes too, and landscapist-core does not match Glide's pooled decode throughput.
- Measure with a matched configuration. Hardware bitmaps (the Android default) decode slowly under an emulator's software GPU, which makes any emulator number unrepresentative of a real device, so compare matched configs and prefer a physical device for absolute figures.

The honest positioning, and it is worth being precise about which "footprint" is meant. The AAR is
smaller; the runtime memory is not. landscapist-core keys its memory cache on the size an image is
drawn at, where Coil leaves the size out of the key and keeps one entry per image. A request with no
entry of its own is served by an already decoded variant of the same image that covers it on both
axes and is no more than twice its size, so sizes a pixel or two apart share one bitmap, but a
thumbnail and a detail view are still two entries. Forty images each asked for at a thumbnail, a
list row, a jittered grid and a detail size gives it three times the entries and 1.27 times the
bytes; one image asked for at fifteen sizes between 352 and 366 px gives it eight entries against
Coil's one, and 7.7 times the bytes. What it buys is that a 96 px slot is answered with 96 px, where
Coil answers it with whatever it has, which can be the 720 px detail bitmap and 56 times the pixels
to sample on every frame that draws it.

The rows below are measured rather than qualitative, and you can reproduce them with
`./gradlew :benchmark-engine:run`. They are one JVM on one machine against Coil 3.6.2 running in
the same process, so treat them as the shape of the difference rather than as figures to quote.

Landscapist allocates 750 to 805 B per memory cache hit against Coil's 1.6 KiB, and reaches the
network once where Coil reaches it 32 times for 32 concurrent requests for one image.

It decodes a 4000x3000 JPEG down to 400x300 in about 41 ms against about 49 ms, and that row is a
model rather than a head to head: the comparison is against a hand written `Image.makeFromEncoded`
plus a canvas scale, which is what Coil's `SkiaImageDecoder` does, not against Coil itself.

It loses on a cold load, to a thread hop it takes deliberately so that blocking disk reads stay off
the caller's thread. The size of that loss is not quoted here because the timing rows do not
reproduce closely enough across JVM invocations to quote.

What the Compose layer allocates per frame, for twenty images, as medians above an empty scene.
"Before" is the release before this one, on the same benchmark and the same machine.

| KiB per frame | before | after | coil 3.6.2 |
|---|---|---|---|
| first frame | 353.5 | 86.7 | 109.5 |
| resize frame | 34.7 | 5.5 | 5.8 |
| first frame, crossfade | 402.6 | 130.2 | 109.5 |
| first frame, success slot | 335.7 | 266.7 | 147.8 painter, 548.8 subcompose |
| first frame, painter | new | 118.2 | 147.8 |
| resize frame, painter | new | 13.7 | 72.2 |

The first two rows are an image with no plugin and no slot, which is the one drawn by a single
layout node. The rows named painter are `rememberImagePainter` against Coil's
`rememberAsyncImagePainter`, both inside a plain `Image`.

Two of those rows are not comparisons. `resize frame, painter` is one: the Coil arm keys its
request on a size that changes every frame, so it rebuilds the request and restarts the load on each
of them, and the benchmark prints as much next to the number ("that is the row's cost, not the
painter's"). `AsyncImage` does not work that way, and the row should not be read as a painter
against a painter.

The other is the crossfade row, and it is not a fade against a fade. Both sides are warm
there, and Coil declines to fade when `result.dataSource == DataSource.MEMORY_CACHE`, so its column
is what it costs to decide not to fade. That check sits in `CrossfadeTransition.Factory` on
Android and in `AsyncImagePainter.nonAndroid` on the platform this benchmark runs on, with the
same comment above it in both. Landscapist reads the cache while it composes,
so it has no loading state to fade out of either and does not run one. What the row compares is what
each library pays to have a crossfade installed that neither runs, and 49.5 KiB of that is
landscapist's.

None of the rows above is measured on Android, and the decoders there are different code on both
sides, so the section below is the one to read for the platform that ships.

### On a device, against Coil 3.6.2

Taken on an emulator, with a fresh process for each measured loader: whichever loader ran second in
a process read as faster by more than the difference being measured, and warming both stacks first
did not fix it. An emulator, so these are the shape of the difference rather than figures to quote.
Each cell is the spread across the runs that were taken.

| | landscapist | coil 3.6.2 |
|---|---|---|
| first image on screen, 20 composed at once | 138, 124 ms | 99, 98 ms |
| until all 20 report success | 241, 246 ms | 166, 174 ms |
| proportional set above resting, 20 images | 59.4, 59.5 MiB | 45.5, 33.0 MiB |
| decode 2000x1500 to 200x150, median of 8 | 34.0, 35.7, 36.5, 34.4 ms | 22.4, 24.9, 23.9, 23.1 ms |
| the same decode, allocated | 5.45 MiB | 1.84 MiB |
| scroll, 8 swipes over 240 rows, allocated | 44.4, 46.3, 46.3 MiB | 18.7, 18.4 MiB |

The scroll row used to run 60 rows, where both caches held everything and it measured nothing. At
240 the caches evict. It counts what each arm loaded and what it fetched, off each library's own
state callback, because a row that never loaded would otherwise report the allocation of scrolling
an empty list and win on it.

Those counts say something the allocation does not. Both arms put the same 154 of 240 rows on
screen. Landscapist fetches and decodes 136 of them where Coil fetches 78, repeating across runs, so
the two draw the same images and landscapist decodes nearly twice as many. Read its 2.4x as a memory
cache hit rate rather than as a per row overhead.

So: ahead on every JVM row above, behind on cold load, on decode and on resident memory here. The
Android decode path is the same code it has been, so that row is not a regression, but it is the
reverse of what the desktop numbers say and it is the platform that ships.

### 2. Macrobenchmark (frame timing and jank)

`:benchmark-landscapist` is an AndroidX Macrobenchmark that scrolls a `LazyColumn` of many images per library and records `FrameTimingMetric`. That is the right tool for scrolling-list jank and for memory pressure, which a single-image instrumentation test cannot measure reliably.

The module used to compare landscapist to itself: its Coil tab ran `CoilImage`, this library's own wrapper; its Landscapist tab installed eight plugins, which is the composed path rather than the single node one; the app opened on the Landscapist tab, so that tab composed and fetched inside every measured block including Coil's; and the driver waited on `By.res(packageName, tag)` while `testTagsAsResourceId` publishes the tag with no package prefix, so the wait never matched anything. All of that is fixed, and the driver now also waits for a marker the app publishes only once rows have reported a loaded image, since a composed row is not a loaded one.

What a fixed run says is that frame timing is a tie: medians within a tenth of a millisecond and upper percentiles that swing both ways between runs. Nothing is claimed from it in either direction.

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
| Memory cache (LRU, byte-bounded) | Yes, plus a weak reference second tier | Yes, with the same second tier |
| Disk cache | Yes (Okio based) | Yes |
| Downsampling at decode (Android) | Yes (two-pass `inSampleSize`) | Yes |
| Hardware bitmaps (Android) | Yes (opaque images, API 26+) | Yes |
| Bitmap pooling / `inBitmap` reuse | No. `GlobalBitmapPool` exists and the Android decoders ask it for a bitmap, but nothing in the library ever puts one in | Dropped (net negative in Coil's testing) |
| Kotlin Multiplatform (Android / iOS / Desktop / Web) | Yes | Yes |
| Cancellation on composable dispose | Yes | Yes |

Two notes on that first row, because it reads as a difference and is not one. Coil keeps the same
weak reference tier, enabled by default, so an evicted entry stays reachable there on both sides.
And "byte-bounded" bounds only what each cache reports on either side: an entry evicted from the
strong tier stays reachable through the weak one until the collector clears it, and neither library
counts it while it is. What a cache reports is what it holds strongly, not what is in the heap.

## When to choose which

Choose **landscapist-core / LandscapistImage** when you want a small dependency footprint, first-class Kotlin Multiplatform support across Android, iOS, Desktop, and Web, and direct control over the loading pipeline without a wrapper layer.

Choose **CoilImage (Coil3)** when you are already invested in the Coil ecosystem or rely on its extensions.

Choose **GlideImage** or **FrescoImage** for Android-only apps that already use those libraries or need their specific features (Glide's transformations and video thumbnails, Fresco's animated formats and progressive JPEG).

## See Also

- [Why Choose Landscapist](why-choose-landscapist.md) - Key benefits and advantages
- [Landscapist Core](landscapist-core.md) - Core image loading engine
- [Landscapist Image](landscapist-image.md) - Compose UI component
