<div class="header">
  <a href="https://github.com/bumptech/glide" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545537-1bc15200-0a39-11eb-883d-644f564da5d3.png" align="left" width="6%" alt="Landscapist-Glide" /></a>
  <h1>Landscapist Glide</h1>
</div>

## Download

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

Add the codes below to your **root** `build.gradle` file (not your module-level build.gradle file):

```Groovy
allprojects {
    repositories {
        mavenCentral()
    }
}
```

Next, add the dependency below to your **module**'s `build.gradle` file:

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-glide:2.2.8"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-glide:2.2.8")
    }
    ```

!!! note

    `Landscapist-Glide` includes version `4.15.1` of [Glide](https://github.com/bumptech/glide) internally. So please make sure your project uses the same Glide version or exclude the Glide dependency to adapt yours. Also, please make sure the Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

### GlideImage

You can load images simply by using `GlideImage` composable function as the following example below:

```kotlin
GlideImage(
  imageModel = { imageUrl }, // loading a network image using an URL.
  imageOptions = ImageOptions(
    contentScale = ContentScale.Crop,
    alignment = Alignment.Center
  )
)
```

### Compose Metrics

According to the [Compose Compoler Metrics](https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md), the `GlideImage` Composable function is marked as Restartable and Skippable. This means you don't have to worry about performance issues related to re-rendering or re-fetching problems that can occur during recomposition. The Composable function's restartable and skippable nature ensures that the necessary actions are taken to optimize rendering, making it more efficient and seamless.

![Compose Metrics](https://github.com/skydoves/landscapist/assets/24237865/bc83dd61-b10a-480d-8797-252df81a10d1)
