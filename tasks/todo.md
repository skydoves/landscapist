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
- [~] Real image fixtures. JPEG and PNG are exercised, and a truncated and an undecodable one.
      WebP is never served, and GIF cannot be produced by `Bitmap.compress` at all, so it needs a
      bundled asset. The decoder branches on both, so neither is covered.
- [~] On-device measurement helper. Allocation, wall time and resident set are there. Frame
      timing is not, so nothing measures jank.

## Phase 1: on device regression sweep

- [~] Sizing and layout. Bounded, unbounded, fillMaxWidth, every ContentScale, and the decode
      size. `Modifier.aspectRatio` is covered nowhere, on device or on desktop.
- [~] Network. 404, 500, a body cut before its header, a redirect chain, a malformed cookie, and
      a slow response passing through Loading. No timeout test.
- [x] Caching: memory hit, disk hit, size variant reuse, an entry oversized on one axis, dedup
- [x] Node path and composed path both draw
- [~] Model change, LazyColumn reuse and the painter API are covered on desktop only. The
      threading bug this branch shipped was invisible on desktop, so device coverage is what
      counts here and these three do not have it.
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

- [~] Cold load, scroll allocation and decode are measured, three runs each. Time to the first
      frame with pixels in it is not, and neither is resident memory, so the row the JVM
      benchmark leads with has no device equivalent.

## Phase 5

- [x] Fix what the sweep found: the threading regression and the blur radius that threw
- [x] Re-verify: 649 unit tests, 79 device tests
- [ ] Update PR #988. Its body still describes JVM measurements and says nothing about the
      device suite, the threading regression, or the decode row that goes the other way.

## Device measurement, emulator, three runs

| | landscapist | coil 3.6.2 |
|---|---|---|
| cold load, 20 images, until all report success | 154 to 161 ms | 209 to 231 ms |
| decode 2000x1500 to 200x150, median of 8 | 36 to 39 ms | 22 to 24 ms |
| the same decode, bytes allocated | 5.5 MiB | 1.82 MiB |
| scroll, 8 swipes over 60 rows, allocated | 2.1 to 2.8 MiB | 2.2 to 2.6 MiB |

The decode row is a real loss and a large one. The Android decoder is untouched by this branch,
so it is not a regression, but it is the opposite of what the JVM benchmark says about desktop.
Worth its own look: where 5.5 MiB goes for one decode is the first question.

## Found so far

- [x] FIXED: the node asked the layout to run again from the loader's thread, so a screen of
      images failed with CalledFromWrongThreadException on Android. Snapshot state now.
      Costs 660 bytes an image on the first frame, which is Compose observing the reads.
- [ ] SEPARATE BUG, pre-existing on main: `rememberImageComponent` is `remember { component }`
      with no keys, so a plugin set that depends on state is frozen at its first value and a
      toggle never reaches the image. `imageComponent(block)` also rebuilds the component and
      its list every composition and throws all but the first away, which is about 395 bytes an
      image per composition. Belongs in its own pull request off main.
- [ ] The Android decoder halves only while both axes still cover the target, so a landscape
      image in a square slot is not downsampled at all: 1200x801 decoded for a 550px box, seen
      in the playground. Correct for a content scale that crops, 2.2x of the pixels needed for
      one that fits. The decoder is never told which. Pre-existing, and the claim that an image
      is decoded at the size it is drawn at needs that caveat.
- [ ] Hardware bitmaps and pixel reading plugins. The Android decoder picks Bitmap.Config.HARDWARE
      on API 26+ for any mime type without alpha, which is every JPEG, and a hardware bitmap has no
      CPU readable pixels. Hardware is turned off for requests with transformations but not for a
      plugin that reads pixels, such as the palette. The demo's palette does work over JPEG on this
      emulator, so either the palette port copies first or something else intervenes. Worth pinning
      with a test either way, and worth turning hardware off for a palette plugin if it does not.
- [ ] Android and desktop disagree on a truncated image: BitmapFactory returns the rows it read,
      the desktop Skia path throws. Worth deciding which is right.
