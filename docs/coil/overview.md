<div class="header">
  <a href="https://github.com/coil-kt/coil" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545538-1cf27f00-0a39-11eb-83dd-ef9b8c6a74cb.png" align="left" width="6%" alt="Fresco" /></a>
  <h1>Landscapist Coil</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>
Add the dependency below to your **module**'s `build.gradle` file:

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-coil:$version"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-coil:$version")
    }
    ```

!!! note

    Please make sure your project uses the same Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

### CoilImage
You can load images by using the `CoilImage` composable function as the following example below:

```kotlin
CoilImage(
  imageModel = { imageUrl }, // loading a network image or local resource using an URL.
  imageOptions = ImageOptions(
    contentScale = ContentScale.Crop,
    alignment = Alignment.Center
  )
)
```

### Compose Metrics

According to the [Compose Compoler Metrics](https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md), the `CoilImage` Composable function is marked as Restartable and Skippable. This means you don't have to worry about performance issues related to re-rendering or re-fetching problems that can occur during recomposition. The Composable function's restartable and skippable nature ensures that the necessary actions are taken to optimize rendering, making it more efficient and seamless.

![compose-metrics-coil](https://github.com/skydoves/landscapist/assets/24237865/5718a15e-07bc-4ee3-b76f-13fb09ed6d27)
