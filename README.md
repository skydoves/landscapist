

![landscapist](https://user-images.githubusercontent.com/24237865/127760344-bb042fe8-23e1-4014-b208-b7b549d32086.png)<br><br>

<p align="center">
  <a href="https://devlibrary.withgoogle.com/products/android/repos/skydoves-Landscapist"><img alt="Google" src="https://skydoves.github.io/badges/google-devlib.svg"/></a><br>
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://android-arsenal.com/api?level=21"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://github.com/skydoves/Landscapist/actions"><img alt="Build Status" src="https://github.com/skydoves/Frescomposable/workflows/Android%20CI/badge.svg"/></a>
  <a href="https://androidweekly.net/issues/issue-441"><img alt="Android Weekly" src="https://skydoves.github.io/badges/android-weekly.svg"/></a>
  <a href="https://proandroiddev.com/loading-images-for-jetpack-compose-using-glide-coil-and-fresco-1211261a296e"><img alt="Medium" src="https://skydoves.github.io/badges/Story-Medium.svg"/></a>
  <a href="https://github.com/skydoves"><img alt="Profile" src="https://skydoves.github.io/badges/skydoves.svg"/></a> 
</p>

<p align="center">
🍂 Jetpack Compose image loading library which fetches and displays network images with <a href="https://github.com/bumptech/glide" target="_blank"> Glide</a>, <a href="https://github.com/coil-kt/coil" target="_blank"> Coil</a>, and <a href="https://github.com/facebook/fresco" target="_blank"> Fresco</a>
</p>

## Who's using Landscapist?
👉 [Check out who's using Landscapist](/usecases.md).

## Demo projects
You can see the use cases of this library in the repositories below:
- [DisneyCompose](https://github.com/skydoves/disneycompose) - 🧸 A demo Disney app using Jetpack Compose and Hilt based on modern Android tech-stacks and MVVM architecture.
- [MovieCompose](https://github.com/skydoves/MovieCompose) - 🎞 A demo movie app using Jetpack Compose and Hilt based on modern Android tech stacks. <br>

## SNAPSHOT
<details>
 <summary>See how to import the snapshot</summary>

### Including the SNAPSHOT
[![Balloon](https://img.shields.io/static/v1?label=snapshot&message=landscapist&logo=apache%20maven&color=C71A36)](https://oss.sonatype.org/content/repositories/snapshots/com/github/skydoves/landscapist/) <br>
Snapshots of the current development version of Landscapist are available, which track [the latest versions](https://oss.sonatype.org/content/repositories/snapshots/com/github/skydoves/landscapist/).

To import snapshot versions on your project, add the code snippet below on your gradle file.
```Gradle
repositories {
   maven { url 'https://oss.sonatype.org/content/repositories/snapshots/' }
}
```
</details>

<div class="header">
  <a href="https://github.com/bumptech/glide" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545537-1bc15200-0a39-11eb-883d-644f564da5d3.png" align="left" width="4%" alt="Glide" /></a>
  <h1>Glide</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

Add the below codes to your **root** `build.gradle` file (not your module-level build.gradle file):
```gradle
allprojects {
    repositories {
        mavenCentral()
    }
}
```

Next, add the below dependency to your **module**'s `build.gradle` file:
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-glide:1.4.4"
}
```

> Note: `Landscapist-Glide` includes version `4.12.0` of [Glide](https://github.com/bumptech/glide) internally. So please make sure your project is using the same Glide version or exclude the Glide dependency to adapt yours. Also, please make sure the Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

### GlideImage
You can load images simply by using `GlideImage` composable function as the following example below:

```kotlin
GlideImage(
  imageModel = imageUrl,
  // Crop, Fit, Inside, FillHeight, FillWidth, None
  contentScale = ContentScale.Crop,
  // shows an image with a circular revealed animation.
  circularReveal = CircularReveal(duration = 250),
  // shows a placeholder ImageBitmap when loading.
  placeHolder = ImageBitmap.imageResource(R.drawable.placeholder),
  // shows an error ImageBitmap when the request failed.
  error = ImageBitmap.imageResource(R.drawable.error)
)
```

### More Details for GlideImage
<details>
 <summary>👉 Read further for more details</summary>

### Custom RequestOptions and TransitionOptions
You can customize your request-options with your own [RequestOptions](https://bumptech.github.io/glide/doc/options.html#requestoptions) and [TransitionOptions](https://bumptech.github.io/glide/doc/options.html#transitionoptions) for applying caching strategies, loading transformations like below:

```kotlin
GlideImage(
  imageModel = poster.poster,
  requestOptions = {
    RequestOptions()
        .override(256, 256)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .centerCrop()
  },
  contentScale = ContentScale.Crop,
  modifier = modifier,
  alignment = Alignment.Center,
)
```

### Custom RequestBuilder
You can request image with your own [RequestBuilder](https://bumptech.github.io/glide/doc/options.html#requestbuilder), which is the backbone of the request in Glide and is responsible for bringing your options together with your requested url or model to start a new load.

```kotlin
GlideImage(
  imageModel = poster.poster,
  requestBuilder = { Glide.with(LocalContext.current.applicationContext).asDrawable() },
  modifier = Modifier.constrainAs(image) {
    centerHorizontallyTo(parent)
    top.linkTo(parent.top)
  }.aspectRatio(0.8f)
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
Add the below dependency to your **module**'s `build.gradle` file.
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-coil:<version>"
}
```

> Note: Please make sure your project uses the same Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

### CoilImage
You can load images by using the `CoilImage` composable function as the following example below:

```kotlin
CoilImage(
  imageModel = imageUrl,
  // Crop, Fit, Inside, FillHeight, FillWidth, None
  contentScale = ContentScale.Crop,
  // shows an image with a circular revealed animation.
  circularReveal = CircularReveal(duration = 250),
  // shows a placeholder ImageBitmap when loading.
  placeHolder = ImageBitmap.imageResource(R.drawable.placeholder),
  // shows an error ImageBitmap when the request failed.
  error = ImageBitmap.imageResource(R.drawable.error)
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
  contentScale = ContentScale.Crop,
  modifier = modifier,
  alignment = Alignment.Center,
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
    imageModel = poster.gif, // URL of an animated image.
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

Add the below dependency to your **module**'s `build.gradle` file.
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-fresco:<version>"
}
```
> Note: `Landscapist-Fresco` includes version `2.6.0` of Fresco. So please make sure your project is using the same Fresco version or exclude the Fresco dependency to adapt yours. Also, please make sure the Jetpack Compose version on the [release page](https://github.com/skydoves/Landscapist/releases).

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
  imageUrl = stringImageUrl,
  // Crop, Fit, Inside, FillHeight, FillWidth, None
  contentScale = ContentScale.Crop,
  // shows an image with a circular revealed animation.
  circularReveal = CircularReveal(duration = 250),
  // shows a placeholder ImageBitmap when loading.
  placeHolder = ImageBitmap.imageResource(R.drawable.placeholder),
  // shows an error ImageBitmap when the request failed.
  error = ImageBitmap.imageResource(R.drawable.error)
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
  imageRequest = { imageRequest },
  contentScale = ContentScale.Crop)
```

### LocalFrescoImageRequest
You can pass the same instance of your `ImageRequest` down through the Composition in your composable hierarchy as following the example below:

```kotlin
// customize the ImageRequest as needed
val imageRequest = ImageRequestBuilder
  .newBuilderWithSource(uri)
  .setImageDecodeOptions(decodeOptions)
  .setLocalThumbnailPreviewsEnabled(true)
  .setLowestPermittedRequestLevel(RequestLevel.FULL_FETCH)
  .setProgressiveRenderingEnabled(false)
  .setResizeOptions(ResizeOptions(width, height))
  .build()

CompositionLocalProvider(LocalFrescoImageRequest provides imageRequest) {
  // This will automatically use the value of current ImageRequest in the hierarchy.
  FrescoImage(
    imageurl = ...
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
  controllerBuilder = Fresco.newDraweeControllerBuilder()
    .setUri(poster.gif) // GIF or Webp image url.
    .setAutoPlayAnimations(true),
  modifier = Modifier
    .fillMaxWidth()
    .height(300.dp)
)
```

For more details, check out [DraweeController](https://frescolib.org/docs/animations.html), and [Supported URIs](https://frescolib.org/docs/supported-uris.html) for setting URI addresses. Also, you can load general images (jpeg, png, etc) which can be loaded with `FrescoImage` by using `FrescoWebImage` and your custom controller.

</details>

## Custom Composables
You can build compose with your own composable functions following the three request states.

- **loading**: While loading an image, the indicator will be shown up.
- **success**: If succeed to load an image, the indicator will be gone and a content image will be shown. 
- **failure**: If fail to load an image (e.g. network error, wrong destination), an error placeholder will be shown up instead.

<img src="https://user-images.githubusercontent.com/24237865/94174882-d6e1db00-fed0-11ea-86ec-671b5039b1b9.gif" align="right" width="28%"/>

```kotlin
 GlideImage( // CoilImage, FrescoImage
   imageModel = imageUrl,
   modifier = modifier,
   // shows an indicator while loading an image.
   loading = {
     ConstraintLayout(
       modifier = Modifier.fillMaxSize()
     ) {
       val indicator = createRef()
       CircularProgressIndicator(
         modifier = Modifier.constrainAs(indicator) {
           top.linkTo(parent.top)
           bottom.linkTo(parent.bottom)
          start.linkTo(parent.start)
          end.linkTo(parent.end)
         }
       )
     }
   },
   // shows an error text if fail to load an image.
   failure = {
     Text(text = "image request failed.")
   })
```

Also, you can customize the image content with our own composable function like the example below:

```kotlin
GlideImage( // CoilImage, FrescoImage
  imageModel = imageUrl,
  // draw a resized image.
  success = { frescoImageState ->
    frescoImageState.imageBitmap?.let {
      Image(
        bitmap = it,
        modifier = Modifier
          .width(128.dp)
          .height(128.dp))
    }
  },
  loading = { 
    // do something 
  })
```

<img src="https://user-images.githubusercontent.com/24237865/95812167-be3a4780-0d4f-11eb-9360-2a4a66a3fb46.gif" align="right" width="26%"/>

## Shimmer effect
You can implement a shimmering effect while loading an image by using the `ShimmerParams` parameter as following the example below:

```kotlin
GlideImage( // CoilImage, FrescoImage
   imageModel = imageUrl,
   modifier = modifier,
   // shows a shimmering effect when loading an image.
   shimmerParams = ShimmerParams(
       baseColor = MaterialTheme.colors.background,
       highlightColor = shimmerHighLight,
       durationMillis = 350,
       dropOff = 0.65f,
       tilt = 20f
     ),
   // shows an error text message when request failed.
   failure = {
     Text(text = "image request failed.")
   })
 ```

 <img src="https://user-images.githubusercontent.com/24237865/95661452-6abad480-0b6a-11eb-91c4-7cbe40b77927.gif" align="right" width="26%"/>

## Circular Reveal Animation
You can implement the circular reveal animation while drawing images with `circularRevealedEnabled` attribute as `true`.

```kotlin
GlideImage( // CoilImage, FrescoImage
  imageModel = imageUrl,
  // Crop, Fit, Inside, FillHeight, FillWidth, None
  contentScale = ContentScale.Crop,
  // shows an image with a circular revealed animation.
  circularRevealedEnabled = true,
  // shows a placeholder ImageBitmap when loading.
  placeHolder = ImageBitmap.imageResource(R.drawable.placeholder),
  // shows an error ImageBitmap when the request failed.
  error = ImageBitmap.imageResource(R.drawable.error)
)
```
The default value of the `circularRevealedEnabled` is `false`.

## Palette
You can extract major (theme) color profiles with `BitmapPalette`. You can check out [Extract color profiles](https://developer.android.com/training/material/palette-colors#extract-color-profiles) to see which kinds of colors can be extracted.

<img src="https://user-images.githubusercontent.com/24237865/129226361-877689b8-a1ec-4f59-b8a6-e2efe33a8de7.gif" align="right" width="26%"/>

```kotlin
var palette by remember { mutableStateOf<Palette?>(null) }

GlideImage( // CoilImage, FrescoImage also can be used.
  imageModel = imageUrl,
  bitmapPalette = BitmapPalette {
    palette = it
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
Also, you can customize attributes of `BitmapPalette` like the example below:

```kotlin
var palette by remember { mutableStateOf<Palette?>(null) }

GlideImage( // CoilImage, FrescoImage also can be used.
  imageModel = imageUrl,
  modifier = Modifier
    .aspectRatio(0.8f),
  bitmapPalette = BitmapPalette(
    imageModel = poster.poster,
    useCache = true,
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
)
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
