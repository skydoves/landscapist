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
@file:JvmName("CoilImage")
@file:JvmMultifileClass

package com.skydoves.landscapist.coil

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleOwner
import coil.ImageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.skydoves.landscapist.CircularRevealedImage
import com.skydoves.landscapist.DefaultCircularRevealedDuration
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageWithSource
import com.skydoves.landscapist.Shimmer
import com.skydoves.landscapist.ShimmerParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Requests loading an image with a loading placeholder and error image resource ([ImageBitmap], [ImageVector], [Painter]).
 *
 * ```
 * CoilImage(
 *   imageModel = imageModel,
 * shimmerParams = ShimmerParams (
 *  baseColor = backgroundColor,
 *  highlightColor = highlightColor
 * ),
 *  error = ImageBitmap.imageResource(R.drawable.error)
 * )
 * ```
 *
 * or we can use [ImageVector] or custom [Painter] like the below.
 *
 * ```
 * error = ImageVector.vectorResource(R.drawable.error)
 * ```
 *
 * @param imageModel The data model to request image. See [ImageRequest.Builder.data] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param context The context for creating the [ImageRequest.Builder].
 * @param lifecycleOwner The [LifecycleOwner] for constructing the [ImageRequest.Builder].
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * Defaults to [LocalCoilProvider.getCoilImageLoader].
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
fun CoilImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  alpha: Float = DefaultAlpha,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  colorFilter: ColorFilter? = null,
  shimmerParams: ShimmerParams,
  error: Any? = null,
) {
  CoilImage(
    imageModel = imageModel,
    context = context,
    lifecycleOwner = lifecycleOwner,
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    shimmerParams = shimmerParams,
    failure = {
      error?.let {
        ImageWithSource(
          source = it,
          modifier = modifier,
          alignment,
          contentScale,
          contentDescription,
          colorFilter,
          alpha
        )
      }
    }
  )
}

