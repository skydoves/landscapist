# Stop cached images from blinking, add SVG support

## Part 1 — Blink root cause (#974, #620)

A memory cache hit cost **three frames and two dispatcher hops** before a single pixel was drawn:

| Step | Where | Cost |
|---|---|---|
| 1. `state` seeds to `ImageLoadState.None` | `LandscapistImage.kt:299` | frame 1 draws nothing |
| 2. `canLoad` is false until `Modifier.layout` writes `incomingMaxWidth` in the measure pass | `LandscapistImage.kt:351`, `:370` | the `LaunchedEffect` is not even composed until frame 2 |
| 3. `executeImageLoading` emits `Loading` first, then `flowOn(Dispatchers.Default)` | `LandscapistImage.kt:434`, `:453` | hop 1 |
| 4. `Landscapist.load` emits `Loading` **before** it reads the memory cache, then `flowOn(ioDispatcher)` | `Landscapist.kt:147`, `:157` | hop 2 |
| 5. `CrossfadeWithEffect` seeds an **empty** visible list and only adds the target in a `LaunchedEffect` | `CrossfadeWithEffect.kt:56` | one more empty frame, then a fade in from alpha 0 |

Shared element transitions hit every one of these at once: the destination composable is brand new,
so it always started from `None`, and the shared bounds animated over an empty box.

- [x] `CacheKey.baseKey` and `MemoryCache.getIgnoringSize`, backed by a `SizeVariantIndex`, find an
      already decoded variant of an image before layout constraints are known.
- [x] `Landscapist.peekMemoryCache` reads the cache without suspending, and `memoryCache` is public,
      so the reflection workaround in #974 is no longer needed.
- [x] `Landscapist.load` reads the memory cache **before** emitting `Loading`.
- [x] `LandscapistImageInternal` seeds its state from `peekMemoryCache` during composition, and never
      drops a `Success` that is already on screen back to `Loading`.
- [x] `CrossfadeWithEffect` renders the state it was composed with on the first frame, at full
      opacity. Content that arrives later, including a return to that same state, still fades in.

## Part 2 — `ImageLoad`, the coil/coil3/glide/fresco path

There is no safe synchronous probe for those backends: coil builds its `MemoryCache.Key` extras
internally from the resolved size and transformations, and glide and fresco key their caches
internally too. Reconstructing those keys would couple us to their internals for a silent miss when
they change. So the frame 0 render stays exclusive to `landscapist-image`, and what is removable was
removed:

- [x] `flowOn(platformCoroutineDispatcher)` is gone; the dispatcher had no other users and is deleted.
      The same hop is gone from `landscapist-image`.
- [x] The synthetic `emit(Loading)` head is gone; the backends emit their own.
- [x] A restart's `Loading` no longer blanks an image already on screen. `None` still clears it,
      because Glide reports that when it releases the bitmap.
- [x] `catch` keeps the throwable instead of replacing it with null.
- [x] Coil 2 and Glide send with `trySend` rather than `trySendBlocking`, now that the producer can
      run on the composition dispatcher.

## Part 3 — SVG support (#945)

**First attempt was wrong and was thrown away.** Compose Multiplatform's `decodeToImageVector` looked
like an SVG parser available on every target. It is the Android VectorDrawable XML parser: it reads
`android:` namespaced attributes and only knows `path`, `clip-path` and `group`. Against real SVG it
produces an empty vector, or throws on any `<path>`. Worse, passing markup through as a `Success`
turned what used to be a reported decode failure into a silent blank. A test that only asserted
`assertIs<VectorPainter>` passed anyway, because an empty vector satisfies it.

- [x] New `landscapist-svg` artifact that rasterizes at the requested size, with AndroidSVG on
      Android (what coil-svg uses; the platform has no SVG of its own) and Skia everywhere else.
- [x] `SvgImageDecoder` wraps any other decoder and reports unparseable markup as
      `DecodeResult.Error`, so the failure slot still runs.
- [x] Rasterizing rather than passing markup through means the memory cache accounts for real pixels,
      transformations and palette keep working, and nothing parses XML during composition.
- [x] `isSvg` and `readSvgDimensions` stay in `landscapist-core`, in the same hand rolled header
      parsing style as `readImageDimensions`, and are now public so the svg module reuses them.
      Detection requires `<svg` as the **root** element so an HTML error page that embeds one is not
      taken for an image.
- [x] Progressive loading routes SVG to the standard decode.
- [x] Tests assert rendered pixels, not just result types.

## Part 4 — CI break on `main`

- [x] `:landscapist-animation:compileKotlinDesktop` failed on `asFrameworkPaint()`, deprecated as an
      error since the Compose 1.12.0 bump. The skia painter sets `isAntiAlias` on the Compose `Paint`
      directly; `isDither` has no Compose equivalent and no effect here, and the `reset()` released a
      paint to a pool that only the Android painter has.

## Review

### Behaviour change

- A `LandscapistImage` whose image is in memory renders it in the **first composed frame**, with no
  loading state and no crossfade.
- `Landscapist.load` no longer emits `ImageResult.Loading` for a memory hit.
- Before layout, the probe may return a differently sized variant of the same image, replaced by the
  correctly sized one as soon as the real load resolves. This is what Coil does with
  `placeholderMemoryCacheKey`.
- The coil, coil3, glide and fresco backends deliver their states on the composition dispatcher now,
  and hold a resolved image through a restart.
- SVG needs the new artifact and one line of setup. Nothing changes for anyone who does not add it.

### Verification

- 292 desktop tests pass, up from 262. Reverting each fix makes its own tests fail: 3 of 4 in
  `CachedImageFirstFrameTest`, 2 of 5 in `ImageLoadTest`.
- The CI sequence passes: `assemble`, `spotlessCheck`, `apiCheck`, `testDebugUnitTest`.
- Every changed module compiles for android, desktop, iosArm64, macosArm64 and wasmJs.
- The api dump diff is additive only. `landscapist` and `landscapist-image` dumps are unchanged.
- Android SVG rasterizing is compile verified only; there is no device here to run it on. The skia
  path is covered by pixel assertions.

### Still red on main, untouched

- `stabilityCheck` fails in `coil`, `glide`, `fresco` and `landscapist-transformation`, with the same
  failures before and after this branch. They are all `restartable changed from true to false` on
  `remember*` helpers, an artifact of the Compose compiler bump. Accepting them needs
  `./gradlew stabilityDump`, which is unrelated churn for this change.
