/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("unused")
@file:JvmName("GlideImage")
@file:JvmMultifileClass

package com.skydoves.landscapist.glide

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ContextAmbient
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.CircularRevealedImage
import com.skydoves.landscapist.DefaultCircularRevealedDuration
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.Shimmer
import com.skydoves.landscapist.ShimmerParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Requests loading an image with a loading placeholder and error imageAsset.
 *
 * ```
 * GlideImage(
 *   imageModel = imageUrl,
 *   requestBuilder = Glide
 *     .with(ContextAmbient.current)
 *     .asBitmap()
 *     .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *     .thumbnail(0.6f)
 *     .transition(withCrossFade()),
 *   circularRevealedEnabled = true,
 *   shimmerParams = ShimmerParams (
 *      baseColor = backgroundColor,
 *      highlightColor = highlightColor
 *   ),
 *   error = imageResource(R.drawable.error)
 * )
 * ```
 */
@Composable
fun GlideImage(
  imageModel: Any,
  requestBuilder: RequestBuilder<Bitmap>,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  error: ImageAsset? = null
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = requestBuilder,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    colorFilter = colorFilter,
    alpha = alpha,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    shimmerParams = shimmerParams,
    failure = {
      error?.let {
        Image(
          asset = it,
          alignment = alignment,
          contentScale = contentScale,
          colorFilter = colorFilter,
          alpha = alpha
        )
      }
    }
  )
}

/**
 * Requests loading an image with a loading placeholder and error imageAsset.
 *
 * ```
 * GlideImage(
 *   imageModel = imageUrl,
 *   requestBuilder = Glide
 *     .with(ContextAmbient.current)
 *     .asBitmap()
 *     .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *     .thumbnail(0.6f)
 *     .transition(withCrossFade()),
 *   placeHolder = imageResource(R.drawable.placeholder),
 *   error = imageResource(R.drawable.error)
 * )
 * ```
 */
@Composable
fun GlideImage(
  imageModel: Any,
  requestBuilder: RequestBuilder<Bitmap>,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  placeHolder: ImageAsset? = null,
  error: ImageAsset? = null
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = requestBuilder,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    colorFilter = colorFilter,
    alpha = alpha,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    loading = {
      placeHolder?.let {
        Image(
          asset = it,
          alignment = alignment,
          contentScale = contentScale,
          colorFilter = colorFilter,
          alpha = alpha
        )
      }
    },
    failure = {
      error?.let {
        Image(
          asset = it,
          alignment = alignment,
          contentScale = contentScale,
          colorFilter = colorFilter,
          alpha = alpha
        )
      }
    }
  )
}

/**
 * Requests loading an image and create some composables based on [GlideImageState].
 *
 * ```
 * GlideImage(
 * imageModel = imageUrl,
 * modifier = modifier,
 * shimmerParams = ShimmerParams (
 *  baseColor = backgroundColor,
 *  highlightColor = highlightColor
 * ),
 * failure = {
 *   Text(text = "image request failed.")
 * })
 * ```
 */
