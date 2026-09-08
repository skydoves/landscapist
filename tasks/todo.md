# Real device verification of perf/compose-node-structure

Everything on this branch was verified with stub fetchers and stub decoders. A cookie
that no image needed took the whole demo app down and 643 passing tests said nothing.
This replaces that with verification that runs the real path on a real device.

## Ground rules

- One gradle invocation at a time, owned by the main session. Subagents write code and
  do not run gradle. Daemons are stopped between phases.
- Every instrumented test serves real bytes over real HTTP from a local server, so it is
  deterministic and offline but exercises headers, redirects, status codes and the real
  decoder.

## Phase 0: infrastructure

- [x] Local HTTP server for androidTest: latency, gates, status, headers, redirects, payloads
- [x] Real image fixtures: JPEG, PNG, WebP (lossy, lossless, and lossy with alpha), an animated
      GIF built as a verified byte literal, a truncated one and an undecodable one
- [x] On-device measurement: allocation, wall time, resident set, time to the first image, and
      frame timing through the macrobenchmark module that was already here and had never run

## Phase 1: on device regression sweep

- [x] Sizing and layout, including `Modifier.aspectRatio` and the size the decode is made at
- [x] Network: 404, 500, a cut body, a redirect chain, a malformed cookie, a slow response
      passing through Loading, and a response that never arrives failing at the read timeout
- [x] Caching: memory hit, disk hit, size variant reuse, an entry oversized on one axis, dedup
- [x] Node path and composed path both draw
- [x] Model change, node reuse, an abandoned load, a recycled LazyColumn and the painter API,
      all on the device now
- [x] Crossfade on both paths

## Phase 2: plugin UI tests, one per plugin

- [x] Crossfade, CircularReveal, Shimmer, Palette, Zoomable
- [x] BlurTransformation, BlurHash, ThumbHash, Thumbnail
- [x] Placeholder (loading, failure), ProgressiveLoading
- [x] Plugin combinations that pick different internal paths

## Phase 3: playground screen in the demo app

- [x] Every plugin toggleable and visible on device
- [x] Every loading behaviour: sizes, content scales, cache states, failures

## Phase 4: real device measurement against Coil

- [x] First image, all images, resident set, decode, scroll allocation and frame timing, each
      in a fresh process where the measurement needs one

## Phase 5

- [x] Fix what the sweep found: the threading regression and the blur radius that threw
- [x] Re-verify: 649 unit tests, 79 device tests
- [x] Update PR #988

## Device measurement, emulator

A fresh process per measured loader. Two rows had to be measured that way and were wrong
before it: whichever loader ran second in a process read as faster by more than the
difference being measured, and warming both stacks first did not fix it, because the
sockets, the thread pools and the JIT of everything under Compose are shared too. The
comparison now refuses to measure a second loader in a process that has already measured
one. The decode and scroll rows were checked against fresh processes and did not move.

| | landscapist | coil 3.6.2 |
|---|---|---|
| first image on screen, 20 composed at once | 197, 218, 365 ms | 154, 182, 267 ms |
| until all 20 report success | 243, 265, 403 ms | 214, 219, 333 ms |
| resident set above resting, 20 images | 51, 52, 60 MiB | 45.2, 45.2, 45.3 MiB |
| decode 2000x1500 to 200x150, median of 8 | 34, 35 ms | 24, 25 ms |
| the same decode, allocated | 5.4 MiB | 1.9 MiB |
| scroll, 8 swipes over 60 rows, allocated | 2.8, 3.3 MiB | 2.7, 2.8 MiB |

Frame timing comes from the repository's own macrobenchmark, five iterations each, scrolling
thirty images. Emulator, so the absolute values mean little; the two columns were taken the
same way.

| frame, ms | landscapist | coil 3.6.2 |
|---|---|---|
| duration P50 / P90 / P95 / P99 | 4.6 / 15.0 / 17.7 / 29.0 | 6.0 / 18.0 / 19.9 / 38.0 |
| overrun P50 / P90 / P95 / P99 | -10.7 / 1.3 / 1.8 / 35.6 | -9.6 / 2.7 / 6.7 / 27.4 |

So: ahead on frame time while scrolling, behind on cold load, on decode and on resident
memory. The decode path is untouched by this branch. The cold load and memory rows are not
what the JVM benchmark says about desktop, and the JVM benchmark is not measuring the
platform decoder.

## Found so far

- [x] FIXED: the node asked the layout to run again from the loader's thread, so a screen of
      images failed with CalledFromWrongThreadException on Android. Snapshot state now.
      Costs 660 bytes an image on the first frame, which is Compose observing the reads.
- [x] FIXED: `rememberImageComponent` froze its plugin set at the first composition
- [x] NOT A BUG: hardware bitmaps and pixel reading plugins. A JPEG does decode to
      Bitmap.Config.HARDWARE here, confirmed by probe, and both plugins that read pixels cope:
      the blur copies to ARGB_8888 itself, and kmpalette handles it. Pinned by a palette test
      and a blur test that both go through a JPEG.
- [ ] The decoder halves only while both axes still cover the target, which is right for a content
      scale that crops and decodes up to twice the pixels needed for one that fits. Fixing it means
      telling the decoder which, and `ImageRequest` is a public data class, so a field for it
      changes the constructor and `copy` signatures. That is a binary break, so it needs a release
      that takes one. Measured: 1200x801 decoded for a 550px box.
- [x] `GlobalBitmapPool` is never filled by the library, so `inBitmap` reuse does not happen.
      Deleting it would break the ABI and filling it safely needs to know when a bitmap has
      stopped being drawn, which is a feature rather than a fix. Documented on the class so
      nobody assumes pooling is happening.
- [x] A download that stops short of its declared length fails rather than becoming half an
      image, on both platforms; pinned by a test. The disagreement that remains is a complete
      response carrying corrupt bytes, where Android returns the rows it read and desktop throws.
      Neither is wrong and the dangerous path is covered.
