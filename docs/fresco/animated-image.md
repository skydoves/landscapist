# Fresco Animated Image Support (GIF, Webp)

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

Add the below dependency to your **module**'s `build.gradle` file:

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-fresco-websupport:$version"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-fresco-websupport:$version")
    }
    ```

<img src="https://user-images.githubusercontent.com/24237865/131246748-b88903a1-43de-4e6c-9069-3e956a0cf8a6.gif" align="right" width="32%"/>

You can load animated GIFs and WebP Images with `FrescoWebImage` composable function. You should pass the `AbstractDraweeController` like the following example below:

```kotlin
FrescoWebImage(
  controllerBuilder = {
      Fresco.newDraweeControllerBuilder()
          .setUri(poster.gif) // GIF or Webp image url.
          .setAutoPlayAnimations(true)
  },
  modifier = Modifier
    .fillMaxWidth()
    .height(300.dp)
)
```

For more details, check out [DraweeController](https://frescolib.org/docs/animations.html), and [Supported URIs](https://frescolib.org/docs/supported-uris.html) for setting URI addresses. Also, you can load general images (jpeg, png, etc) which can be loaded with `FrescoImage` by using `FrescoWebImage` and your custom controller.