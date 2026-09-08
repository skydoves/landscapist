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

- [ ] Local HTTP server for androidTest: real responses, controllable latency, status,
      headers, redirects, content type, and byte payloads
- [ ] Real image fixtures: JPEG, PNG, WebP, GIF, at several sizes, and a malformed one
- [ ] On-device Compose measurement helper: frame timing and allocation per composable

## Phase 1: on device regression sweep

- [ ] Sizing and layout: bounded, unbounded, fillMaxWidth, aspectRatio, every ContentScale
- [ ] Network: success, 404, timeout, redirect, malformed cookie, malformed bytes
- [ ] Caching: memory hit, disk hit, size variant reuse, dedup of concurrent loads
- [ ] Node path and composed path both draw
- [ ] Model change, LazyColumn reuse, painter API
- [ ] Crossfade on both paths

## Phase 2: plugin UI tests, one per plugin

- [ ] Crossfade, CircularReveal, Shimmer, Palette, Zoomable
- [ ] BlurTransformation, BlurHash, ThumbHash, Thumbnail
- [ ] Placeholder (loading, failure), ProgressiveLoading
- [ ] Plugin combinations that pick different internal paths

## Phase 3: playground screen in the demo app

- [ ] Every plugin toggleable and visible on device
- [ ] Every loading behaviour: sizes, content scales, cache states, failures

## Phase 4: real device measurement against Coil

- [ ] First frame, scroll, memory, decode, on the emulator rather than the JVM

## Phase 5

- [ ] Fix what the sweep finds, re-verify, update PR #988

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
