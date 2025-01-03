/*
 * Designed and developed by 2020-2023 skydoves (Jaewoong Eum)
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.drawee.backends.pipeline.info.ImageOriginRequestListener
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.LandscapistImage
import com.skydoves.landscapist.StableHolder
import com.skydoves.landscapist.components.ComposeFailureStatePlugins
import com.skydoves.landscapist.components.ComposeLoadingStatePlugins
import com.skydoves.landscapist.components.ComposeSuccessStatePlugins
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.imagePlugins
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.constraints.constraint
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
 * @param previewPlaceholder A painter that is specifically rendered when this function operates in preview mode.
 */
@Composable
public fun FrescoImage(
  imageUrl: String?,
  modifier: Modifier = Modifier,
  imageRequest: @Composable () -> ImageRequestBuilder = {
    LocalFrescoProvider.getFrescoImageRequest(imageUrl)
  },
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  onImageStateChanged: (FrescoImageState) -> Unit = {},
  previewPlaceholder: Painter? = null,
  loading: @Composable (BoxScope.(imageState: FrescoImageState.Loading) -> Unit)? = null,
  success: @Composable (
    BoxScope.(
      imageState: FrescoImageState.Success,
      painter: Painter,
    ) -> Unit
  )? = null,
  failure: @Composable (BoxScope.(imageState: FrescoImageState.Failure) -> Unit)? = null,
) {
  if (LocalInspectionMode.current && previewPlaceholder != null) {
    with(imageOptions) {
      Image(
        modifier = modifier,
        painter = previewPlaceholder,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        contentDescription = contentDescription,
      )
      return
    }
  }

  FrescoImage(
    recomposeKey = imageUrl,
    imageOptions = imageOptions,
    imageRequest = StableHolder(imageRequest.invoke()),
    modifier = modifier,
  ) ImageRequest@{ imageState ->

    val state: FrescoImageState = imageState.toFrescoImageState()
    val frescoImageState = remember(state) {
      state.apply {
        onImageStateChanged.invoke(this)
      }
    }

    when (frescoImageState) {
      is FrescoImageState.None -> Unit

      is FrescoImageState.Loading -> {
        component.ComposeLoadingStatePlugins(
          modifier = Modifier.constraint(this),
          imageOptions = imageOptions,
          executor = { size ->
            FrescoImageThumbnail(
              requestSize = size,
              recomposeKey = imageUrl,
              imageOptions = imageOptions,
              imageRequest = StableHolder(imageRequest.invoke()),
            )
          },
        )
        loading?.invoke(this, frescoImageState)
      }

      is FrescoImageState.Failure -> {
        component.ComposeFailureStatePlugins(
          modifier = Modifier.constraint(this),
          imageOptions = imageOptions,
          reason = frescoImageState.reason,
        )
        failure?.invoke(this, frescoImageState)
      }

      is FrescoImageState.Success -> {
        component.ComposeSuccessStatePlugins(
          modifier = Modifier.constraint(this),
          imageModel = imageUrl,
          imageOptions = imageOptions,
          imageBitmap = frescoImageState.imageBitmap,
        )

        val imageBitmap = frescoImageState.imageBitmap ?: return@ImageRequest
        val painter = rememberBitmapPainter(
          imagePlugins = component.imagePlugins,
          imageBitmap = imageBitmap,
        )

        if (success != null) {
          success.invoke(this, frescoImageState, painter)
        } else {
          imageOptions.LandscapistImage(
            modifier = Modifier
              .constraint(this)
              .testTag(imageOptions.tag),
            painter = painter,
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
 * @param recomposeKey request to execute image loading asynchronously.
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param imageRequest The pipeline has to know about requested image to proceed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param content Content to be displayed for the given state.
 */
@Composable
private fun FrescoImage(
  recomposeKey: String?,
  imageOptions: ImageOptions,
  imageRequest: StableHolder<ImageRequestBuilder>,
  modifier: Modifier = Modifier,
  content: @Composable BoxWithConstraintsScope.(imageState: ImageLoadState) -> Unit,
) {
  val subscriber = remember(recomposeKey) { FlowBaseBitmapDataSubscriber() }
  val imageOriginRequestListener = ImageOriginRequestListener(
    recomposeKey,
  ) { _, imageOrigin, _, _ ->
    subscriber.updateImageOrigin(imageOrigin)
  }

  val context = LocalContext.current
  val datasource = remember(recomposeKey) {
    imagePipeline.fetchDecodedImage(
      imageRequest.value.apply {
        if (imageOptions.isValidSize) {
          resizeOptions =
            ResizeOptions(imageOptions.requestSize.width, imageOptions.requestSize.height)
        }
      }.build(),
      context,
      imageOriginRequestListener,
    )
  }

  ImageLoad(
    recomposeKey = recomposeKey,
    executeImageRequest = {
      suspendCancellableCoroutine { cont ->
        datasource.subscribe(subscriber, CallerThreadExecutor.getInstance())

        cont.resume(subscriber.imageLoadStateFlow) {
          // close the fresco datasource request if cancelled.
          datasource.close()
        }
      }
    },
    imageOptions = imageOptions,
    modifier = modifier,
    content = content,
  )
}

@Composable
private fun FrescoImageThumbnail(
  requestSize: IntSize,
  recomposeKey: String?,
  imageOptions: ImageOptions,
  imageRequest: StableHolder<ImageRequestBuilder>,
) {
  FrescoImage(
    recomposeKey = recomposeKey,
    imageOptions = imageOptions.copy(requestSize = requestSize),
    imageRequest = imageRequest,
  ) ImageRequest@{ imageState ->
    val frescoImageState = imageState.toFrescoImageState()
    if (frescoImageState is FrescoImageState.Success) {
      val imageBitmap = frescoImageState.imageBitmap ?: return@ImageRequest
      imageOptions.LandscapistImage(
        modifier = Modifier,
        painter = rememberBitmapPainter(
          imageBitmap = imageBitmap,
          imagePlugins = emptyList(),
        ),
      )
    }
  }
}
