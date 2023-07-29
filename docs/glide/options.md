Landscapist provides multiple ways to customize the request and transition options.

## Custom RequestOptions and TransitionOptions

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