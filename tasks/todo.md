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

## Measurements, retaken after the harnesses were fixed

Every harness that was found to measure something other than its label has been repaired, so
none of the earlier numbers carry over. The JVM rows are one process with the two libraries
alternating which goes first; the device rows are one fresh process per measured loader,
enforced; the frame timing comes from the macrobenchmark with a real Coil `AsyncImage`, a
landscapist variant that actually takes the node path, no tab selected at startup, images from
a socket inside the app, and full compilation so neither column is left interpreted.

### JVM, allocation above an empty scene, 20 images

| KiB per frame | before | after | coil 3.6.2 |
|---|---|---|---|
| first frame | 353.5 | **76.2** | 109.5 |
| resize frame | 34.7 | **5.5** | 5.7 |
| first frame, crossfade | 402.6 | 126.5 | **109.5** |
| first frame, success slot | 335.7 | 262.2 | **147.8** painter, 549.7 subcompose |
| first frame, painter | new | **119.1** | 147.8 |
| resize frame, painter | new | **13.7** | 72.2 |

A memory cache hit allocates 804 B against 1.6 KiB. 32 concurrent loads of one image reach the
network once, against 32. Decoding a 4000x3000 JPEG to 400x300 takes 39.3 ms against Coil's own
decoder at 47.6 ms, and the Java heap for one decode went from 344.85 MiB to 953.7 KiB.

### JVM, timing, three runs, sides alternating

| p50 | landscapist | coil 3.6.2 |
|---|---|---|
| cold load | 110.8, 119.1, 94.2 us | 114.6, 119.9, 93.8 us |
| memory cache hit | 958, 834, 875 ns | **750, 709, 708 ns** |

Cold load is a tie. The memory hit is consistently about 18 percent slower, which is the row an
earlier revision quoted the other way round before the sides were paired.

### Device, emulator, one fresh process per measurement, two runs

| | landscapist | coil 3.6.2 |
|---|---|---|
| first image on screen, 20 composed at once | 138, 124 ms | **99, 98 ms** |
| until all 20 report success | 241, 246 ms | **166, 174 ms** |
| resident set above resting, 20 images | 59.4, 59.5 MiB | **45.5, 33.0 MiB** |
| decode 2000x1500 to 200x150 | 34.1, 34.8 ms | **23.6, 23.8 ms** |
| the same decode, allocated | 5.45 MiB | **1.82 MiB** |
| scroll, 8 swipes over 240 rows, allocated | 45.1, 41.2 MiB | **18.7, 18.6 MiB** |

The scroll row is new: it used to run 60 rows, where both caches held everything and the row
measured nothing. At 240 rows the caches evict and the gap is 2.4x, which tracks the 3x gap in
what one decode allocates.

### Frame timing, macrobenchmark, five iterations, three runs

| p50 / p90 / p95 / p99, ms | landscapist | coil 3.6.2 |
|---|---|---|
| duration, run 1 | 3.4 / 5.8 / 6.5 / 11.0 | 3.5 / 5.8 / 6.7 / 19.8 |
| duration, run 2 | 3.5 / 7.4 / 18.0 / 21.8 | 3.3 / 6.1 / 7.0 / 10.1 |
| duration, run 3 | 3.6 / 6.8 / 7.6 / 10.2 | 3.4 / 6.4 / 7.1 / 9.8 |

A tie. The medians are within a tenth of a millisecond and the upper percentiles swing both ways
between runs, so nothing is claimed from this row in either direction.

### What the picture says

The Compose layer, which is what this branch changed, is ahead on every allocation row on the
JVM. The device rows it is behind on are the decode and what follows from it, and that path is
untouched here: one decode allocates three times what Coil's does, which is most of the scroll
gap and some of the resident set gap. That is the next thing worth working on.

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
