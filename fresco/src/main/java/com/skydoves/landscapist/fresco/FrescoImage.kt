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

package com.skydoves.landscapist.fresco

import android.net.Uri
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
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
 */
@Composable
@ExperimentalCoroutinesApi
fun FrescoImage(
  imageUrl: String?,
  imageRequest: ImageRequest = imageUrl.defaultImageRequest,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
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
    observeLoadingProcess = observeLoadingProcess,
    loading = {
      placeHolder?.let {
        Image(
          asset = it,
          modifier = modifier,
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
          modifier = modifier,
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
@ExperimentalCoroutinesApi
fun FrescoImage(
  imageUrl: String?,
  imageRequest: ImageRequest = imageUrl.defaultImageRequest,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
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
          Image(
            asset = it,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter
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
 */
@Composable
@ExperimentalCoroutinesApi
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
      val subscriber = FlowBaseBitmapDataSubscriber(observeLoadingProcess)
      datasource.subscribe(subscriber, CallerThreadExecutor.getInstance())
      subscriber.imageLoadStateFlow
    },
    disposeImageRequest = {
      datasource.close()
    },
    modifier = modifier,
    content = content
  )
}

private inline val String?.defaultImageRequest: ImageRequest
  @JvmName("defaultImageRequest")
  get() = ImageRequestBuilder.newBuilderWithSource(Uri.parse(this)).build()
