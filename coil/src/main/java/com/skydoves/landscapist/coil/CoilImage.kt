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
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.skydoves.landscapist.CircularReveal
import com.skydoves.landscapist.CircularRevealedImage
import com.skydoves.landscapist.ImageBySource
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.Shimmer
import com.skydoves.landscapist.ShimmerParams
import com.skydoves.landscapist.palette.BitmapPalette
import com.skydoves.landscapist.rememberDrawablePainter
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param shimmerParams The shimmer related parameter used to determine constructions of the [Shimmer].
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param error An [ImageBitmap], [ImageVector], or [Painter] for showing instead of the target image when images are failed to load.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 */
@Composable
public fun CoilImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  alpha: Float = DefaultAlpha,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  circularReveal: CircularReveal? = null,
  colorFilter: ColorFilter? = null,
  shimmerParams: ShimmerParams,
  bitmapPalette: BitmapPalette? = null,
  error: Any? = null,
  @DrawableRes previewPlaceholder: Int = 0,
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
    circularReveal = circularReveal,
    shimmerParams = shimmerParams,
    bitmapPalette = bitmapPalette,
    previewPlaceholder = previewPlaceholder,
    failure = {
      error?.let {
        ImageBySource(
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param placeHolder An [ImageBitmap], [ImageVector], or [Painter] to be displayed when the request is in progress.
 * @param error An [ImageBitmap], [ImageVector], or [Painter] for showing instead of the target image when images are failed to load.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 */
@Composable
public fun CoilImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  alpha: Float = DefaultAlpha,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  circularReveal: CircularReveal? = null,
  bitmapPalette: BitmapPalette? = null,
  colorFilter: ColorFilter? = null,
  placeHolder: Any? = null,
  error: Any? = null,
  @DrawableRes previewPlaceholder: Int = 0,
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
    circularReveal = circularReveal,
    bitmapPalette = bitmapPalette,
    previewPlaceholder = previewPlaceholder,
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun CoilImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  shimmerParams: ShimmerParams,
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
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
    circularReveal = circularReveal,
    shimmerParams = shimmerParams,
    bitmapPalette = bitmapPalette,
    previewPlaceholder = previewPlaceholder,
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun CoilImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  context: Context = LocalContext.current,
  lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
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
    circularReveal = circularReveal,
    bitmapPalette = bitmapPalette,
    previewPlaceholder = previewPlaceholder,
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun CoilImage(
  imageRequest: ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  shimmerParams: ShimmerParams,
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
  success: @Composable ((imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  if (LocalInspectionMode.current && previewPlaceholder != 0) {
    Image(
      modifier = modifier.fillMaxWidth(),
      painter = painterResource(id = previewPlaceholder),
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter,
      contentDescription = contentDescription
    )
    return
  }

  CoilImage(
    recomposeKey = imageRequest,
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
    bitmapPalette = bitmapPalette,
  ) ImageRequest@{ imageState ->
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
        if (success != null) {
          success.invoke(coilImageState)
        } else {
          val drawable = coilImageState.drawable ?: return@ImageRequest
          CircularRevealedImage(
            modifier = modifier,
            bitmap = drawable.toBitmap().asImageBitmap(),
            bitmapPainter = rememberDrawablePainter(drawable = drawable),
            alignment = alignment,
            contentScale = contentScale,
            contentDescription = contentDescription,
            alpha = alpha,
            colorFilter = colorFilter,
            circularReveal = circularReveal
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun CoilImage(
  imageRequest: ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
  loading: @Composable ((imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  if (LocalInspectionMode.current && previewPlaceholder != 0) {
    Image(
      modifier = modifier.fillMaxWidth(),
      painter = painterResource(id = previewPlaceholder),
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter,
      contentDescription = contentDescription
    )
    return
  }

  CoilImage(
    recomposeKey = imageRequest,
    imageLoader = imageLoader,
    modifier = modifier.fillMaxWidth(),
    bitmapPalette = bitmapPalette,
  ) ImageRequest@{ imageState ->
    when (val coilImageState = imageState.toCoilImageState()) {
      is CoilImageState.None -> Unit
      is CoilImageState.Loading -> loading?.invoke(coilImageState)
      is CoilImageState.Failure -> failure?.invoke(coilImageState)
      is CoilImageState.Success -> {
        if (success != null) {
          success.invoke(coilImageState)
        } else {
          val drawable = coilImageState.drawable ?: return@ImageRequest
          CircularRevealedImage(
            modifier = modifier,
            bitmap = drawable.toBitmap().asImageBitmap(),
            bitmapPainter = rememberDrawablePainter(drawable = drawable),
            alignment = alignment,
            contentScale = contentScale,
            contentDescription = contentDescription,
            alpha = alpha,
            colorFilter = colorFilter,
            circularReveal = circularReveal
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
 * @param recomposeKey The request to execute.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param content Content to be displayed for the given state.
 */
@Composable
private fun CoilImage(
  recomposeKey: ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader = LocalCoilProvider.getCoilImageLoader(),
  bitmapPalette: BitmapPalette? = null,
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  val context = LocalContext.current
  val imageLoadStateFlow =
    remember(recomposeKey) { MutableStateFlow<ImageLoadState>(ImageLoadState.None) }
  val disposable = remember(recomposeKey) { mutableStateOf<Disposable?>(null) }

  ImageLoad(
    recomposeKey = recomposeKey,
    executeImageRequest = {
      suspendCancellableCoroutine { cont ->
        disposable.value = imageLoader.enqueue(
          recomposeKey.newBuilder(context).target(
            onStart = {
              imageLoadStateFlow.value = ImageLoadState.Loading(0f)
            },
            onSuccess = {
              imageLoadStateFlow.value = ImageLoadState.Success(it)
              bitmapPalette?.applyImageModel(recomposeKey.data)
                ?.generate(it.toBitmap().copy(Bitmap.Config.ARGB_8888, true))
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
