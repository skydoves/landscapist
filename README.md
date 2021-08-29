

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
🍂 Jetpack Compose image loading library which can fetch and display network images using <a href="https://github.com/bumptech/glide" target="_blank"> Glide</a>, <a href="https://github.com/coil-kt/coil" target="_blank"> Coil</a>, <a href="https://github.com/facebook/fresco" target="_blank"> Fresco</a>
</p>

## Who's using Landscapist?
[See who's using Landscapist](/usecases.md).

## Usecase
You can see the use cases of this library in the below repositories.
- [DisneyCompose](https://github.com/skydoves/disneycompose) - 🧸 A demo Disney app using Jetpack Compose and Hilt based on modern Android tech-stacks and MVVM architecture.
- [MovieCompose](https://github.com/skydoves/MovieCompose) - 🎞 A demo movie app using Jetpack Compose and Hilt based on modern Android tech stacks. <br>

<div class="header">
  <a href="https://github.com/bumptech/glide" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545537-1bc15200-0a39-11eb-883d-644f564da5d3.png" align="left" width="4%" alt="Glide" /></a>
  <h1>Glide</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>

<img src="https://user-images.githubusercontent.com/24237865/95661452-6abad480-0b6a-11eb-91c4-7cbe40b77927.gif" align="right" width="32%"/>

Add below codes to your **root** `build.gradle` file (not your module build.gradle file).
```gradle
allprojects {
    repositories {
        mavenCentral()
    }
}
```
Also add a dependency code to your **module**'s `build.gradle` file.
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-glide:1.3.4"
}
```

### Usage
We can request and load images simply using a `GlideImage` composable function.
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

#### RequestOptions and TransitionOptions
We can customize our request options using [RequestOptions](https://bumptech.github.io/glide/doc/options.html#requestoptions) and [TransitionOptions](https://bumptech.github.io/glide/doc/options.html#transitionoptions) for applying caching strategies, loading transformations.
```kotlin
GlideImage(
  imageModel = poster.poster,
  requestOptions = RequestOptions()
    .override(256, 256)
    .diskCacheStrategy(DiskCacheStrategy.ALL)
    .centerCrop(),
  contentScale = ContentScale.Crop,
  modifier = modifier,
  alignment = Alignment.Center,
)
```

#### RequestBuilder
Also we can request image by passing a [RequestBuilder](https://bumptech.github.io/glide/doc/options.html#requestbuilder). RequestBuilder is the backbone of the request in Glide and is responsible for bringing your options together with your requested url or model to start a new load.
```kotlin
GlideImage(
  imageModel = poster.poster,
  requestBuilder = Glide
    .with(LocalView.current)
    .asBitmap()
    .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
    .thumbnail(0.1f)
    .transition(withCrossFade()),
  modifier = Modifier.constrainAs(image) {
    centerHorizontallyTo(parent)
    top.linkTo(parent.top)
  }.aspectRatio(0.8f)
)
```

#### LocalGlideRequestBuilder
We can provide the same instance of the `RequestBuilder` in the composable hierarchy.
```kotlin
// customize the RequestBuilder as needed
val requestBuilder = Glide.with(LocalView.current)
  .asBitmap()
  .thumbnail(0.1f)
  .transition(BitmapTransitionOptions.withCrossFade())

CompositionLocalProvider(LocalGlideRequestBuilder provides requestBuilder) {
  // This will automatically use the value of current RequestBuilder in the hierarchy.
  GlideImage(
    imageModel = ...
  )
}
```

<img src="https://user-images.githubusercontent.com/24237865/94174882-d6e1db00-fed0-11ea-86ec-671b5039b1b9.gif" align="right" width="32%"/>

### Composable loading, success, failure
We can create our own composable functions following requesting states.<br>
Here is an example that shows a progress indicator when loading an image,<br>
After complete requesting, the indicator will be gone and a content image will be shown.<br>
If the request failed (e.g. network error, wrong destination), error text will be shown.
```kotlin
 GlideImage(
 imageModel = poster.poster,
 modifier = modifier,
 // shows a progress indicator when loading an image.
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
 // shows an error text message when request failed.
 failure = {
   Text(text = "image request failed.")
 })
```

<div class="header">
  <a href="https://github.com/coil-kt/coil" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545538-1cf27f00-0a39-11eb-83dd-ef9b8c6a74cb.png" align="left" width="4%" alt="Fresco" /></a>
  <h1>Coil</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>
Add a dependency code to your **module**'s `build.gradle` file.
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-coil:<version>"
}
```

### Usage
We can request and load images simply using a `CoilImage` composable function.
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

#### ImageRequest and ImageLoader
We can customize request options using [ImageRequest](https://coil-kt.github.io/coil/image_requests/) and [ImageLoader](https://coil-kt.github.io/coil/image_loaders/) for providing all the necessary information for loading images like caching strategies and transformations.

```kotlin
CoilImage(
  imageRequest = ImageRequest.Builder(LocalContext.current)
    .data(poster.poster)
    .crossfade(true)
    .build(),
  imageLoader = ImageLoader.Builder(LocalContext.current)
    .availableMemoryPercentage(0.25)
    .crossfade(true)
    .build(),
  contentScale = ContentScale.Crop,
  modifier = modifier,
  alignment = Alignment.Center,
)
```

<img src="https://user-images.githubusercontent.com/24237865/94174882-d6e1db00-fed0-11ea-86ec-671b5039b1b9.gif" align="right" width="32%"/>

### Composable loading, success, failure
We can create our own composable functions following requesting states. Here is an example that shows a progress indicator when loading an image, After complete requesting, the indicator will be gone and a content image will be shown. If the request failed (e.g. network error, wrong destination), error text will be shown.
```kotlin
CoilImage(
  imageModel = poster.poster,
  modifier = Modifier.constrainAs(image) {
    centerHorizontallyTo(parent)
    top.linkTo(parent.top)
  }.aspectRatio(0.8f),
  // shows a progress indicator when loading an image.
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
  // shows an error text message when request failed.
  failure = {
    Text(text = "image request failed.")
  })
```

<img src="https://user-images.githubusercontent.com/24237865/95812167-be3a4780-0d4f-11eb-9360-2a4a66a3fb46.gif" align="right" width="26%"/>

### Shimmer effect
We can give a shimmering effect when loading images using a `ShimmerParams`. We can also use `ShimmerParams` in `GlideImage` and `FrescoImage`.
```kotlin
 CoilImage(
 imageModel = poster.poster,
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

 ### LocalCoilImageLoader
 We can provide the same instance of the `ImageLoader` in the composable hierarchy.
 ```kotlin
 val imageLoader = ImageLoader.Builder(context)
    // customize the ImageLoader as needed
    .build()
CompositionLocalProvider(LocalCoilImageLoader provides imageLoader) {
   // This will automatically use the value of current imageLoader in the hierarchy.
   CoilImage(
     imageModel = ...
   )
 }
 ```

<div class="header">
  <a href="https://github.com/facebook/fresco" target="_blank"> <img src="https://user-images.githubusercontent.com/24237865/95545540-1cf27f00-0a39-11eb-9e84-96b9df81364b.png" align="left" width="4%" alt="Fresco" /></a>
  <h1>Fresco</h1>
</div>

[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>
Add a dependency code to your **module**'s `build.gradle` file.
```gradle
dependencies {
    implementation "com.github.skydoves:landscapist-fresco:<version>"
}
```

### Initialize
We should initialize `Fresco` using [ImagePipelineConfig](https://frescolib.org/docs/configure-image-pipeline.html) in our `Application` class.<br>
If we need to fetch images from the network, recommend using `OkHttpImagePipelineConfigFactory`.<br>
By using an `ImagePipelineConfig`, we can customize caching, networking, and thread pool strategies.<br>
[Here](https://fresco.buzhidao.net/javadoc/reference/com/facebook/imagepipeline/core/ImagePipelineConfig.Builder.html) are more references related to the pipeline config.
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

<img src="https://user-images.githubusercontent.com/24237865/95661452-6abad480-0b6a-11eb-91c4-7cbe40b77927.gif" align="right" width="32%"/>

### Usage
We can request and load images simply using a `FrescoImage` composable function.
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
We can customize our requests using an [ImageRequest](https://frescolib.org/docs/image-requests.html) that consists only of a URI, we can use the helper method ImageRequest.fromURI.
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
  imageRequest = imageRequest,
  contentScale = ContentScale.Crop)
```

<img src="https://user-images.githubusercontent.com/24237865/94174882-d6e1db00-fed0-11ea-86ec-671b5039b1b9.gif" align="right" width="32%"/>

### Composable loading, success, failure
We can create our own composable functions following requesting states.<br>
Here is an example that shows a progress indicator when loading an image,<br>
After complete requesting, the indicator will be gone and a content image will be shown.<br>
If the request failed (e.g. network error, wrong destination), error text will be shown.
```kotlin
 FrescoImage(
 imageUrl = stringImageUrl,
 modifier = modifier,
 // shows a progress indicator when loading an image.
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
 // shows an error text message when request failed.
 failure = {
   Text(text = "image request failed.")
 })
```
Also, we can customize the content image using our own composable function like below.
```kotlin
FrescoImage(
    imageUrl = imageUrl,
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

#### LocalFrescoImageRequest
We can provide the same instance of the `ImageRequest` in the composable hierarchy.
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

<img src="https://user-images.githubusercontent.com/24237865/129226361-877689b8-a1ec-4f59-b8a6-e2efe33a8de7.gif" align="right" width="32%"/>

## Palette
We can extract major (theme) color profiles using `BitmapPalette`. Basically, we should use `BitmapPalette` for extracting the major colors from image. You can reference which kinds of colors can be extracted [here](https://developer.android.com/training/material/palette-colors#extract-color-profiles).

```kotlin
var palette by remember { mutableStateOf<Palette?>(null) }

GlideImage( // CoilImage, FrescoImage also can be used.
  imageModel = poster?.poster!!,
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
Also we can customize attributes of `BitmapPalette` like the below.

```kotlin
  var palette by remember { mutableStateOf<Palette?>(null) }

  GlideImage(
    imageModel = poster?.poster!!,
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

<img src="https://user-images.githubusercontent.com/24237865/131246748-b88903a1-43de-4e6c-9069-3e956a0cf8a6.gif" align="right" width="32%"/>

## Fresco Animated Support (GIF, Webp)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.skydoves/landscapist.svg?label=Maven%20Central)](https://search.maven.org/search?q=landscapist)<br>
Add a dependency code to your **module**'s `build.gradle` file.
```gradle
dependencies {
  implementation "com.github.skydoves:landscapist-fresco-websupport:<version>"
}
```

Fresco supports animated GIF and WebP Images using `FrescoWebImage` composable function.We should pass the `AbstractDraweeController` that can be created like the below.
You can reference how to build the [DraweeController](https://frescolib.org/docs/animations.html), and [Supported URIs](https://frescolib.org/docs/supported-uris.html) for setting uri address. Also, we can load a normal image (jpeg, png, etc) using the custom controller.


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

## Who's using Landscapist?
If your project uses Landscapist, let me know via creating a new issue! 🤗

## [Twitter for Android](https://user-images.githubusercontent.com/24237865/125583736-f0ffa76f-8f87-433b-a9fd-192231dc5e63.jpg)

[![twitter](https://user-images.githubusercontent.com/24237865/125583182-9527dd48-433e-4e17-ae52-3f2bb544a847.jpg)](https://play.google.com/store/apps/details?id=com.twitter.android&hl=ko&gl=US)

## Reference repository
This library is mostly inspired by [Accompanist](https://github.com/chrisbanes/accompanist). <br>

Accompanist is a group of libraries that contains some utilities which I've found myself copying around projects which use Jetpack Compose. Currently, it contains image loading and insets. You can get more variety and recent systems from the library maintained by Google.

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
