# Animation

The landscapist-animation package offers a set of valuable image plugins related to animations, including crossfade and circular reveal animations. 

To utilize these animation supports, simply add the following dependency:

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-animation:$version"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-animation:$version")
    }
    ```

### Crossfade Animation

You can effortlessly implement the crossfade animation while displaying images using the CrossfadePlugin, as shown in the example below:

=== "Glide"

    ```kotlin
    GlideImage(
      component = rememberImageComponent {
          +CrossfadePlugin(
            duration = 550
          )
      },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      component = rememberImageComponent {
          +CrossfadePlugin(
            duration = 550
          )
      },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      component = rememberImageComponent {
          +CrossfadePlugin(
            duration = 550
          )
      },
      ..
    )
    ```

By using the `CrossfadePlugin`, you can achieve smooth and visually pleasing image transitions that gracefully fade from one image to another. This animation effect adds a touch of elegance to your app and enhances the overall user experience while displaying images.

### Circular Reveal Animation

You can seamlessly implement the circular reveal animation while displaying images using the `CircularRevealPlugin`, as demonstrated below:

=== "Glide"

    ```kotlin
    GlideImage(
      component = rememberImageComponent {
          +CircularRevealPlugin(
            duration = 350
          )
      },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      component = rememberImageComponent {
          +CircularRevealPlugin(
            duration = 350
          )
      },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      component = rememberImageComponent {
          +CircularRevealPlugin(
            duration = 350
          )
      },
    )
    ```

The `CircularRevealPlugin` allows you to create captivating image transitions that emanate from a circular shape, adding a visually engaging effect to your app. This animation enhances the user experience and provides a delightful way to showcase images within your application.

### Preview

|                                                                Circular Reveal                                                                 |                                                                   Crossfade                                                                    |
|:----------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------:|
| <img src="https://user-images.githubusercontent.com/24237865/189552544-5f8e1209-4930-45e6-a050-3a0cda088e9f.gif" align="center" width="100%"/> | <img src="https://user-images.githubusercontent.com/24237865/189552547-d933cee7-e811-4170-a806-1ac165e8f055.gif" align="center" width="100%"/> | 

