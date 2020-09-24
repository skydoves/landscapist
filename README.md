
<h1 align="center">Frescompose</h1></br>
<p align="center">  
🍂 Jetpack Compose image loading library for requesting and displaying images using <a href="https://github.com/facebook/fresco" target="_blank"> Fresco</a>. <br><br>
Fresco takes care of image loading and display, so you don't have to. It will load images from the network, local storage, or local resources, and display a placeholder until the image has arrived.
</p>
</br>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://android-arsenal.com/api?level=21"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://github.com/skydoves"><img alt="Profile" src="https://skydoves.github.io/badges/skydoves.svg"/></a> 
</p>

## Including in your project
### Gradle 
Add below codes to your **root** `build.gradle` file (not your module build.gradle file).
```gradle
allprojects {
    repositories {
        jcenter()
    }
}
```
And add a dependency code to your **module**'s `build.gradle` file.
```gradle
dependencies {
    implementation "com.github.skydoves:frescompose:1.0.0"
}
```

## Initialize
We should initialize `Fresco` using [ImagePipelineConfig](https://frescolib.org/docs/configure-image-pipeline.html) in our `Application` class.<br>
And we will fetch images from the network, recommend using `OkHttpImagePipelineConfigFactory`.<br>
We can set caching, networking, and thread pool strategies. And we can reference related methods in [here](https://fresco.buzhidao.net/javadoc/reference/com/facebook/imagepipeline/core/ImagePipelineConfig.Builder.html).
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

## Usage
We can simply request and load images using a `FrescoImage` composable function.
```kotlin
FrescoImage(
  imageUrl = stringImageUrl,
  contentScale = ContentScale.Crop, // Crop, Fit, Inside, FillHeight, FillWidth, None
  placeHolder = imageResource(R.drawable.placeholder), // shows a placeholder imageAssert when loading.
  error = imageResource(R.drawable.error) // shows an error imageAssert when the request failed.
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
### Composable loading, success, failure
We can create our own composable functions following requesting states.<br>
Here is an example that shows a progress indicator when loading an image,<br>
After complete requesting, the indicator will be gone and a content image will be shown.<br>
And if the request failed (e.g. network error, wrong destination), error text will be shown.
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
    frescoImageState.imageAsset?.let {
      Image(
        asset = it,
        modifier = Modifier.width(100.dp))
    }
  },
  loading = { // do something }
)
```

## Find this repository useful? :heart:
Support it by joining __[stargazers](https://github.com/skydoves/Frescompose/stargazers)__ for this repository. :star: <br>
And __[follow](https://github.com/skydoves)__ me for my next creations! 🤩

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
