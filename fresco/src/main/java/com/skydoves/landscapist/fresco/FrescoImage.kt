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
@file:JvmName("FrescoImage")
@file:JvmMultifileClass

package com.skydoves.landscapist.fresco

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
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.imagepipeline.request.ImageRequest
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
 * FrescoImage(
 *   imageUrl = stringImageUrl,
 *   placeHolder = imageResource(R.drawable.placeholder),
 *   error = imageResource(R.drawable.error)
 * )
 * ```
 *
 * @param imageUrl The target url to request image.
 * @param imageRequest The pipeline has to know about requested image to proceed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param alignment The alignment parameter used to place the loaded [ImageAsset] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageAsset].
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param placeHolder An [ImageAsset] to be displayed when the request is in progress.
 * @param error An [ImageAsset] for showing instead of the target image when images are failed to load.
 */
@Composable
fun FrescoImage(
  imageUrl: String?,
  imageRequest: ImageRequest = FrescoAmbientProvider.getFrescoImageRequest(imageUrl),
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  placeHolder: ImageAsset? = null,
  error: ImageAsset? = null,
  observeLoadingProcess: Boolean = false
) {
  FrescoImage(
    imageUrl = imageUrl,
    imageRequest = imageRequest,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    colorFilter = colorFilter,
    alpha = alpha,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    observeLoadingProcess = observeLoadingProcess,
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
          alpha = alpha,
        )
      }
    }
  )
}

/**
 * Requests loading an image with a loading placeholder and error imageAsset.
 *
 * ```
 * FrescoImage(
 *   imageUrl = stringImageUrl,
 *   placeHolder = imageResource(R.drawable.placeholder),
 *   error = imageResource(R.drawable.error)
 * )
 * ```
 *
 * @param imageUrl The target url to request image.
 * @param imageRequest The pipeline has to know about requested image to proceed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param alignment The alignment parameter used to place the loaded [ImageAsset] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageAsset].
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param shimmerParams The shimmer related parameter used to determine constructions of the [Shimmer].
 * @param error An [ImageAsset] for showing instead of the target image when images are failed to load.
 */
@Composable
fun FrescoImage(
  imageUrl: String?,
  imageRequest: ImageRequest = FrescoAmbientProvider.getFrescoImageRequest(imageUrl),
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  shimmerParams: ShimmerParams,
  error: ImageAsset? = null,
  observeLoadingProcess: Boolean = false
) {
  FrescoImage(
    imageUrl = imageUrl,
    imageRequest = imageRequest,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    colorFilter = colorFilter,
    alpha = alpha,
    circularRevealedEnabled = circularRevealedEnabled,
    circularRevealedDuration = circularRevealedDuration,
    observeLoadingProcess = observeLoadingProcess,
    shimmerParams = shimmerParams,
    failure = {
      error?.let {
        Image(
          asset = it,
          alignment = alignment,
          contentScale = contentScale,
          colorFilter = colorFilter,
          alpha = alpha,
        )
      }
    }
  )
}

/**
 * Requests loading an image and create some composables based on [FrescoImageState].
 *
 * ```
 * FrescoImage(
 * imageUrl = stringImageUrl,
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
 * @param imageUrl The target url to request image.
 * @param imageRequest The pipeline has to know about requested image to proceed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param alignment The alignment parameter used to place the loaded [ImageAsset] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageAsset].
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
fun FrescoImage(
  imageUrl: String?,
  imageRequest: ImageRequest = FrescoAmbientProvider.getFrescoImageRequest(imageUrl),
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  observeLoadingProcess: Boolean = false,
  shimmerParams: ShimmerParams,
  success: @Composable ((imageState: FrescoImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: FrescoImageState.Failure) -> Unit)? = null,
) {
  FrescoImage(
    imageRequest = imageRequest,
    modifier = modifier,
    observeLoadingProcess = observeLoadingProcess,
  ) { imageState ->
    when (val frescoImageState = imageState.toFrescoImageState()) {
      is FrescoImageState.None -> Unit
      is FrescoImageState.Loading -> {
        Shimmer(
          baseColor = shimmerParams.baseColor,
          highlightColor = shimmerParams.highlightColor,
          intensity = shimmerParams.intensity,
          dropOff = shimmerParams.dropOff,
          tilt = shimmerParams.tilt,
          durationMillis = shimmerParams.durationMillis
        )
      }
      is FrescoImageState.Failure -> failure?.invoke(frescoImageState)
      is FrescoImageState.Success -> {
        success?.invoke(frescoImageState) ?: frescoImageState.imageAsset?.let {
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
 * Requests loading an image and create some composables based on [FrescoImageState].
 *
 * ```
 * FrescoImage(
 * imageUrl = stringImageUrl,
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
 * @param imageUrl The target url to request image.
 * @param imageRequest The pipeline has to know about requested image to proceed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param alignment The alignment parameter used to place the loaded [ImageAsset] in the image container.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageAsset].
 * @param circularRevealedEnabled Whether to run a circular reveal animation when images are successfully loaded.
 * @param circularRevealedDuration The duration of the circular reveal animation.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
fun FrescoImage(
  imageUrl: String?,
  imageRequest: ImageRequest = FrescoAmbientProvider.getFrescoImageRequest(imageUrl),
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  observeLoadingProcess: Boolean = false,
  loading: @Composable ((imageState: FrescoImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: FrescoImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: FrescoImageState.Failure) -> Unit)? = null,
) {
  FrescoImage(
    imageRequest = imageRequest,
    modifier = modifier,
    observeLoadingProcess = observeLoadingProcess,
  ) { imageState ->
    when (val frescoImageState = imageState.toFrescoImageState()) {
      is FrescoImageState.None -> Unit
      is FrescoImageState.Loading -> loading?.invoke(frescoImageState)
      is FrescoImageState.Failure -> failure?.invoke(frescoImageState)
      is FrescoImageState.Success -> {
        success?.invoke(frescoImageState) ?: frescoImageState.imageAsset?.let {
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
 * FrescoImage(
 * imageUri = Uri.parse(stringImageUrl),
 * modifier = modifier,
 * imageRequest = imageRequest
 * ) { imageState ->
 *   when (val frescoImageState = imageState.toFrescoImageState()) {
 *     is FrescoImageState.None -> // do something
 *     is FrescoImageState.Loading -> // do something
 *     is FrescoImageState.Failure -> // do something
 *     is FrescoImageState.Success ->  // do something
 *   }
 * }
 * ```
 *
 * @param imageRequest The pipeline has to know about requested image to proceed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param content Content to be displayed for the given state.
 */
@Composable
@OptIn(ExperimentalCoroutinesApi::class)
private fun FrescoImage(
  imageRequest: ImageRequest,
  modifier: Modifier = Modifier.fillMaxWidth(),
  observeLoadingProcess: Boolean = false,
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  val context = ContextAmbient.current
  val datasource = remember { imagePipeline.fetchDecodedImage(imageRequest, context) }

  ImageLoad(
    imageRequest = imageRequest,
    executeImageRequest = {
      suspendCancellableCoroutine { cont ->
        val subscriber = FlowBaseBitmapDataSubscriber(observeLoadingProcess)
        datasource.subscribe(subscriber, CallerThreadExecutor.getInstance())

        cont.resume(subscriber.imageLoadStateFlow) {
          // close the fresco datasource request if cancelled.
          datasource.close()
        }
      }
    },
    modifier = modifier,
    content = content
  )
}
