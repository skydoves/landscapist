# Palette

The `landscapist-palette` package offers a set of valuable image plugins related to the palette, including extracting primary color sets.

To utilize these palette supports, simply add the following dependency:

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-palette:$version"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-palette:$version")
    }
    ```

You can extract primary (theme) color profiles with `PalettePlugin`. You can check out [Extract color profiles](https://developer.android.com/training/material/palette-colors#extract-color-profiles) to see what kinds of colors can be extracted.

=== "Glide"

    ```kotlin
    var palette by rememberPaletteState(null)

    GlideImage(
      component = rememberImageComponent {
          +PalettePlugin { palette = it }
      },
      ..
    )

    Crossfade(
        targetState = palette,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    ```

=== "Coil"

    ```kotlin
    var palette by rememberPaletteState(null)

    CoilImage(
      component = rememberImageComponent {
          +PalettePlugin { palette = it }
      },
      ..
    )

    Crossfade(
        targetState = palette,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    ```

=== "Fresco"

    ```kotlin
    var palette by rememberPaletteState(null)

    FrescoImage(
      component = rememberImageComponent {
          +PalettePlugin { palette = it }
      },
      ..
    )

    Crossfade(
        targetState = palette,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    ```

You can also customize attributes of `PalettePlugin` like the example below:

=== "Glide"

    ```kotlin
    var palette by rememberPaletteState(null)

    GlideImage(
      component = rememberImageComponent {
        +PalettePlugin(
            imageModel = poster.image,
            useCache = true, // use cache strategies for the same image model.
            interceptor = {
                it.addFilter { rgb, hsl ->
                // here edit to add the filter colors.
                false
                }
            },
            paletteLoadedListener = {
                palette = it
            }
        )
      },
      ..
    )

    Crossfade(
        targetState = palette,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    ```

=== "Coil"

    ```kotlin
    var palette by rememberPaletteState(null)

    CoilImage(
      component = rememberImageComponent {
        +PalettePlugin(
            imageModel = poster.image,
            useCache = true, // use cache strategies for the same image model.
            interceptor = {
                it.addFilter { rgb, hsl ->
                // here edit to add the filter colors.
                false
                }
            },
            paletteLoadedListener = {
                palette = it
            }
        )
      },
      ..
    )

    Crossfade(
        targetState = palette,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    ```

=== "Fresco"

    ```kotlin
    var palette by rememberPaletteState(null)

    FrescoImage(
      component = rememberImageComponent {
        +PalettePlugin(
            imageModel = poster.image,
            useCache = true, // use cache strategies for the same image model.
            interceptor = {
                it.addFilter { rgb, hsl ->
                // here edit to add the filter colors.
                false
                }
            },
            paletteLoadedListener = {
                palette = it
            }
        )
      },
      ..
    )

    Crossfade(
        targetState = palette,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(45.dp)
    ) {
      Box(
        modifier = Modifier
          .background(color = Color(it?.lightVibrantSwatch?.rgb ?: 0))
          .fillMaxSize()
      )
    }
    ```

## Preview

![Palette](https://user-images.githubusercontent.com/24237865/129226361-877689b8-a1ec-4f59-b8a6-e2efe33a8de7.gif)