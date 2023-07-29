# Transformation

The `landscapist-transformation` package offers a range of valuable image transformation plugins, including the blur effect.

To utilize these transformation supports, simply add the following dependency:

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-transformation:$version"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-transformation:$version")
    }
    ```

### BlurTransformationPlugin

You can effortlessly implement the blur effect using the `BlurTransformationPlugin`, as demonstrated below:

=== "Glide"

    ```kotlin
    GlideImage(
      component = rememberImageComponent {
        +BlurTransformationPlugin(radius = 10) // between 0 to Int.MAX_VALUE.
     },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      component = rememberImageComponent {
        +BlurTransformationPlugin(radius = 10) // between 0 to Int.MAX_VALUE.
     },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      component = rememberImageComponent {
        +BlurTransformationPlugin(radius = 10) // between 0 to Int.MAX_VALUE.
     },
      ..
    )
    ```

By incorporating the `BlurTransformationPlugin`, you can easily add a captivating blur effect to your images, enhancing their visual appeal and creating a more dynamic and immersive user experience within your app. Adjust the `blurRadius` parameter to achieve the desired level of blurriness for your images.

!!! note
    
    Landscapist's blur transformation falls back onto a CPU-based implementation to support older API levels. So you don't need to worry about API compatibilities and performance issues.

### Preview

![Blur Transformation](https://user-images.githubusercontent.com/24237865/196038507-54a3a79c-2e8e-45ec-b5e8-5de65cd50248.png)