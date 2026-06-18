# Image Loading Performance

Landscapist's positioning is a lightweight, Kotlin Multiplatform image loader. This page summarizes what is measurable and points to the benchmarks you can run yourself. Load-time and memory numbers are intentionally not hardcoded here, because they depend on the device, OS version, and network.

## Binary size (verifiable)

Release AAR size of each library's core engine module, for the versions this repository depends on:

| Library | Module | Release AAR | vs landscapist-core |
|---------|--------|-------------|---------------------|
| **landscapist-core** | `landscapist-core` | **313 KiB** | baseline |
| Coil3 | `coil-core` 3.5.0 | 468 KiB | +50% |
| Glide | `glide` 5.0.7 | 693 KiB | +121% |
| Fresco | core pipeline artifacts | ~1.0 MiB | roughly 3.3x |

Reproduce:

```bash
./gradlew :landscapist-core:assembleRelease
ls -l landscapist-core/build/outputs/aar/landscapist-core-release.aar   # 320,771 bytes = 313 KiB
```

Note: this is a single module's AAR, not the full transitive footprint. See [docs/landscapist/performance-comparison.md](../docs/landscapist/performance-comparison.md) for the caveats.

## Load time and memory

Run the included benchmarks on your own device rather than relying on a published figure:

- **Instrumented load time (all four wrappers, symmetric):**
  ```bash
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.ImageLibraryBenchmark
  ```
  Every library is timed from the model change until its own `onImageStateChanged` reports a terminal state. No fixed sleeps, no hardcoded success.

- **Engine-level load time (no Compose layer):** `UnitPerformanceTest.kt` in the same source set.

- **Frame timing and jank during scrolling:** the AndroidX Macrobenchmark in `:benchmark-landscapist`.
  ```bash
  ./gradlew :benchmark-landscapist:pixel6api31BenchmarkAndroidTest
  ```

For memory, profile a scrolling run with the Android Studio Memory Profiler.

## Why the old numbers were removed

A previous version of this page published a load-time and memory table. The harness behind it stopped the timer for Glide, Coil, and Fresco with a fixed `Thread.sleep(...)` while only Landscapist waited for its real callback, and reported a forced "0 KB" memory figure from a post-GC PSS delta. Those numbers were not a like-for-like comparison and have been removed. The benchmarks above measure every library the same way.

See [docs/landscapist/performance-comparison.md](../docs/landscapist/performance-comparison.md) for the full methodology and architecture comparison.
