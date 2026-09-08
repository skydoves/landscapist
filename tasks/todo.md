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
