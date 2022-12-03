

![landscapist](https://user-images.githubusercontent.com/24237865/127760344-bb042fe8-23e1-4014-b208-b7b549d32086.png)<br><br>

<p align="center">
  <a href="https://devlibrary.withgoogle.com/products/android/repos/skydoves-Landscapist"><img alt="Google" src="https://skydoves.github.io/badges/google-devlib.svg"/></a><br>
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://android-arsenal.com/api?level=21"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://github.com/skydoves/Landscapist/actions"><img alt="Build Status" src="https://github.com/skydoves/landscapist/workflows/Android%20CI/badge.svg"/></a>
  <a href="https://androidweekly.net/issues/issue-441"><img alt="Android Weekly" src="https://skydoves.github.io/badges/android-weekly.svg"/></a>
  <a href="https://proandroiddev.com/loading-images-for-jetpack-compose-using-glide-coil-and-fresco-1211261a296e"><img alt="Medium" src="https://skydoves.github.io/badges/Story-Medium.svg"/></a>
  <a href="https://github.com/skydoves"><img alt="Profile" src="https://skydoves.github.io/badges/skydoves.svg"/></a> 
</p>

<p align="center">
🍂 Landscapist is a Jetpack Compose image loading solution that fetches and displays network images with <a href="https://github.com/bumptech/glide" target="_blank"> Glide</a>, <a href="https://github.com/coil-kt/coil" target="_blank"> Coil</a>, and <a href="https://github.com/facebook/fresco" target="_blank"> Fresco.</a> This library supports tracing image loading states, composing custom implementations, and some useful animations, such as crossfades, blur transformation, and circular reveals. Also, with image plugins, you can configure and attach image loading behaviors more easily and fast.
</p>

## Who's using Landscapist?
👉 [Check out who's using Landscapist](/usecases.md).

Landscapist hits **+300,000 downloads every month** around the globe! 🚀

![globe](https://user-images.githubusercontent.com/24237865/196018576-a9c87534-81a2-4618-8519-0024b67964bf.png)

## Why Landscapist?

Landscapist is built with a lot of consideration to improve the performance of image loadings in Jetpack Compose. Most composable functions of Landscapist are Restartable and Skippable, which indicates fairly improved recomposition performance according to the Compose compiler metrics. Also, the library performance was improved with [Baseline Profiles](https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html). 

<details>
 <summary>See the Compose compiler metrics for Landscapist</summary>
 
![metrics](https://user-images.githubusercontent.com/24237865/201906004-f4490bdf-7af9-4ad6-b586-7dcc6f07d0c8.png)

</details>

## Demo projects
You can see the use cases of this library in the repositories below:
- [google/modernstorage](https://github.com/google/modernstorage/tree/e62cda539ca75884dd49df3bcf8629751f0a91e6/sample): ModernStorage is a group of libraries that provide an abstraction layer over storage on Android to simplify its interactions.
- [GetStream/WhatsApp-Clone-Compose](https://github.com/getStream/whatsApp-clone-compose): 📱 WhatsApp clone project demonstrates modern Android development built with Jetpack Compose and Stream Chat SDK for Compose.
- [android/storage-samples](https://github.com/android/storage-samples/tree/main/ScopedStorage): Multiple samples showing the best practices in storage APIs on Android.
- [skydoves/DisneyCompose](https://github.com/skydoves/disneycompose): 🧸 A demo Disney app using Jetpack Compose and Hilt based on modern Android tech-stacks and MVVM architecture.
- [skydoves/MovieCompose](https://github.com/skydoves/MovieCompose): 🎞 A demo movie app using Jetpack Compose and Hilt based on modern Android tech stacks. <br>

## SNAPSHOT

<details>
 <summary>See how to import the snapshot</summary>

### Including the SNAPSHOT
[![Landscapist](https://img.shields.io/static/v1?label=snapshot&message=landscapist&logo=apache%20maven&color=C71A36)](https://oss.sonatype.org/content/repositories/snapshots/com/github/skydoves/landscapist/) <br>
Snapshots of the current development version of Landscapist are available, which track [the latest versions](https://oss.sonatype.org/content/repositories/snapshots/com/github/skydoves/landscapist/).

To import snapshot versions on your project, add the code snippet below on your gradle file:
```Gradle
repositories {
   maven { url 'https://oss.sonatype.org/content/repositories/snapshots/' }
}
```

Next, add the dependency below to your **module**'s `build.gradle` file:
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-glide:2.1.1-SNAPSHOT"
}
```
</details>

<div class="header">
  <a href="https://github.com/bumptech/glide" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545537-1bc15200-0a39-11eb-883d-644f564da5d3.png" align="left" width="4%" alt="Glide" /></a>
  <h1>Glide</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

Add the codes below to your **root** `build.gradle` file (not your module-level build.gradle file):
```gradle
allprojects {
    repositories {
        mavenCentral()
    }
}
```

Next, add the dependency below to your **module**'s `build.gradle` file:
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-glide:2.1.0"
}
```

> **Note**: `Landscapist-Glide` includes version `4.14.2` of [Glide](https://github.com/bumptech/glide) internally. So please make sure your project is using the same Glide version or exclude the Glide dependency to adapt yours. Also, please make sure the Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

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

### More Details for GlideImage
<details>
 <summary>👉 Read further for more details</summary>

### Custom RequestOptions and TransitionOptions
You can customize your request-options with your own [RequestOptions](https://bumptech.github.io/glide/doc/options.html#requestoptions) and [TransitionOptions](https://bumptech.github.io/glide/doc/options.html#transitionoptions) for applying caching strategies, loading transformations like below:

```kotlin
GlideImage(
  imageModel = { imageUrl },
  requestOptions = {
    RequestOptions()
        .override(256, 256)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .centerCrop()
  }
)
```

### Custom RequestBuilder
You can request image with your own [RequestBuilder](https://bumptech.github.io/glide/doc/options.html#requestbuilder), which is the backbone of the request in Glide and is responsible for bringing your options together with your requested url or model to start a new load.

```kotlin
GlideImage(
  imageModel = { imageUrl },
  requestBuilder = { Glide.with(LocalContext.current.applicationContext).asDrawable() },
  modifier = Modifier.constrainAs(image) {
    centerHorizontallyTo(parent)
    top.linkTo(parent.top)
  }.aspectRatio(0.8f)
)
```

### Custom RequestListener
You can register your own [RequestListener](https://bumptech.github.io/glide/javadocs/440/com/bumptech/glide/request/RequestListener.html), which allows you to trace the status of a request while images load.

```kotlin
GlideImage(
  imageModel = { imageUrl },
  requestListener = object: RequestListener<Drawable> {
    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Drawable>?,
      isFirstResource: Boolean
    ): Boolean {
      // do something
      return false
    }

    override fun onResourceReady(
      resource: Drawable?,
      model: Any?,
      target: Target<Drawable>?,
      dataSource: DataSource?,
      isFirstResource: Boolean
    ): Boolean {
      // do something
      return true
    }
  }
)
```

### LocalGlideRequestOptions
You can pass the same instance of your `RequestOptions` down through the Composition in your composable hierarchy as following the example below:

```kotlin
val requestOptions = RequestOptions()
    .override(300, 300)
    .circleCrop()

CompositionLocalProvider(LocalGlideRequestOptions provides requestOptions) {
  // Loads images with the custom `requestOptions` without explicit defines.
  GlideImage(
    imageModel = ...
  )
}
```

</details>

<div class="header">
  <a href="https://github.com/coil-kt/coil" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545538-1cf27f00-0a39-11eb-83dd-ef9b8c6a74cb.png" align="left" width="4%" alt="Fresco" /></a>
  <h1>Coil</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>
Add the dependency below to your **module**'s `build.gradle` file:
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-coil:<version>"
}
```

> **Note**: Please make sure your project uses the same Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

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

### More Details for CoilImage
<details>
 <summary>👉 Read further for more details</summary>

### Custom ImageRequest and ImageLoader
You can load images with your own [ImageRequest](https://coil-kt.github.io/coil/image_requests/) and [ImageLoader](https://coil-kt.github.io/coil/image_loaders/), which provides all the necessary information for loading images like caching strategies and transformations.

```kotlin
CoilImage(
  imageRequest = {
      ImageRequest.Builder(LocalContext.current)
        .data(poster.poster)
        .crossfade(true)
        .build() },
  imageLoader = {
      ImageLoader.Builder(LocalContext.current)
        .availableMemoryPercentage(0.25)
        .crossfade(true)
        .build() },
  modifier = modifier,
)
```

 ### LocalCoilImageLoader
 You can pass the same instance of your `ImageLoader` down through the Composition in your composable hierarchy as following the example below:

 ```kotlin
 val imageLoader = ImageLoader.Builder(context).build()
CompositionLocalProvider(LocalCoilImageLoader provides imageLoader) {
  
   // This will automatically use the value of current imageLoader in the hierarchy.
   CoilImage(
     imageModel = ...
   )
 }
 ```

 <img src="https://user-images.githubusercontent.com/24237865/131246748-b88903a1-43de-4e6c-9069-3e956a0cf8a6.gif" align="right" width="32%"/>

## Animated Image Supports (GIF, Webp)
You can load animated GIFs and WebP Images with your `ImageLoader`.

```kotlin
val context = LocalContext.current
val imageLoader = ImageLoader.Builder(context)
  .componentRegistry {
    if (SDK_INT >= 28) {
      add(ImageDecoderDecoder(context))
    } else {
      add(GifDecoder())
    }
  }
  .build()

CoilImage(
    imageModel = { poster.gif }, // URL of an animated image.
    imageLoader = { imageLoader },
    shimmerParams = ShimmerParams(
      baseColor = background800,
      highlightColor = shimmerHighLight
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp)
      .height(500.dp)
      .clip(RoundedCornerShape(8.dp))
  )
```

</details>

<div class="header">
  <a href="https://github.com/facebook/fresco" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545540-1cf27f00-0a39-11eb-9e84-96b9df81364b.png" align="left" width="4%" alt="Fresco" /></a>
  <h1>Fresco</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

Add the dependency below to your **module**'s `build.gradle` file:
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-fresco:<version>"
}
```
> **Note**: `Landscapist-Fresco` includes version `2.6.0` of Fresco. So please make sure your project is using the same Fresco version or exclude the Fresco dependency to adapt yours. Also, please make sure the Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

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

### More Details for FrescoImage
<details>
 <summary>👉 Read further for more details</summary>

### Custom ImageRequest
You can load images with your own [ImageRequest](https://frescolib.org/docs/image-requests.html), which provides some necessary information for loading images like decoding strategies and resizing.

```kotlin
val imageRequest = ImageRequestBuilder
  .newBuilderWithSource(uri)
  .setImageDecodeOptions(decodeOptions)
  .setLocalThumbnailPreviewsEnabled(true)
  .setLowestPermittedRequestLevel(RequestLevel.FULL_FETCH)
  .setProgressiveRenderingEnabled(false)
  .setResizeOptions(ResizeOptions(width, height))
  .build()

FrescoImage(
  imageUrl = stringImageUrl,
  imageRequest = { imageRequest }
)
```

### LocalFrescoImageRequest
You can pass the same instance of your `imageRequestBuilder` down through the Composition in your composable hierarchy as following the example below:

```kotlin
// customize the ImageRequest as needed
val imageRequestBuilder = ImageRequestBuilder
  .newBuilderWithSource(uri)
  .setImageDecodeOptions(decodeOptions)
  .setLocalThumbnailPreviewsEnabled(true)
  .setLowestPermittedRequestLevel(RequestLevel.FULL_FETCH)
  .setProgressiveRenderingEnabled(false)
  .setResizeOptions(ResizeOptions(width, height))

CompositionLocalProvider(LocalFrescoImageRequest provides imageRequestBuilder) {
  // This will automatically use the value of current ImageRequest in the hierarchy.
  FrescoImage(
    imageUrl = ...
  )
}
```

<img src="https://user-images.githubusercontent.com/24237865/131246748-b88903a1-43de-4e6c-9069-3e956a0cf8a6.gif" align="right" width="32%"/>

## Fresco Animated Image Support (GIF, Webp)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

Add the below dependency to your **module**'s `build.gradle` file.

```gradle
dependencies {
  implementation "com.github.skydoves:landscapist-fresco-websupport:<version>"
}
```

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

</details>

## ImageOptions

You can give image options to your image composable functions by passing `ImageOptions` instance like the below:

```kotlin
GlideImage(
  ..
  imageOptions = ImageOptions(
      contentScale = ContentScale.Crop,
      alignment = Alignment.Center,
      contentDescription = "main image",
      colorFilter = null,
      alpha = 1f
    )
)
```

### RequestSize

You can set the request size of your image by giving `requestSize` property as seen in the below:

```kotlin
GlideImage(
  ..
  imageOptions = ImageOptions(requestSize = IntSize(800, 600)),
)
```

## Listening image state changes

You can listen the image state changes by giving `onImageStateChanged` parameter to your image composable functions like the below:

```kotlin
GlideImage(
  ..
  onImageStateChanged = {
    when (it) {
      GlideImageState.None -> ..
      GlideImageState.Loading -> ..
      is GlideImageState.Success -> ..
      is GlideImageState.Failure -> ..
    }
  }
)
```

> **Note**: You can use `CoilImageState` for `CoilImage` and `FrescoImageState` for `FrescoImage`.

### DataSource

For the success state, you can trace the origin of the image with the `DataSource` parameter. `DataSource` represents the following source origins below:

- **Memory**: Represents an in-memory data source or cache (e.g. bitmap, ByteBuffer).
- **Disk**: Represents a disk-based data source (e.g. drawable resource, or File).
- **Network**: Represents a network-based data source.
- **Unknown**: Represents an unknown data source.

<img src="https://user-images.githubusercontent.com/24237865/94174882-d6e1db00-fed0-11ea-86ec-671b5039b1b9.gif" align="right" width="310px"/>

## Custom Composables
You can execute your own composable functions depending on the three request states below:

- **loading**: Executed while loading an image.
- **success**: Executed if loading an image successfully.
- **failure**: Executed if fails to load an image (e.g. network error, wrong destination).

```kotlin
GlideImage( // CoilImage, FrescoImage
  imageModel = { imageUrl },
  modifier = modifier,
  // shows an indicator while loading an image.
  loading = {
    Box(modifier = Modifier.matchParentSize()) {
      CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center)
      )
    }
  },
  // shows an error text if fail to load an image.
  failure = {
    Text(text = "image request failed.")
  }
)
```

Also, you can customize the image content with our own composable function like the example below:

```kotlin
GlideImage( // CoilImage, FrescoImage
  imageModel = { imageUrl },
  // draw a resized image.
  success = { imageState ->
    imageState.imageBitmap?.let {
      Image(
        bitmap = it,
        modifier = Modifier.size(128.dp)
      )
    }
  },
  loading = { 
    // do something 
  }
)
```
> **Note**: You can also use the custom Composables for **`CoilImage`** and **`FrescoImage`**.

<img src="https://user-images.githubusercontent.com/24237865/148672035-6a82eba5-900c-44ee-a42c-acbf8038d0ab.png" align="right" width="46%">


## Preview on Android Studio
Landscapist supports preview mode for each image library; **Glide**, **Coil**, and **Fresco**. You can show the preview image on your editor with a `previewPlaceholder` parameter as following:

```kotlin
GlideImage(
  imageModel = { imageUrl },
  modifier = Modifier.aspectRatio(0.8f),
  previewPlaceholder = R.drawable.poster
)
```
> **Note**: You can also use the the `previewPlaceholder` parameter for **`CoilImage`** and **`FrescoImage`**.

## ImageComponent and ImagePlugin

You can compose supported image plugins by Landscapist or you can create your own image plugin that will be composed following the image loading state.
`ImagePlugin` is a pluggable compose interface that will be executed for loading images. `ImagePlugin` provides following types below:

- **PainterPlugin**: A pinter plugin interface to be composed with the given `Painter`.
- **LoadingStatePlugin**: A pluggable state plugin that will be composed while the state is `ImageLoadState.Loading`.
- **SuccessStatePlugin**: A pluggable state plugin that will be composed when the state is `ImageLoadState.Success`.
- **FailureStatePlugin**: A pluggable state plugin that will be composed when the state is `ImageLoadState.Failure`.

For example, you can implement your own `LoadingStatePlugin` that will be composed while loading an image like the below:

```kotlin
public data class LoadingPlugin(val source: Any?) : ImagePlugin.LoadingStatePlugin {

  @Composable
  override fun compose(
    modifier: Modifier,
    imageOptions: ImageOptions?
  ): ImagePlugin = apply {
    if (source != null && imageOptions != null) {
      ImageBySource(
        source = source,
        modifier = modifier,
        alignment = imageOptions.alignment,
        contentDescription = imageOptions.contentDescription,
        contentScale = imageOptions.contentScale,
        colorFilter = imageOptions.colorFilter,
        alpha = imageOptions.alpha
      )
    }
  }
}
```

Next, you can compose plugins by adding them in the `rememberImageComponent` like the below:

```kotlin
GlideImage(
  imageModel = { poster.image },
  component = rememberImageComponent {
    add(CircularRevealPlugin())
    add(LoadingPlugin(source))
  },
)
```

or you can just add plugins by using the **+** expression like the below:

```kotlin
GlideImage(
  imageModel = { poster.image },
  component = rememberImageComponent {
    +CircularRevealPlugin()
    +LoadingPlugin(source)
  },
)
```

### LocalImageComponent

You can provide the same `ImageComponent` instance in the composable hierarchy by using `imageComponent` extension and `LocalImageComponent` like the below:

```kotlin
val component = imageComponent {
  +CrossfadePlugin()
  +PalettePlugin()
}

CompositionLocalProvider(LocalImageComponent provides component) {
  ..
}
```

## Placeholder

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

The `landscapist-placeholder` package provides useful image plugins, such as loading & failure placeholder supports and shimmering animation.
To use placeholder supports, add the dependency below:

```groovy
dependencies {
    implementation "com.github.skydoves:landscapist-placeholder:$version"
}
```

<img src="https://user-images.githubusercontent.com/24237865/95812167-be3a4780-0d4f-11eb-9360-2a4a66a3fb46.gif" align="right" width="250px"/>

### ShimmerPlugin
You can implement a shimmering effect while loading an image by using the `ShimmerPlugin` as following the example below:

```kotlin
GlideImage( // CoilImage, FrescoImage
  imageModel = { imageUrl },
  modifier = modifier,
  component = rememberImageComponent {
    // shows a shimmering effect when loading an image.
    +ShimmerPlugin(
      baseColor = background800,
      highlightColor = shimmerHighLight
    )
  },
  // shows an error text message when request failed.
  failure = {
    Text(text = "image request failed.")
  }
)
 ```
 > **Note**: You can also use the Shimmer effect for **`CoilImage`** and **`FrescoImage`**.

### PlaceholderPlugin

You can show your own placeholder while loading an image or when fails to load an image with `PlaceholderPlugin.Loading` and `PlaceholderPlugin.Failure`.

```kotlin
GlideImage(
  ..
  component = imageComponent {
      +PlaceholderPlugin.Loading(painterResource(id = R.drawable.placeholder_loading))
      +PlaceholderPlugin.Failure(painterResource(id = R.drawable.placeholder_failure))
    },
)
```

> **Note**: The source should be one of `ImageBitmap`, `ImageVector`, or `Painter`.

## Animation

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

The `landscapist-animation` package provides useful image plugins related to animations, such as crossfade and circular reveal animation.
To use animation supports, add the dependency below:

```groovy
dependencies {
    implementation "com.github.skydoves:landscapist-animation:$version"
}
```

### Preview

| Circular Reveal | Crossfade |
| :---------------: | :---------------: |
| <img src="https://user-images.githubusercontent.com/24237865/189552544-5f8e1209-4930-45e6-a050-3a0cda088e9f.gif" align="center" width="100%"/> | <img src="https://user-images.githubusercontent.com/24237865/189552547-d933cee7-e811-4170-a806-1ac165e8f055.gif" align="center" width="100%"/> | 


### Crossfade Animation

You can implement the crossfade animation while drawing images with `CrossfadePlugin` as the following:

```kotlin
GlideImage(
  imageModel = { poster.image },
  component = rememberImageComponent {
    +CrossfadePlugin(
      duration = 550
    )
  }
)
```

 > **Note**: You can also use the crossfade animation for **`CoilImage`** and **`FrescoImage`**.

### Circular Reveal Animation
You can implement the circular reveal animation while drawing images with `CircularRevealplugin` as the following:

```kotlin
GlideImage(
  imageModel = { poster.image },
  component = rememberImageComponent {
    +CircularRevealPlugin(
      duration = 350
    )
  }
)
```

 > **Note**: You can also use the Circular Reveal animation for **`CoilImage`** and **`FrescoImage`**.

 ## Transformation

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

<img src="https://user-images.githubusercontent.com/24237865/196038507-54a3a79c-2e8e-45ec-b5e8-5de65cd50248.png" align="right" width="250"/>

The `landscapist-transformation` package provides useful image transformation plugins, such as the blur effect.
To use transformation supports, add the dependency below:

```groovy
dependencies {
    implementation "com.github.skydoves:landscapist-transformation:$version"
}
```

### BlurTransformationPlugin

You can implement the blur effect with `BlurTransformationPlugin` as the following:

```kotlin
GlideImage( // CoilImage, FrescoImage also can be used.
  imageModel = { poster.image },
  component = rememberImageComponent {
      +BlurTransformationPlugin(radius = 10)
  }
)
```

>**Note**: Landscapist's blur transformation falls back onto a CPU-based implementation to support older API levels. So you don't need to worry about API compatibilities and performance issues.

## Palette

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

The `landscapist-palette` package provides useful image plugins related to palette, such as extracting primary color sets.
To use palette supports, add the dependency below:

```groovy
dependencies {
    implementation "com.github.skydoves:landscapist-palette:$version"
}
```

You can extract primary (theme) color profiles with `PalettePlugin`. You can check out [Extract color profiles](https://developer.android.com/training/material/palette-colors#extract-color-profiles) to see what kinds of colors can be extracted.

<img src="https://user-images.githubusercontent.com/24237865/129226361-877689b8-a1ec-4f59-b8a6-e2efe33a8de7.gif" align="right" width="250"/>

```kotlin
var palette by remember { mutableStateOf<Palette?>(null) }

GlideImage( // CoilImage, FrescoImage also can be used.
  imageModel = { poster.image },
  component = rememberImageComponent {
      +PalettePlugin { palette = it }
  }
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

Also, you can customize attributes of `PalettePlugin` like the example below:

```kotlin
var palette by remember { mutableStateOf<Palette?>(null) }

GlideImage( // CoilImage, FrescoImage also can be used.
  imageModel = { poster.image },
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
  }
)
```
 > **Note**: You can also use the Palette for **`CoilImage`** and **`FrescoImage`**.

 ## BOM

 [![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

 The landscapist Bill of Materials (BOM) lets you manage all of your landscapist library versions by specifying only the BOM’s version.

 ```groovy
dependencies {
    // Import the landscapist BOM
    implementation "com.github.skydoves:landscapist-bom:$version"

    // Import landscapist libraries
    implementation "com.github.skydoves:landscapist-glide" // fresco or coil
    implementation "com.github.skydoves:landscapist-placeholder"
    implementation "com.github.skydoves:landscapist-palette"
    implementation "com.github.skydoves:landscapist-transformation"
}
 ```

## Who's using Landscapist?
If your project uses Landscapist, please let me know by creating a new issue! 🤗

## [Twitter for Android](https://user-images.githubusercontent.com/24237865/125583736-f0ffa76f-8f87-433b-a9fd-192231dc5e63.jpg)

[![twitter](https://user-images.githubusercontent.com/24237865/125583182-9527dd48-433e-4e17-ae52-3f2bb544a847.jpg)](https://play.google.com/store/apps/details?id=com.twitter.android&hl=ko&gl=US)

## Inspiration
This library was mostly inspired by [Accompanist](https://github.com/chrisbanes/accompanist).<br>

> Accompanist is a group of libraries that contains some utilities which I've found myself copying around projects which use Jetpack Compose. Currently, it contains image loading and insets. You can get more variety and recent systems from the library maintained by Google.

## Find this repository useful? :heart:
Support it by joining __[stargazers](https://github.com/skydoves/Landscapist/stargazers)__ for this repository. :star: <br>
Also __[follow](https://github.com/skydoves)__ me for my next creations! 🤩

# License
```xml
Designed and developed by 2020 skydoves (Jaewoong Eum)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