@Composable
fun GlideImage(
  imageModel: Any,
  requestOptions: RequestOptions = GlideAmbientProvider.getGlideRequestOptions(),
  transitionOptions: BitmapTransitionOptions = withCrossFade(),
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  success: @Composable ((imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = Glide
      .with(ContextAmbient.current)
      .asBitmap()
      .apply(requestOptions)
      .transition(transitionOptions),
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    shimmerParams = shimmerParams,
    success = success,
    failure = failure
  )
}

/**
 * Requests loading an image and create some composables based on [GlideImageState].
 *
 * ```
 * GlideImage(
 * imageModel = imageUrl,
 * modifier = modifier,
 * loading = {
 *   ConstraintLayout(
 *     modifier = Modifier.fillMaxSize()
 *   ) {
 *     val indicator = createRef()
 *     CircularProgressIndicator(
 *       modifier = Modifier.constrainAs(indicator) {
 *         top.linkTo(parent.top)
 *         bottom.linkTo(parent.bottom)
 *        start.linkTo(parent.start)
 *        end.linkTo(parent.end)
 *       }
 *     )
 *   }
 * },
 * failure = {
 *   Text(text = "image request failed.")
 * })
 * ```
 */
@Composable
fun GlideImage(
  imageModel: Any,
  requestOptions: RequestOptions = GlideAmbientProvider.getGlideRequestOptions(),
  transitionOptions: BitmapTransitionOptions = withCrossFade(),
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  loading: @Composable ((imageState: GlideImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = Glide
      .with(ContextAmbient.current)
      .asBitmap()
      .apply(requestOptions)
      .transition(transitionOptions),
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    loading = loading,
    success = success,
    failure = failure
  )
}

/**
 * Requests loading an image and create some composables based on [GlideImageState].
 *
 * ```
 * GlideImage(
 * imageModel = imageUrl,
 * requestBuilder = Glide
 *   .with(ContextAmbient.current)
 *   .asBitmap()
 *   .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *   .thumbnail(0.6f)
 *   .transition(withCrossFade()),
 * modifier = modifier,
 * shimmerParams = ShimmerParams (
 *  baseColor = backgroundColor,
 *  highlightColor = highlightColor
 * ),
 * failure = {
 *   Text(text = "image request failed.")
 * })
 * ```
 */
@Composable
fun GlideImage(
  imageModel: Any,
  requestBuilder: RequestBuilder<Bitmap>,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  success: @Composable ((imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  GlideImage(
    builder = requestBuilder.load(imageModel),
    modifier = modifier,
  ) { imageState ->
    when (val glideImageState = imageState.toGlideImageState()) {
      is GlideImageState.None -> Unit
      is GlideImageState.Loading -> {
        Shimmer(
          baseColor = shimmerParams.baseColor,
          highlightColor = shimmerParams.highlightColor,
          intensity = shimmerParams.intensity,
          dropOff = shimmerParams.dropOff,
          tilt = shimmerParams.tilt,
          durationMillis = shimmerParams.durationMillis
        )
      }
      is GlideImageState.Failure -> failure?.invoke(glideImageState)
      is GlideImageState.Success -> {
        success?.invoke(glideImageState) ?: glideImageState.imageAsset?.let {
          CircularRevealedImage(
            asset = it,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            circularRevealedEnabled = circularRevealedEnabled,
            circularRevealedDuration = circularRevealedDuration
          )
        }
      }
    }
  }
}

/**
 * Requests loading an image and create some composables based on [GlideImageState].
 *
 * ```
 * GlideImage(
 * imageModel = imageUrl,
 * requestBuilder = Glide
 *   .with(ContextAmbient.current)
 *   .asBitmap()
 *   .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *   .thumbnail(0.6f)
 *   .transition(withCrossFade()),
 * modifier = modifier,
 * loading = {
 *   ConstraintLayout(
 *     modifier = Modifier.fillMaxSize()
 *   ) {
 *     val indicator = createRef()
 *     CircularProgressIndicator(
 *       modifier = Modifier.constrainAs(indicator) {
 *         top.linkTo(parent.top)
 *         bottom.linkTo(parent.bottom)
 *        start.linkTo(parent.start)
 *        end.linkTo(parent.end)
 *       }
 *     )
 *   }
 * },
 * failure = {
 *   Text(text = "image request failed.")
 * })
 * ```
 */
@Composable
fun GlideImage(
  imageModel: Any,
  requestBuilder: RequestBuilder<Bitmap>,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  loading: @Composable ((imageState: GlideImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  GlideImage(
    builder = requestBuilder.load(imageModel),
    modifier = modifier,
  ) { imageState ->
    when (val glideImageState = imageState.toGlideImageState()) {
      is GlideImageState.None -> Unit
      is GlideImageState.Loading -> loading?.invoke(glideImageState)
      is GlideImageState.Failure -> failure?.invoke(glideImageState)
      is GlideImageState.Success -> {
        success?.invoke(glideImageState) ?: glideImageState.imageAsset?.let {
          CircularRevealedImage(
            asset = it,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            circularRevealedEnabled = circularRevealedEnabled,
            circularRevealedDuration = circularRevealedDuration
          )
        }
      }
    }
  }
}

/**
 * Requests loading an image and create a composable that provides
 * the current state [ImageLoadState] of the content.
 *
 * ```
 * GlideImage(
 * requestBuilder = Glide
 *   .with(ContextAmbient.current)
 *   .asBitmap()
 *   .load(poster.poster)
 *   .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *   .thumbnail(0.6f)
 *   .transition(withCrossFade()),
 * modifier = modifier,
 * ) { imageState ->
 *   when (val glideImageState = imageState.toGlideImageState()) {
 *     is GlideImageState.None -> // do something
 *     is GlideImageState.Loading -> // do something
 *     is GlideImageState.Failure -> // do something
 *     is GlideImageState.Success ->  // do something
 *   }
 * }
 * ```
 */
@Composable
@OptIn(ExperimentalCoroutinesApi::class)
private fun GlideImage(
  builder: RequestBuilder<Bitmap>,
  modifier: Modifier = Modifier.fillMaxWidth(),
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  val context = ContextAmbient.current
  val target = remember { FlowCustomTarget() }

  ImageLoad(
    imageRequest = builder,
    executeImageRequest = {
      suspendCancellableCoroutine { cont ->
        builder.into(target)
        builder.submit()

        cont.resume(target.imageLoadStateFlow) {
          // clear the glide target request if cancelled.
          Glide.with(context).clear(target)
        }
      }
    },
    modifier = modifier,
    content = content
  )
}
