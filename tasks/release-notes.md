# 2.13.0

> Draft. The version is a placeholder: `buildSrc/.../Configuration.kt` still says 2.12.1, so bump it
> before tagging. Everything below covers #986 and #988, which are the two PRs between 2.12.1 and
> this tag.

---

The Compose layer and the loader underneath it were both rebuilt for what they cost per frame, and two cache keys changed shape along the way. Read the upgrade section first: every image is downloaded once more on the first launch after upgrading.

## Read this before upgrading

**Every image is downloaded once more on the first launch after upgrading.** The disk cache key used to be the URL, and then the target size and the transformation keys folded in on top when a request had either. It is the URL alone now, so one download answers every size an image is drawn at instead of one file per size. An image loaded through a composable is always sized, so its old entry sat under a key nothing looks for any more: the first launch refetches it, and the file 2.12.1 wrote stays on disk until the cache passes its size limit and evicts it. There is no migration, the entries are orphaned rather than renamed. Call `landscapist.config.diskCache?.clear()` once on upgrade if you would rather reclaim the space immediately than wait for eviction. A request that carried no target size and no transformations keys as it did before, and its entry is still found.

**Requests that carry headers now key separately from requests that do not.** An `Authorization` or `Cookie` header makes it a different viewer's image and content negotiation makes it different bytes, so headers are folded into both the memory key and the disk key. Two requests for one URL with different headers no longer share an entry, which is the point, but an app that sets a per-request header and expected to share a cache entry with an unauthenticated request will see one fetch per distinct header set. A request with no headers keys exactly as it did in 2.12.1.

**A `MemoryCache` you wrote yourself keeps working, but decodes more than it needs to.** The loader now asks the cache for an already decoded variant through the new `MemoryCache.getMatching`. The default implementation falls back to an exact `get`, so a custom cache that does not override it is correct but loses the reuse: a layout that measures to 359, 360 and 361 decodes the same image three times. `LruMemoryCache` and `TwoTierMemoryCache` both implement it.

**An image usually exposes one node with its content description now, where it used to expose two.** An image was a container plus a child `Image` composed inside it, and both carried `ImageOptions.contentDescription`. The child is composed only when something actually has to go inside the container: a `loading`, `success` or `failure` slot, a state plugin or a `ComposablePlugin`. A plain image, an image with only a `CrossfadePlugin` and an image with only a `PainterPlugin` such as `BlurTransformationPlugin` are all drawn without one. A UI test that looked the image up by content description and expected two nodes, or that reached for the child of the container, needs updating.

**`rememberImageComponent` follows a plugin set that changes.** It was a `remember` with no keys, so a plugin set computed from state was frozen at whatever it was on the first composition and a toggle never reached the image. The remember is keyed on the plugins now, so the component keeps its identity while they are unchanged and is replaced when they are not. Updating it in place, which is what the first attempt did, kept the identity and so kept the change from ever reaching an image: images are skippable and take the component as a stable parameter.

---

## The Compose layer stopped allocating on every frame

An image drew through a container node and a child composed inside it, whether or not there was anything to compose. There usually is not: no `success` slot, no state plugin, no wrapping plugin. In that case one layout node now measures, draws and runs its own load. The size to decode at is read while measuring rather than written back as state, and a resolved image invalidates one node's draw rather than recomposing.

Twenty images, medians, allocation above an empty scene, from `./gradlew :benchmark-engine:run` on one JVM against the real Coil 3.6.2 engine in the same process:

| KiB per frame | 2.12.1 | 2.13.0 | coil 3.6.2 |
|---|---|---|---|
| first frame | 353.5 | **76.2** | 109.5 |
| resize frame | 34.7 | **5.5** | 5.7 |
| first frame, crossfade | 402.6 | **126.5** | 109.5 |
| first frame, success slot | 335.7 | **262.2** | 147.8 painter, 549.5 subcompose |
| first frame, painter | new | **119.1** | 147.8 |
| resize frame, painter | new | **13.7** | 72.2 |

There is no frame timing row. The macrobenchmark module in this repository can produce one, but the comparison it sets up does not measure two libraries against each other: its Coil tab runs `CoilImage`, this library's own wrapper, rather than Coil's `AsyncImage`; its Landscapist tab installs eight plugins, so the single node path is never in the measurement; and the app opens on the Landscapist tab, which composes and fetches inside every measured block including Coil's. The benchmark app needs fixing before a frame timing figure can be published.

## `rememberLandscapistImagePainter`

The painter on its own, for a caller who wants a plain `Image` and one layout node.

```kotlin
Image(
  painter = rememberLandscapistImagePainter(model = "https://example.com/image.jpg"),
  contentDescription = null,
  modifier = Modifier.size(120.dp),
)
```