/**
 * Requests loading an image with a loading placeholder and error image resource ([ImageBitmap], [ImageVector], [Painter]).
 *
 * ```
 * CoilImage(
 *   imageModel = imageModel,
 *   placeHolder = ImageBitmap.imageResource(R.drawable.placeholder),
 *   error = ImageBitmap.imageResource(R.drawable.error)
 * )
 * ```
 *
 * or we can use [ImageVector] or custom [Painter] like the below.
 *
 * ```
 * placeHolder = ImageVector.vectorResource(R.drawable.placeholder)
 * error = ImageVector.vectorResource(R.drawable.error)
 * ```
 *
 * @param imageModel The data model to request image. See [ImageRequest.Builder.data] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param context The context for creating the [ImageRequest.Builder].
 * @param lifecycleOwner The [LifecycleOwner] for constructing the [ImageRequest.Builder].
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * Defaults to [LocalCoilProvider.getCoilImageLoader].
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
fun CoilImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  alpha: Float = DefaultAlpha,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  colorFilter: ColorFilter? = null,
  placeHolder: Any? = null,
  error: Any? = null,
) {
  CoilImage(
    imageModel = imageModel,
    context = context,
    lifecycleOwner = lifecycleOwner,
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    loading = {
      placeHolder?.let {
        ImageWithSource(
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
        ImageWithSource(
          source = it,
          modifier = modifier,
          alignment = alignment,
          contentDescription = contentDescription,
          contentScale = contentScale,
          colorFilter = colorFilter,
          alpha = alpha,
        )
      }
    }
  )
}

/**
 * Requests loading an image and create some composables based on [CoilImageState].
 *
 * ```
 * CoilImage(
 * imageModel = imageModel,
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
 * @param imageModel The data model to request image. See [ImageRequest.Builder.data] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param context The context for creating the [ImageRequest.Builder].
 * @param lifecycleOwner The [LifecycleOwner] for constructing the [ImageRequest.Builder].
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * Defaults to [LocalCoilProvider.getCoilImageLoader].
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
fun CoilImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  success: @Composable ((imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  CoilImage(
    imageRequest = ImageRequest.Builder(context)
      .data(imageModel)
      .lifecycle(lifecycleOwner)
      .build(),
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
    alignment = alignment,
    contentScale = contentScale,
    contentDescription = contentDescription,
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
 * Requests loading an image and create some composables based on [CoilImageState].
 *
 * ```
 * CoilImage(
 * imageModel = imageModel,
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
 * @param imageModel The data model to request image. See [ImageRequest.Builder.data] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param context The context for creating the [ImageRequest.Builder].
 * @param lifecycleOwner The [LifecycleOwner] for constructing the [ImageRequest.Builder].
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * Defaults to [LocalCoilProvider.getCoilImageLoader].
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
fun CoilImage(
  imageModel: Any,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  loading: @Composable ((imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  CoilImage(
    imageRequest = ImageRequest.Builder(context)
      .data(imageModel)
      .lifecycle(lifecycleOwner)
      .build(),
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
    alignment = alignment,
    contentScale = contentScale,
    contentDescription = contentDescription,
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
 * Requests loading an image and create some composables based on [CoilImageState].
 *
 * ```
 * CoilImage(
 * imageRequest = ImageRequest.Builder(context)
 *      .data(imageModel)
 *      .lifecycle(lifecycleOwner)
 *      .build(),
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
 * @param imageRequest The request to execute.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * Defaults to [LocalCoilProvider.getCoilImageLoader].
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
fun CoilImage(
  imageRequest: ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  success: @Composable ((imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  CoilImage(
    request = imageRequest,
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
  ) { imageState ->
    when (val coilImageState = imageState.toCoilImageState()) {
      is CoilImageState.None -> Unit
      is CoilImageState.Loading -> {
        Shimmer(
          baseColor = shimmerParams.baseColor,
          highlightColor = shimmerParams.highlightColor,
          intensity = shimmerParams.intensity,
          dropOff = shimmerParams.dropOff,
          tilt = shimmerParams.tilt,
          durationMillis = shimmerParams.durationMillis
        )
      }
      is CoilImageState.Failure -> failure?.invoke(coilImageState)
      is CoilImageState.Success -> {
        success?.invoke(coilImageState) ?: coilImageState.imageBitmap?.let {
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
 * Requests loading an image and create some composables based on [CoilImageState].
 *
 * ```
 * CoilImage(
 * imageRequest = ImageRequest.Builder(context)
 *      .data(imageModel)
 *      .lifecycle(lifecycleOwner)
 *      .build(),
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
 * @param imageRequest The request to execute.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * Defaults to [LocalCoilProvider.getCoilImageLoader].
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
fun CoilImage(
  imageRequest: ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  loading: @Composable ((imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  CoilImage(
    request = imageRequest,
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
  ) { imageState ->
    when (val coilImageState = imageState.toCoilImageState()) {
      is CoilImageState.None -> Unit
      is CoilImageState.Loading -> loading?.invoke(coilImageState)
      is CoilImageState.Failure -> failure?.invoke(coilImageState)
      is CoilImageState.Success -> {
        success?.invoke(coilImageState) ?: coilImageState.imageBitmap?.let {
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
 * CoilImage(
 * imageRequest = ImageRequest.Builder(context)
 *      .data(imageModel)
 *      .lifecycle(lifecycleOwner)
 *      .build(),
 * modifier = modifier,
 * ) { imageState ->
 *   when (val coilImageState = imageState.toCoilImageState()) {
 *     is CoilImageState.None -> // do something
 *     is CoilImageState.Loading -> // do something
 *     is CoilImageState.Failure -> // do something
 *     is CoilImageState.Success ->  // do something
 *   }
 * }
 * ```
 *
 * @param request The request to execute.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * @param content Content to be displayed for the given state.
 */
@Composable
@OptIn(ExperimentalCoroutinesApi::class)
fun CoilImage(
  request: ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  val context = LocalContext.current
  val imageLoadStateFlow = remember { MutableStateFlow<ImageLoadState>(ImageLoadState.Loading(0f)) }
  val disposable = remember { mutableStateOf<Disposable?>(null) }

  ImageLoad(
    imageRequest = request,
    executeImageRequest = {
      suspendCancellableCoroutine { cont ->
        disposable.value = imageLoader.enqueue(
          request.newBuilder(context).target(
            onSuccess = {
              imageLoadStateFlow.value = ImageLoadState.Success(it.toBitmap().asImageBitmap())
            },
            onError = {
              imageLoadStateFlow.value = ImageLoadState.Failure(it?.toBitmap()?.asImageBitmap())
            }
          ).build()
        )

        cont.resume(imageLoadStateFlow) {
          // dispose the coil disposable request if cancelled.
          disposable.value?.dispose()
        }
      }
    },
    modifier = modifier,
    content = content
  )
}
