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
@file:JvmName("CoilImage")
@file:JvmMultifileClass

package com.skydoves.landscapist.coil

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.size.SizeResolver
import com.skydoves.landscapist.DataSource
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
import com.skydoves.landscapist.constraints.Constrainable
import com.skydoves.landscapist.constraints.constraint
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.rememberDrawablePainter
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.channelFlow
import okhttp3.HttpUrl
import java.io.File
import java.nio.ByteBuffer

/**
 * Load and render an image with the given [imageModel] from the network or local storage.
 *
 * Supported types for the [imageModel] are the below:
 * [String], [Uri], [HttpUrl], [File], [DrawableRes], [Drawable], [Bitmap], [ByteArray], [ByteBuffer]
 *
 * ```
 * CoilImage(
 * imageModel = { imageModel },
 * modifier = modifier,
 * imageOptions = ImageOptions(contentScale = ContentScale.Crop),
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
 * @param imageModel The data model to request image. See [ImageRequest.Builder.data] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * @param component An image component that conjuncts pluggable [ImagePlugin]s.
 * @param requestListener A class for monitoring the status of a request while images load.
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param onImageStateChanged An image state change listener will be triggered whenever the image state is changed.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun CoilImage(
  imageModel: () -> Any?,
  modifier: Modifier = Modifier,
  imageLoader: @Composable () -> ImageLoader = { LocalCoilProvider.getCoilImageLoader() },
  component: ImageComponent = rememberImageComponent {},
  requestListener: (() -> ImageRequest.Listener)? = null,
  imageOptions: ImageOptions = ImageOptions(),
  onImageStateChanged: (CoilImageState) -> Unit = {},
  @DrawableRes previewPlaceholder: Int = 0,
  loading: @Composable (BoxScope.(imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable (
    BoxScope.(
      imageState: CoilImageState.Success,
      painter: Painter,
    ) -> Unit
  )? = null,
  failure: @Composable (BoxScope.(imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  CoilImage(
    imageRequest = {
      ImageRequest.Builder(context)
        .data(imageModel.invoke())
        .listener(requestListener?.invoke())
        .lifecycle(lifecycleOwner)
        .build()
    },
    imageLoader = imageLoader,
    component = component,
    modifier = modifier,
    imageOptions = imageOptions,
    onImageStateChanged = onImageStateChanged,
    previewPlaceholder = previewPlaceholder,
    loading = loading,
    success = success,
    failure = failure,
  )
}

/**
 * Load and render an image with the given [imageRequest] from the network or local storage.
 *
 * ```
 * CoilImage(
 * imageRequest = {
 *   ImageRequest.Builder(context)
 *      .data(imageModel)
 *      .lifecycle(lifecycleOwner)
 *      .build()
 * },
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
 * @param imageRequest The request to execute.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * @param component An image component that conjuncts pluggable [ImagePlugin]s.
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param onImageStateChanged An image state change listener will be triggered whenever the image state is changed.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun CoilImage(
  imageRequest: () -> ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: @Composable () -> ImageLoader = { LocalCoilProvider.getCoilImageLoader() },
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  onImageStateChanged: (CoilImageState) -> Unit = {},
  @DrawableRes previewPlaceholder: Int = 0,
  loading: @Composable (BoxScope.(imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable (
    BoxScope.(
      imageState: CoilImageState.Success,
      painter: Painter,
    ) -> Unit
  )? = null,
  failure: @Composable (BoxScope.(imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  if (LocalInspectionMode.current && previewPlaceholder != 0) {
    with(imageOptions) {
      Image(
        modifier = modifier,
        painter = painterResource(id = previewPlaceholder),
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        contentDescription = contentDescription,
      )
      return
    }
  }

  CoilImage(
    recomposeKey = StableHolder(imageRequest.invoke()),
    imageLoader = StableHolder(imageLoader.invoke()),
    imageOptions = imageOptions,
    modifier = modifier,
  ) ImageRequest@{ imageState ->
    when (
      val coilImageState = imageState.toCoilImageState().apply {
        onImageStateChanged.invoke(this)
      }
    ) {
      is CoilImageState.None -> Unit

      is CoilImageState.Loading -> {
        component.ComposeLoadingStatePlugins(
          modifier = modifier,
          imageOptions = imageOptions,
          executor = {},
        )
        loading?.invoke(this, coilImageState)
      }

      is CoilImageState.Failure -> {
        component.ComposeFailureStatePlugins(
          modifier = modifier,
          imageOptions = imageOptions,
          reason = coilImageState.reason,
        )
        failure?.invoke(this, coilImageState)
      }

      is CoilImageState.Success -> {
        component.ComposeSuccessStatePlugins(
          modifier = modifier,
          imageModel = imageRequest.invoke().data,
          imageOptions = imageOptions,
          imageBitmap = coilImageState.drawable?.toBitmap()
            ?.copy(Bitmap.Config.ARGB_8888, true)?.asImageBitmap(),
        )

        val drawable = coilImageState.drawable ?: return@ImageRequest
        val painter = rememberDrawablePainter(
          drawable = drawable,
          imagePlugins = component.imagePlugins,
        )

        if (success != null) {
          success.invoke(this, coilImageState, painter)
        } else {
          imageOptions.LandscapistImage(
            modifier = Modifier
              .constraint(this)
              .testTag(imageOptions.testTag),
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
 * CoilImage(
 * recomposeKey = ImageRequest.Builder(context)
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
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * @param content Content to be displayed for the given state.
 */