It reads the memory cache while it composes, so an image that is already loaded is drawn in the frame the composable appears in, and it takes the size to decode at from the first draw. Set a size through `requestBuilder` when the first draw is not the size the image ends up at. There are no loading or failure slots here, no `ImagePlugin` and no crossfade, since each of those needs something composed around the image.

## Desktop decodes at the size that was asked for

Desktop decode read the whole raster and shrank it afterwards, so a 400x300 thumbnail of a 4000x3000 photo materialised 48 MB of pixels first. ImageIO's `setSourceSubsampling` never reaches libjpeg's own scaling, so asking for one pixel in eight still ran the full inverse DCT.

It decodes through Skia when skiko is on the classpath, which scales during the inverse DCT, and falls back to ImageIO subsampling otherwise. A Compose desktop application already has skiko. Decoding a 4000x3000 JPEG down to 400x300 goes from 486.8 ms to 41.7 ms, the Java heap for one decode from 344.85 MiB to 953.7 KiB, and peak heap above resting from 144.99 MiB to zero, on the same JVM benchmark as above.

Two things fell out of that work. Skia's eighths scaling never engaged, because libjpeg rounds up and the request rounded down. And Skia's four argument `ImageInfo` leaves the colour space null, so an ICC tagged photograph came back oversaturated.

## The memory cache reuses a variant it already decoded

A layout rarely measures to the same pixel twice. A grid whose columns do not divide evenly asks for 359, 360 and 361 wide, and keying strictly on the target size made those three entries and three decodes of one image.

A request with no entry of its own is now served by an already decoded variant of the same image, through `MemoryCache.getMatching`. The rule compares the box an entry was decoded for against the box being asked for, rather than pixel counts: most decoders fit an image inside its box and keep its shape, while an SVG renderer fills the box exactly, so comparing pixels is right about one and wrong about the other. A variant is accepted when it covers both axes to within a pixel and is no more than twice the request on either. On Apple and wasm, where the decoder keeps the encoded bytes and lets Skia decode at draw size, the entry is the whole source and is accepted for any box. That last part is a fix as well as a rule: an entry oversized on one axis alone used to be reused, so a 1080x135 panorama served a 360 wide slot.

## The engine hot path, from #986

`peekMemoryCache` read an entry in 125 ns while `load` took 14 us for the same entry. The difference was a dispatcher round trip that `load` imposed on every call, although the fetch and the decode already run on the loader's own scope. Only the progressive path, which does its disk and network work inline, needs one. `CacheKey` also allocated three lazy holders per call and mapped an empty transformation list into a fresh `ArrayList`.

| | 2.12.1 | after #986 | coil 3.6.2 |
|---|---|---|---|
| memory cache hit p50 | 14.0 us | **1.6 us** | 833 ns |
| cold load p50 | 39.8 us | **22.1 us** | 17.3 us |
| allocation per hit | 2.7 KiB | **1.1 KiB** | 1.6 KiB |

After the further work in #988 a memory cache hit allocates 804 B, against Coil's 1.6 KiB. Thirty two concurrent loads of one image reach the network once, where Coil reaches it 32 times.

#986 also fixed the Android memory cache charging by the wrong number. The Android decoder reported the source dimensions of a downsampled image and the cache charges an entry by the dimensions it is given, so a bitmap that was 0.72 MiB was charged at 45.78 MiB, 64 times over. A 64 MiB budget held one such image where it should hold 89. Expect a higher cache hit rate and more retained memory, up to the budget you set, on any app showing large images.

## On a device

Everything above is a JVM on a desktop, and it does not measure the decoder or the graphics stack that ships. A regression on this branch proved that: a screen of twenty images never loaded on Android, and no desktop test could see it. The verification changed rather than another test being added.

There is now a real HTTP server on a socket in the test process, real encoded fixtures the platform decoder reads back, and an instrumented suite that runs against them. Every plugin renders on an emulator and its pixels are read back: the crossfade on both drawing paths, the reveal's circle rather than a rectangle fading up, the shimmer while a response is held open on a latch, the palette's dominant swatch against a fixture of known colour, a real two pointer pinch, the blur against its unblurred control and against a changed radius, the blurhash and thumbhash placeholders, and the thumbnail and progressive previews against the sizes a recording fetcher saw. Alongside those: sizing including `Modifier.aspectRatio`, every `ContentScale`, caching, dedup, the disk cache, redirects, a malformed cookie, a read timeout, a download that stops short of its declared length, model change, node reuse, an abandoned load, a recycled `LazyColumn`, and the painter API. Formats: JPEG, PNG, WebP in all three shapes the decoder treats differently, and an animated GIF as a byte literal, since `Bitmap.compress` cannot produce one.

