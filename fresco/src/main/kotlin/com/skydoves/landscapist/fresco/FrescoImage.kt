/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.imagepipeline.request.ImageRequest
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.ComposeFailureStatePlugins
import com.skydoves.landscapist.components.ComposeLoadingStatePlugins
import com.skydoves.landscapist.components.ComposeSuccessStatePlugins
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.imagePlugins
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.rememberBitmapPainter
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Load and render an image with the given [imageUrl] from the network or local storage.
 *
 * ```
 * FrescoImage(
 * imageUrl = stringImageUrl,
 * modifier = modifier,
 * loading = {
 *   Box(modifier = Modifier.matchParentSize()) {
 *     CircularProgressIndicator(
 *        modifier = Modifier.align(Alignment.Center)
 *     )
 *   }
 * },
 * failure = {
 *   Text(text = "image request failed.")
 * })
 * ```
 *
 * @param imageUrl The target url to request image.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageRequest The pipeline has to know about requested image to proceed.
 * @param component An image component that conjuncts pluggable [ImagePlugin]s.
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 * @param onImageStateChanged An image state change listener will be triggered whenever the image state is changed.ø
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 */
@Composable
public fun FrescoImage(
  imageUrl: String?,
  modifier: Modifier = Modifier,
  imageRequest: @Composable () -> ImageRequest = {
    LocalFrescoProvider.getFrescoImageRequest(imageUrl)
  },
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  onImageStateChanged: (FrescoImageState) -> Unit = {},
  @DrawableRes previewPlaceholder: Int = 0,
  loading: @Composable (BoxScope.(imageState: FrescoImageState.Loading) -> Unit)? = null,
  success: @Composable (BoxScope.(imageState: FrescoImageState.Success) -> Unit)? = null,
  failure: @Composable (BoxScope.(imageState: FrescoImageState.Failure) -> Unit)? = null
) {
  if (LocalInspectionMode.current && previewPlaceholder != 0) with(imageOptions) {
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

  var internalState: FrescoImageState by remember { mutableStateOf(FrescoImageState.None) }

  LaunchedEffect(key1 = internalState) {
    onImageStateChanged.invoke(internalState)
  }

  FrescoImage(
    recomposeKey = imageUrl,
    imageRequest = imageRequest.invoke(),
    modifier = modifier
  ) ImageRequest@{ imageState ->
    when (val frescoImageState = imageState.toFrescoImageState().apply { internalState = this }) {
      is FrescoImageState.None -> Unit
      is FrescoImageState.Loading -> {
        component.ComposeLoadingStatePlugins(
          modifier = modifier,
          imageOptions = imageOptions
        )
        loading?.invoke(this, frescoImageState)
      }
      is FrescoImageState.Failure -> {
        component.ComposeFailureStatePlugins(
          modifier = modifier,
          imageOptions = imageOptions,
          reason = frescoImageState.reason
        )
        failure?.invoke(this, frescoImageState)
      }
      is FrescoImageState.Success -> {
        component.ComposeSuccessStatePlugins(
          modifier = modifier,
          imageModel = imageUrl,
          imageOptions = imageOptions,
          imageBitmap = frescoImageState.imageBitmap
        )
        if (success != null) {
          success.invoke(this, frescoImageState)
        } else with(imageOptions) {
          val imageBitmap = frescoImageState.imageBitmap ?: return@ImageRequest

          Image(
            modifier = Modifier.fillMaxSize(),
            painter = rememberBitmapPainter(
              imagePlugins = component.imagePlugins,
              imageBitmap = imageBitmap
            ),
            alignment = alignment,
            contentScale = contentScale,
            contentDescription = contentDescription,
            alpha = alpha,
            colorFilter = colorFilter
          )
        }
      }
    }
  }
}

/**
 * Requests loading an image and create a composable that provides the current state [ImageLoadState] of the content.
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
private fun FrescoImage(
  recomposeKey: Any?,
  imageRequest: ImageRequest,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.(imageState: ImageLoadState) -> Unit
) {
  val context = LocalContext.current
  val datasource =
    remember(recomposeKey) { imagePipeline.fetchDecodedImage(imageRequest, context) }

  ImageLoad(
    recomposeKey = recomposeKey,
    executeImageRequest = {
      suspendCancellableCoroutine { cont ->
        val subscriber = FlowBaseBitmapDataSubscriber()
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
