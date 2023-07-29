<div class="header">
  <a href="https://github.com/facebook/fresco" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545540-1cf27f00-0a39-11eb-9e84-96b9df81364b.png" align="left" width="6%" alt="Fresco" /></a>
  <h1>Fresco</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

Add the dependency below to your **module**'s `build.gradle` file:

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-fresco:$version"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-fresco:$version")
    }
    ```

!!! note

    `Landscapist-Fresco` includes version `2.6.0` of Fresco. So please make sure your project is using the same Fresco version or exclude the Fresco dependency to adapt yours. Also, please make sure the Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

### Setup
To get started, you should set up `Fresco` with [ImagePipelineConfig](https://frescolib.org/docs/configure-image-pipeline.html) in your `Application` class. Generally, it's recommended initializing with  `OkHttpImagePipelineConfigFactory`. Also, you can customize caching, networking, and thread pool strategies with your own `ImagePipelineConfig`. For more details, you can check out [Using Other Network Layers](https://frescolib.org/docs/using-other-network-layers.html#using-okhttp).
```kotlin
class App : Application() {

  override fun onCreate() {
    super.onCreate()

    val pipelineConfig =
      OkHttpImagePipelineConfigFactory
        .newBuilder(this, OkHttpClient.Builder().build())
        .setDiskCacheEnabled(true)
        .setDownsampleEnabled(true)
        .setResizeAndRotateEnabledForNetwork(true)
        .build()

    Fresco.initialize(this, pipelineConfig)
  }
}
```

### FrescoImage
You can load images by using the `FrescoImage` composable function as the following example below:

```kotlin
FrescoImage(
  imageUrl = stringImageUrl, // loading a network image using an URL.
  imageOptions = ImageOptions(
    contentScale = ContentScale.Crop,
    alignment = Alignment.Center
  )
)
```

### Compose Metrics

According to the [Compose Compoler Metrics](https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md), the `FrescoImage` Composable function is marked as Restartable and Skippable. This means you don't have to worry about performance issues related to re-rendering or re-fetching problems that can occur during recomposition. The Composable function's restartable and skippable nature ensures that the necessary actions are taken to optimize rendering, making it more efficient and seamless.

![compose-metrics-fresco](https://github.com/skydoves/landscapist/assets/24237865/f28c467d-d8c5-476d-b65d-f976876777af)