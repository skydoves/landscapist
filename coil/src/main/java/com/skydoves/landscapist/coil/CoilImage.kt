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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.graphics.asImageAsset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.platform.LifecycleOwnerAmbient
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleOwner
import coil.ImageLoader
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Requests loading an image with a loading placeholder and error imageAsset.
 *
 * ```
 * CoilImage(
 *   imageModel = imageModel,
 *   placeHolder = imageResource(R.drawable.placeholder),
 *   error = imageResource(R.drawable.error)
 * )
 * ```
 */
@Composable
fun CoilImage(
  imageModel: Any,
  context: Context = ContextAmbient.current,
  lifecycleOwner: LifecycleOwner = LifecycleOwnerAmbient.current,
  imageLoader: ImageLoader = context.imageLoader,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  placeHolder: ImageAsset? = null,
  error: ImageAsset? = null,
) {
  CoilImage(
    imageModel = imageModel,
    context = context,
    lifecycleOwner = lifecycleOwner,
    imageLoader = imageLoader,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
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
 */
@Composable
fun CoilImage(
  imageModel: Any,
  context: Context = ContextAmbient.current,
  lifecycleOwner: LifecycleOwner = LifecycleOwnerAmbient.current,
  imageLoader: ImageLoader = context.imageLoader,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
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
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
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
fun CoilImage(
  imageRequest: ImageRequest,
  imageLoader: ImageLoader = ContextAmbient.current.imageLoader,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  loading: @Composable ((imageState: CoilImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: CoilImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: CoilImageState.Failure) -> Unit)? = null,
) {
  CoilImage(
    request = imageRequest,
    imageLoader = imageLoader,
    modifier = modifier,
  ) { imageState ->
    when (val coilImageState = imageState.toCoilImageState()) {
      is CoilImageState.None -> Unit
      is CoilImageState.Loading -> loading?.invoke(coilImageState)
      is CoilImageState.Failure -> failure?.invoke(coilImageState)
      is CoilImageState.Success -> {
        success?.invoke(coilImageState) ?: coilImageState.imageAsset?.let {
          Image(
            asset = it,
            modifier = modifier,
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
 */
@Composable
@OptIn(ExperimentalCoroutinesApi::class)
fun CoilImage(
  request: ImageRequest,
  imageLoader: ImageLoader = ContextAmbient.current.imageLoader,
  modifier: Modifier = Modifier.fillMaxWidth(),
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  val context = ContextAmbient.current
  val imageLoadStateFlow = remember { MutableStateFlow<ImageLoadState>(ImageLoadState.Loading(0f)) }
  val disposable = remember { mutableStateOf<Disposable?>(null) }

  ImageLoad(
    imageRequest = request,
    executeImageRequest = {
      disposable.value = imageLoader.enqueue(
        request.newBuilder(context).target(
          onSuccess = {
            imageLoadStateFlow.value = ImageLoadState.Success(it.toBitmap().asImageAsset())
          },
          onError = {
            imageLoadStateFlow.value = ImageLoadState.Failure(it?.toBitmap()?.asImageAsset())
          }
        ).build()
      )
      imageLoadStateFlow
    },
    disposeImageRequest = {
      disposable.value?.dispose()
    },
    modifier = modifier,
    content = content
  )
}
