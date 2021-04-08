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

@file:JvmMultifileClass
@file:JvmName("GlideImage")
@file:Suppress("unused")

package com.skydoves.landscapist.glide

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.CircularRevealedImage
import com.skydoves.landscapist.DefaultCircularRevealedDuration
import com.skydoves.landscapist.ImageBySource
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.Shimmer
import com.skydoves.landscapist.ShimmerParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Requests loading an image with a loading placeholder and error image resource ([ImageBitmap], [ImageVector], [Painter]).
 *
 * ```
 * GlideImage(
 *   imageModel = imageUrl,
 *   requestBuilder = Glide
 *     .with(LocalContext.current)
 *     .asBitmap()
 *     .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *     .thumbnail(0.6f)
 *     .transition(withCrossFade()),
 *   placeHolder = ImageBitmap.imageResource(R.drawable.placeholder),
 *   error = ImageBitmap.imageResource(R.drawable.error)
 * )
 * ```
 *
 * @param imageModel The data model to request image. See [RequestBuilder.load] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param requestBuilder Most options in Glide can be applied directly on the RequestBuilder object returned by Glide.with().
 * @param requestOptions Provides type independent options to customize loads with Glide.
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param placeHolder An [ImageBitmap], [ImageVector], or [Painter] to be displayed when the request is in progress.
 * @param error An [ImageBitmap], [ImageVector], or [Painter] for showing instead of the target image when images are failed to load.
 */
@Composable
fun GlideImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  requestBuilder: RequestBuilder<Bitmap> = LocalGlideProvider.getGlideRequestBuilder(imageModel),
  requestOptions: RequestOptions = LocalGlideProvider.getGlideRequestOptions(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  placeHolder: Any? = null,
  error: Any? = null
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = requestBuilder,
    requestOptions = requestOptions,
    modifier = modifier.fillMaxWidth(),
    alignment = alignment,
    contentScale = contentScale,
    contentDescription = contentDescription,
    colorFilter = colorFilter,
    alpha = alpha,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    loading = {
      placeHolder?.let {
        ImageBySource(
          source = it,
          modifier = modifier,
          alignment = alignment,
          contentDescription = contentDescription,
          contentScale = contentScale,
          colorFilter = colorFilter,
          alpha = alpha
        )
      }
    },
    failure = {
      error?.let {
        ImageBySource(
          source = it,
          modifier = modifier,
          alignment = alignment,
          contentDescription = contentDescription,
          contentScale = contentScale,
          colorFilter = colorFilter,
          alpha = alpha
        )
      }
    }
  )
}

/**
 * Requests loading an image with a loading placeholder and error image resource ([ImageBitmap], [ImageVector], [Painter]).
 *
 * ```
 * GlideImage(
 *   imageModel = imageUrl,
 *   requestBuilder = Glide
 *     .with(LocalContext.current)
 *     .asBitmap()
 *     .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *     .thumbnail(0.6f)
 *     .transition(withCrossFade()),
 *   circularRevealedEnabled = true,
 *   shimmerParams = ShimmerParams (
 *      baseColor = backgroundColor,
 *      highlightColor = highlightColor
 *   ),
 *   error = ImageVector.vectorResource(R.drawable.error)
 * )
 * ```
 *
 * @param imageModel The data model to request image. See [RequestBuilder.load] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param requestBuilder Most options in Glide can be applied directly on the RequestBuilder object returned by Glide.with().
 * @param requestOptions Provides type independent options to customize loads with Glide.
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param shimmerParams The shimmer related parameter used to determine constructions of the [Shimmer].
 * @param error An [ImageBitmap], [ImageVector], or [Painter] for showing instead of the target image when images are failed to load.
 */
@Composable
fun GlideImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  requestBuilder: RequestBuilder<Bitmap> = LocalGlideProvider.getGlideRequestBuilder(imageModel),
  requestOptions: RequestOptions = LocalGlideProvider.getGlideRequestOptions(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  error: Any? = null
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = requestBuilder,
    requestOptions = requestOptions,
    modifier = modifier.fillMaxWidth(),
    alignment = alignment,
    contentScale = contentScale,
    contentDescription = contentDescription,
    colorFilter = colorFilter,
    alpha = alpha,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    shimmerParams = shimmerParams,
    failure = {
      error?.let {
        ImageBySource(
          source = it,
          modifier = modifier,
          alignment = alignment,
          contentDescription = contentDescription,
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
 * requestBuilder = Glide
 *   .with(LocalContext.current)
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
 *
 * @param imageModel The data model to request image. See [RequestBuilder.load] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param requestBuilder Most options in Glide can be applied directly on the RequestBuilder object returned by Glide.with().
 * @param requestOptions Provides type independent options to customize loads with Glide.
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param shimmerParams The shimmer related parameter used to determine constructions of the [Shimmer].
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
fun GlideImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  requestBuilder: RequestBuilder<Bitmap> = LocalGlideProvider.getGlideRequestBuilder(imageModel),
  requestOptions: RequestOptions = LocalGlideProvider.getGlideRequestOptions(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  success: @Composable ((imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  GlideImage(
    builder = requestBuilder
      .apply(requestOptions)
      .load(imageModel),
    modifier = modifier.fillMaxWidth(),
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
        success?.invoke(glideImageState) ?: glideImageState.imageBitmap?.let {
          CircularRevealedImage(
            modifier = modifier,
            bitmap = it,
            alignment = alignment,
            contentScale = contentScale,
            contentDescription = contentDescription,
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
 *   .with(LocalContext.current)
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
 *
 * @param imageModel The data model to request image. See [RequestBuilder.load] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param requestBuilder Most options in Glide can be applied directly on the RequestBuilder object returned by Glide.with().
 * @param requestOptions Provides type independent options to customize loads with Glide.
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
fun GlideImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  requestBuilder: RequestBuilder<Bitmap> = LocalGlideProvider.getGlideRequestBuilder(imageModel),
  requestOptions: RequestOptions = LocalGlideProvider.getGlideRequestOptions(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  loading: @Composable ((imageState: GlideImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  GlideImage(
    builder = requestBuilder
      .apply(requestOptions)
      .load(imageModel),
    modifier = modifier.fillMaxWidth(),
  ) { imageState ->
    when (val glideImageState = imageState.toGlideImageState()) {
      is GlideImageState.None -> Unit
      is GlideImageState.Loading -> loading?.invoke(glideImageState)
      is GlideImageState.Failure -> failure?.invoke(glideImageState)
      is GlideImageState.Success -> {
        success?.invoke(glideImageState) ?: glideImageState.imageBitmap?.let {
          CircularRevealedImage(
            modifier = modifier,
            bitmap = it,
            alignment = alignment,
            contentScale = contentScale,
            contentDescription = contentDescription,
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
 *   .with(LocalContext.current)
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
 *
 * @param builder The request to execute.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param content Content to be displayed for the given state.
 */
@Composable
@OptIn(ExperimentalCoroutinesApi::class)
private fun GlideImage(
  builder: RequestBuilder<Bitmap>,
  modifier: Modifier = Modifier,
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  val context = LocalContext.current
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
    modifier = modifier.fillMaxWidth(),
    content = content
  )
}
