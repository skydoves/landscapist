# Landscapist SVG

`landscapist-svg` adds SVG decoding to [landscapist-core](landscapist-core.md), so an SVG URL loads
the same way a PNG does.

It is a separate artifact because Android has no SVG support of its own and needs a rasterizer
bundled with it. Skia targets render SVG natively.

## Installation

=== "Gradle (Android)"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-svg:$version")
    }
    ```

=== "Kotlin Multiplatform"

    ```kotlin
    kotlin {
        sourceSets {
            commonMain.dependencies {
                implementation("com.github.skydoves:landscapist-svg:$version")
            }
        }
    }
    ```

## Usage

Install the decoder once, wherever you configure the loader:

```kotlin
Landscapist.setInstance(
    Landscapist.builder()
        .decoder(SvgImageDecoder())
        .build(),
)
```

An SVG URL then loads like any other image:

```kotlin
LandscapistImage(
    imageModel = { "https://example.com/logo.svg" },
    modifier = Modifier.size(120.dp),
)
```

`SvgImageDecoder()` wraps the default platform decoder, so every other format keeps working. To
layer it on top of your own decoder, pass it in:

```kotlin
Landscapist.builder()
    .decoder(SvgImageDecoder(MyDecoder()))
    .build()
```

## How it renders

The SVG is rasterized at the size the request asks for, which is the size the composable was
measured at, so it is as sharp as the layout it lands in. Placing the same image somewhere larger
re-renders it rather than upscaling, because the target size is part of the cache key.

When the layout gives no size, the `width` and `height` attributes are used, then the `viewBox`, then
a 512 pixel square. Every size is clamped to `LandscapistConfig.maxBitmapSize`.

Markup that fails to parse is reported as a load failure, so the composable's `failure` slot runs the
same way it does for a corrupt bitmap.

## Detection

An SVG is recognized from its content as well as its `image/svg+xml` MIME type, since plenty of
servers hand it back as `text/plain`, `application/octet-stream`, or with no type at all. Sniffing
looks for `<svg` as the document's root element, past a byte order mark, an XML declaration, a
doctype and any comments, so an HTML page that merely embeds an `<svg>` is not mistaken for an image.

## Renderers

| Platform | Renderer |
|---|---|
| Android | [AndroidSVG](https://github.com/BigBadaboom/androidsvg) |
| Desktop, iOS, macOS, Web | Skia, through skiko |
