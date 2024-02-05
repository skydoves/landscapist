# Placeholder

The `landscapist-placeholder` package offers a rich selection of image plugins for implementing placeholders, including loading and failure placeholder support, as well as shimmering animations.

To utilize these placeholder supports, simply add the following dependency:

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=skydoves%2520landscapist)<br>

=== "Groovy"

    ```Groovy
    dependencies {
        implementation "com.github.skydoves:landscapist-placeholder:$version"
    }
    ```

=== "KTS"

    ```kotlin
    dependencies {
        implementation("com.github.skydoves:landscapist-placeholder:$version")
    }
    ```

### PlaceholderPlugin

You have the ability to display your custom placeholders while loading an image or in case of a loading failure by using the `PlaceholderPlugin.Loading` `and PlaceholderPlugin.Failure` respectively.

=== "Glide"

    ```kotlin
    GlideImage(
      component = rememberImageComponent {
          +PlaceholderPlugin.Loading(painterResource(id = R.drawable.placeholder_loading))
          +PlaceholderPlugin.Failure(painterResource(id = R.drawable.placeholder_failure))
      },
      ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
      component = rememberImageComponent {
          +PlaceholderPlugin.Loading(painterResource(id = R.drawable.placeholder_loading))
          +PlaceholderPlugin.Failure(painterResource(id = R.drawable.placeholder_failure))
      },
      ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
      component = rememberImageComponent {
          +PlaceholderPlugin.Loading(painterResource(id = R.drawable.placeholder_loading))
          +PlaceholderPlugin.Failure(painterResource(id = R.drawable.placeholder_failure))
      },
      ..
    )
    ```

!!! note
    
    The source should be one of `ImageBitmap`, `ImageVector`, or `Painter`.

### ShimmerPlugin

You can implement a shimmering effect while loading an image by using the `ShimmerPlugin`, as shown in the example below:


=== "Glide"

    ```kotlin
    GlideImage(
        component = rememberImageComponent {
          // displays a shimmering effect when loading an image.
          +ShimmerPlugin(
            Shimmer.Resonate(
              baseColor = Color.White,
              highlightColor = Color.LightGray,
            )
          )
        },
        ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
        component = rememberImageComponent {
          // displays a shimmering effect when loading an image.
          +ShimmerPlugin(
            Shimmer.Resonate(
              baseColor = Color.White,
              highlightColor = Color.LightGray,
            )
          )
        },
        ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
        component = rememberImageComponent {
          // displays a shimmering effect when loading an image.
          +ShimmerPlugin(
            Shimmer.Resonate(
              baseColor = Color.White,
              highlightColor = Color.LightGray,
            )
          )
        },
        ..
    )
    ```

After building the above sample, you'll see the shimmering effect in the result below:

![Shimmer](https://user-images.githubusercontent.com/24237865/95812167-be3a4780-0d4f-11eb-9360-2a4a66a3fb46.gif)

### ThumbnailPlugin

Landscapist supports the thumbnail feature, enabling pre-loading and displaying small-sized images while loading the original image. This approach creates the illusion of faster image loading and delivers a natural loading effect to users. To showcase the thumbnail, simply add the image plugin to your image component, as illustrated in the example below:

=== "Glide"

    ```kotlin
    GlideImage(
        component = rememberImageComponent {
            +ThumbnailPlugin() 
        },
        ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
        component = rememberImageComponent {
            +ThumbnailPlugin() 
        },
        ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
        component = rememberImageComponent {
            +ThumbnailPlugin() 
        },
        ..
    )
    ```

You can also adjust the request sizes by giving the `requestSize` parameter:

=== "Glide"

    ```kotlin
    GlideImage(
        component = rememberImageComponent {
            +ThumbnailPlugin(IntSize(30 ,30))
        },
        ..
    )
    ```

=== "Coil"

    ```kotlin
    CoilImage(
        component = rememberImageComponent {
            +ThumbnailPlugin(IntSize(30 ,30))
        },
        ..
    )
    ```

=== "Fresco"

    ```kotlin
    FrescoImage(
        component = rememberImageComponent {
            +ThumbnailPlugin(IntSize(30 ,30))
        },
        ..
    )
    ```

!!! note
    
    Using a small request size on the thumbnail plugin is highly recommended to expedite the pre-loading images process. By specifying a smaller request size, you ensure that the pre-loaded images load faster, optimizing the user experience during image loading and achieving smoother transitions to display the original images.

After building the above sample, you'll see the thumbnail while loading an image in the result below:

![Thumbnail](https://github.com/skydoves/landscapist/assets/24237865/dad9db76-31c5-453a-98a8-f3dfd3103993)