@Composable
private fun CoilImage(
  recomposeKey: StableHolder<ImageRequest>,
  modifier: Modifier = Modifier,
  imageOptions: ImageOptions,
  imageLoader: StableHolder<ImageLoader> = StableHolder(LocalCoilProvider.getCoilImageLoader()),
  content: @Composable BoxWithConstraintsScope.(imageState: ImageLoadState) -> Unit,
) {
  val context = LocalContext.current
  val request = rememberRequestWithConstraints(
    request = recomposeKey.value,
    imageOptions = imageOptions,
  )
  val constrainable: Constrainable? =
    remember(recomposeKey, imageOptions) { (request.sizeResolver) as? Constrainable }

  ImageLoad(
    recomposeKey = recomposeKey.value,
    constrainable = constrainable,
    executeImageRequest = {
      channelFlow {
        val newBuilder = request.newBuilder(context).target(
          onStart = { trySendBlocking(ImageLoadState.Loading) },
        ).build()

        val result = imageLoader.value.execute(newBuilder).toResult()
        send(result)
      }
    },
    imageOptions = imageOptions,
    modifier = modifier,
    content = content,
  )
}

private fun ImageResult.toResult(): ImageLoadState = when (this) {
  is coil.request.SuccessResult -> {
    ImageLoadState.Success(
      data = drawable,
      dataSource = dataSource.toDataSource(),
    )
  }

  is coil.request.ErrorResult -> {
    ImageLoadState.Failure(
      data = drawable?.toBitmap()?.asImageBitmap(),
      reason = throwable,
    )
  }
}

private fun coil.decode.DataSource.toDataSource(): DataSource = when (this) {
  coil.decode.DataSource.NETWORK -> DataSource.NETWORK
  coil.decode.DataSource.MEMORY -> DataSource.MEMORY
  coil.decode.DataSource.MEMORY_CACHE -> DataSource.MEMORY
  coil.decode.DataSource.DISK -> DataSource.DISK
}

@Composable
internal fun rememberRequestWithConstraints(
  request: ImageRequest,
  imageOptions: ImageOptions,
): ImageRequest {
  return remember(request, imageOptions) {
    if (request.defined.sizeResolver == null) {
      val sizeResolver = if (imageOptions.isValidSize) {
        SizeResolver(
          coil.size.Size(
            width = imageOptions.requestSize.width,
            height = imageOptions.requestSize.height,
          ),
        )
      } else if (imageOptions.contentScale == ContentScale.None) {
        SizeResolver(coil.size.Size.ORIGINAL)
      } else {
        ConstraintsSizeResolver()
      }
      request.newBuilder().size(sizeResolver).build()
    } else {
      request
    }
  }
}