The demo app gains a Playground screen where every plugin, sizing mode and loading source can be exercised on a device, with a readout of what the loader reported and Coil beside it on the same URL.

Measured on an emulator, with a fresh process for each measured loader, because whichever loader ran second in a process read as faster by more than the difference being measured:

| | landscapist | coil 3.6.2 |
|---|---|---|
| first image on screen, 20 composed at once | 197 to 365 ms | **154 to 267 ms** |
| until all 20 report success | 243 to 403 ms | **214 to 333 ms** |
| resident set above resting, 20 images | 51 to 60 MiB | **45.2 to 45.3 MiB** |
| decode 2000x1500 to 200x150, median of 8 | 34 to 35 ms | **24 to 25 ms** |
| the same decode, allocated | 5.4 MiB | **1.9 MiB** |
| scroll, 8 swipes over 60 rows, allocated | 2.8 to 3.3 MiB | 2.7 to 2.8 MiB |

So: ahead on every JVM row, behind on cold load, on decode and on resident memory on the device. The Android decode path is untouched by this work, so that row is not a regression, but it is the reverse of what the desktop numbers say and it is the platform that ships.

## Fixes

* A screen of images failed with `CalledFromWrongThreadException`. The node held the painter and asked the layout to run again when it changed, which happens on whatever dispatcher the loader finished on, and asking a layout node to measure again reaches `View.requestLayout` on Android, which throws off the main thread. The painter is snapshot state now, so Compose invalidates on the thread it chooses. It costs about 660 bytes an image on the first frame, which is Compose observing the reads, and that is in the first frame row above.
* `BlurTransformationPlugin(radius = 24)` threw. The blur splits a radius into passes of at most 25 and ran the remainder pass unconditionally at `(radius + 1) % 25`, which is zero for 24, 49 and 74.
* A load left behind by a list rebind published over the image that replaced it, and a reused node published the previous row's image.
* A crossfade was dropped entirely for an image that had one and also a `success` slot, a zoomable plugin or a palette plugin.
* The crossfade held the bitmap it replaced for as long as it was on screen, brought a replaced image back after a loading gap, and stretched the outgoing image into the arriving one's box.
* An animating painter's draw time state read was attributed to an ancestor once the per image graphics layer went, so one image's circular reveal redrew the list around it.
* A cached entry oversized on one axis alone was reused, so a 1080x135 panorama served a 360 wide slot.
* Some CDNs set a cookie on the first response and redirect to a target that requires it, and without one the target keeps redirecting until Ktor's send limit is hit. Cookies are now carried from one hop of a redirect chain to the next. Ktor's own `HttpCookies` cannot be used for it: it parses `max-age` with `toLong()`, so a server that sends `max-age=7.0`, which unsplash.com does, throws `NumberFormatException` out of the response pipeline and fails a download that has nothing to do with the cookie. Only the name, the value and the domain are read here, and every other attribute is ignored rather than parsed. Fixes #859.

## Known and not fixed

* The Android decoder halves only while both axes still cover the target. That is right for a content scale that crops and decodes up to twice the pixels needed for one that fits. Telling the decoder which would add a field to `ImageRequest`, a public data class, which changes its constructor and `copy` signatures. It needs a release that takes a binary break.
* `GlobalBitmapPool` is never filled by the library, so `inBitmap` reuse does not happen. Deleting it breaks the ABI, and filling it safely means knowing when a bitmap has stopped being drawn. Documented on the class so nobody assumes pooling is happening.
* `LandscapistImageTest.testImageWithFixedSize` fails on an emulator here. It goes to the internet with a ten second timeout, and it fails the same way on `main`.

## API

Additive. Seven new declarations, nothing removed or changed.

* `rememberLandscapistImagePainter` in `landscapist-image`.
* `Landscapist.Builder.noDiskCache`, for a loader that writes nothing to disk. Leaving the disk cache unset falls through to the default one, so there was no way to say you wanted none.
* `MemoryCache.getMatching`, with a default implementation that falls back to an exact `get`.
* `ImageOptions.Default` and the companion object that holds it, so a composable that is passed no options does not allocate a fresh `ImageOptions` per frame.
* `rememberCrossfadePainter` is in the API dump too, since the crossfade moved into a painter, but it is marked `@InternalLandscapistApi` and is not part of the supported surface.

The four stale top level `*.api` files in `landscapist`, `landscapist-animation`, `landscapist-palette` and `landscapist-placeholder` are deleted. They were left over from the multiplatform migration, still listed pre-migration signatures, and nothing validated them: `apiCheck` reads `api/android/` and `api/desktop/`.

**Full Changelog**: https://github.com/skydoves/landscapist/compare/2.12.1...2.13.0
