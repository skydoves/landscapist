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
@file:JvmName("CoilImage")
@file:JvmMultifileClass

package com.skydoves.landscapist.coil

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.ComposeFailureStatePlugins
import com.skydoves.landscapist.components.ComposeLoadingStatePlugins
import com.skydoves.landscapist.components.ComposeSuccessStatePlugins
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.imagePlugins
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.palette.BitmapPalette
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.rememberDrawablePainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Requests loading an image and create some composables based on [CoilImageState].
 *
 * ```
 * CoilImage(
 * imageModel = imageModel,
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
 * @param imageModel The data model to request image. See [ImageRequest.Builder.data] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param context The context for creating the [ImageRequest.Builder].
 * @param lifecycleOwner The [LifecycleOwner] for constructing the [ImageRequest.Builder].
 * @param imageLoader The [ImageLoader] to use when requesting the image.
 * @param component An image component that conjuncts pluggable [ImagePlugin]s.
 * @param requestListener A class for monitoring the status of a request while images load.
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
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
  imageLoader: @Composable () -> ImageLoader = { LocalCoilProvider.getCoilImageLoader() },
  component: ImageComponent = rememberImageComponent {},
  requestListener: ImageRequest.Listener? = null,
  imageOptions: ImageOptions = ImageOptions(),
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
  loading: @Composable (BoxScope.(imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable (BoxScope.(imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable (BoxScope.(imageState: CoilImageState.Failure) -> Unit)? = null
) {
  CoilImage(
    imageRequest = ImageRequest.Builder(context)
      .data(imageModel)
      .listener(requestListener)
      .lifecycle(lifecycleOwner)
      .build(),
    imageLoader = imageLoader,
    component = component,
    modifier = modifier,
    imageOptions = imageOptions,
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
 * @param bitmapPalette A [Palette] generator for extracting major (theme) colors from images.
 * @param previewPlaceholder Drawable resource ID which will be displayed when this function is ran in preview mode.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun CoilImage(
  imageRequest: ImageRequest,
  modifier: Modifier = Modifier,
  imageLoader: @Composable () -> ImageLoader = { LocalCoilProvider.getCoilImageLoader() },
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  bitmapPalette: BitmapPalette? = null,
  @DrawableRes previewPlaceholder: Int = 0,
  loading: @Composable (BoxScope.(imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable (BoxScope.(imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable (BoxScope.(imageState: CoilImageState.Failure) -> Unit)? = null
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

  CoilImage(
    recomposeKey = imageRequest,
    imageLoader = imageLoader.invoke(),
    modifier = modifier,
    bitmapPalette = bitmapPalette
  ) ImageRequest@{ imageState ->
    when (val coilImageState = imageState.toCoilImageState()) {
      is CoilImageState.None -> Unit
      is CoilImageState.Loading -> {
        component.ComposeLoadingStatePlugins(
          modifier = modifier,
          imageOptions = imageOptions
        )
        loading?.invoke(this, coilImageState)
      }
      is CoilImageState.Failure -> {
        component.ComposeFailureStatePlugins(
          modifier = modifier,
          imageOptions = imageOptions
        )
        failure?.invoke(this, coilImageState)
      }
      is CoilImageState.Success -> {
        component.ComposeSuccessStatePlugins(
          modifier = modifier,
          imageOptions = imageOptions
        )
        if (success != null) {
          success.invoke(this, coilImageState)
        } else with(imageOptions) {
          val drawable = coilImageState.drawable ?: return@ImageRequest
          Image(
            modifier = Modifier.fillMaxSize(),
            painter = rememberDrawablePainter(
              drawable = drawable,
              imagePlugins = component.imagePlugins
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
  content: @Composable BoxScope.(imageState: ImageLoadState) -> Unit
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
              imageLoadStateFlow.value =
                ImageLoadState.Failure(it?.toBitmap()?.asImageBitmap())
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
