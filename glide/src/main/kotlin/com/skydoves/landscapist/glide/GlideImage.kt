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

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.CircularReveal
import com.skydoves.landscapist.CircularRevealImage
import com.skydoves.landscapist.ImageBySource
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.Shimmer
import com.skydoves.landscapist.ShimmerParams
import com.skydoves.landscapist.palette.BitmapPalette
import com.skydoves.landscapist.rememberDrawablePainter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param placeHolder An [ImageBitmap], [ImageVector], or [Painter] to be displayed when the request is in progress.
 * @param error An [ImageBitmap], [ImageVector], or [Painter] for showing instead of the target image when images are failed to load.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 */
@Composable
public fun GlideImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  requestBuilder: @Composable () -> RequestBuilder<Drawable> = {
    LocalGlideProvider.getGlideRequestBuilder(imageModel)
  },
  requestOptions: @Composable () -> RequestOptions = {
    LocalGlideProvider.getGlideRequestOptions()
  },
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  bitmapPalette: BitmapPalette? = null,
  placeHolder: Any? = null,
  error: Any? = null,
  @DrawableRes previewPlaceholder: Int = 0,
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = requestBuilder,
    requestOptions = requestOptions,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    contentDescription = contentDescription,
    colorFilter = colorFilter,
    alpha = alpha,
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
 *   circularRevealEnabled = true,
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param shimmerParams The shimmer related parameter used to determine constructions of the [Shimmer].
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param error An [ImageBitmap], [ImageVector], or [Painter] for showing instead of the target image when images are failed to load.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 */
@Composable
public fun GlideImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  requestBuilder: @Composable () -> RequestBuilder<Drawable> = {
    LocalGlideProvider.getGlideRequestBuilder(imageModel)
  },
  requestOptions: @Composable () -> RequestOptions = {
    LocalGlideProvider.getGlideRequestOptions()
  },
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  bitmapPalette: BitmapPalette? = null,
  shimmerParams: ShimmerParams,
  error: Any? = null,
  @DrawableRes previewPlaceholder: Int = 0,
) {
  GlideImage(
    imageModel = imageModel,
    requestBuilder = requestBuilder,
    requestOptions = requestOptions,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    contentDescription = contentDescription,
    colorFilter = colorFilter,
    alpha = alpha,
    circularReveal = circularReveal,
    shimmerParams = shimmerParams,
    bitmapPalette = bitmapPalette,
    previewPlaceholder = previewPlaceholder,
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param shimmerParams The shimmer related parameter used to determine constructions of the [Shimmer].
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun GlideImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  requestBuilder: @Composable () -> RequestBuilder<Drawable> = {
    LocalGlideProvider.getGlideRequestBuilder(imageModel)
  },
  requestOptions: @Composable () -> RequestOptions = {
    LocalGlideProvider.getGlideRequestOptions()
  },
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  shimmerParams: ShimmerParams,
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
  success: @Composable (BoxScope.(imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable (BoxScope.(imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  if (LocalInspectionMode.current && previewPlaceholder != 0) {
    Image(
      modifier = modifier,
      painter = painterResource(id = previewPlaceholder),
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter,
      contentDescription = contentDescription
    )
    return
  }

  GlideImage(
    recomposeKey = imageModel,
    builder = requestBuilder.invoke()
      .apply(requestOptions.invoke())
      .load(imageModel),
    bitmapPalette = bitmapPalette,
    modifier = modifier,
  ) ImageRequest@{ imageState ->
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
      is GlideImageState.Failure -> failure?.invoke(this, glideImageState)
      is GlideImageState.Success -> {
        if (success != null) {
          success.invoke(this, glideImageState)
        } else {
          val drawable = glideImageState.drawable ?: return@ImageRequest
          CircularRevealImage(
            modifier = Modifier.fillMaxSize(),
            bitmap = drawable.toBitmap().asImageBitmap(),
            bitmapPainter = rememberDrawablePainter(drawable),
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
 * @param circularReveal circular reveal parameters for running reveal animation when images are successfully loaded.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun GlideImage(
  imageModel: Any?,
  modifier: Modifier = Modifier,
  requestBuilder: @Composable () -> RequestBuilder<Drawable> = {
    LocalGlideProvider.getGlideRequestBuilder(imageModel)
  },
  requestOptions: @Composable () -> RequestOptions = {
    LocalGlideProvider.getGlideRequestOptions()
  },
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularReveal: CircularReveal? = null,
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
  loading: @Composable (BoxScope.(imageState: GlideImageState.Loading) -> Unit)? = null,
  success: @Composable (BoxScope.(imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable (BoxScope.(imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  if (LocalInspectionMode.current && previewPlaceholder != 0) {
    Image(
      modifier = modifier,
      painter = painterResource(id = previewPlaceholder),
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter,
      contentDescription = contentDescription
    )
    return
  }

  GlideImage(
    recomposeKey = imageModel,
    builder = requestBuilder.invoke()
      .apply(requestOptions.invoke())
      .load(imageModel),
    modifier = modifier,
    bitmapPalette = bitmapPalette
  ) ImageRequest@{ imageState ->
    when (val glideImageState = imageState.toGlideImageState()) {
      is GlideImageState.None -> Unit
      is GlideImageState.Loading -> loading?.invoke(this, glideImageState)
      is GlideImageState.Failure -> failure?.invoke(this, glideImageState)
      is GlideImageState.Success -> {
        if (success != null) {
          success.invoke(this, glideImageState)
        } else {
          val drawable = glideImageState.drawable ?: return@ImageRequest
          CircularRevealImage(
            modifier = Modifier.fillMaxSize(),
            bitmap = drawable.toBitmap().asImageBitmap(),
            bitmapPainter = rememberDrawablePainter(drawable),
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
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param content Content to be displayed for the given state.
 */
@Composable
private fun GlideImage(
  recomposeKey: Any?,
  builder: RequestBuilder<Drawable>,
  modifier: Modifier = Modifier,
  bitmapPalette: BitmapPalette? = null,
  content: @Composable BoxScope.(imageState: ImageLoadState) -> Unit
) {
  val requestManager = LocalGlideProvider.getGlideRequestManager()

  ImageLoad(
    recomposeKey = recomposeKey,
    executeImageRequest = {
      callbackFlow {
        val target = FlowCustomTarget(this)
        val requestListener =
          FlowRequestListener(
            this, bitmapPalette?.applyImageModel(recomposeKey)
          )

        // start the image request into the target.
        requestManager
          .load(recomposeKey)
          .apply(builder)
          .addListener(requestListener)
          .into(target)

        awaitClose {
          // intentionally do not clear using the Glide.clear for recycling internal bitmaps.
        }
      }
    },
    modifier = modifier,
    content = content
  )
}
