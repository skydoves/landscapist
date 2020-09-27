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

package com.github.skydoves.landscapist.glide

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
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.github.skydoves.landscapist.ImageLoad
import com.github.skydoves.landscapist.ImageLoadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Requests loading an image with a loading placeholder and error imageAsset.
 *
 * ```
 * GlideImage(
 *   imageModel = imageUrl,
 *   placeHolder = imageResource(R.drawable.placeholder),
 *   error = imageResource(R.drawable.error)
 * )
 * ```
 */
@Composable
@ExperimentalCoroutinesApi
fun GlideImage(
  imageModel: Any,
  requestOption: RequestOptions = defaultRequestOptions,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  placeHolder: ImageAsset? = null,
  error: ImageAsset? = null
) {
  GlideImage(
    imageModel = imageModel,
    requestOption = requestOption,
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    colorFilter = colorFilter,
    alpha = alpha,
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
 * requestOption = RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL),
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
fun GlideImage(
  imageModel: Any,
  requestOption: RequestOptions = defaultRequestOptions,
  modifier: Modifier = Modifier.fillMaxWidth(),
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  loading: @Composable ((imageState: GlideImageState.Loading) -> Unit)? = null,
  success: @Composable ((imageState: GlideImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: GlideImageState.Failure) -> Unit)? = null,
) {
  GlideImage(
    model = imageModel,
    requestOption = requestOption,
    modifier = modifier,
  ) { imageState ->
    when (val glideImageState = imageState.toGlideImageState()) {
      is GlideImageState.None -> Unit
      is GlideImageState.Loading -> loading?.invoke(glideImageState)
      is GlideImageState.Failure -> failure?.invoke(glideImageState)
      is GlideImageState.Success -> {
        success?.invoke(glideImageState) ?: glideImageState.imageAsset?.let {
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
 * GlideImage(
 * model = imageUrl,
 * modifier = modifier,
 * requestOption = RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL),
 * ) { imageState ->
 *   when (val glideImageState = imageState.toGlideImageState()) {
 *     is GlideImageState.None -> // do something
 *     is GlideImageState.Loading -> // do something
 *     is GlideImageState.Failure -> // do something
 *     is GlideImageState.Success ->  // do something
 *   }
 * }
 * ```
 */
@Composable
@ExperimentalCoroutinesApi
private fun GlideImage(
  model: Any,
  requestOption: RequestOptions = RequestOptions(),
  modifier: Modifier = Modifier.fillMaxWidth(),
  content: @Composable (imageState: ImageLoadState) -> Unit
) {
  val context = ContextAmbient.current
  val target = remember { FlowCustomTarget() }
  var job: Job? = remember { null }

  ImageLoad(
    imageRequest = requestOption,
    executeImageRequest = {
      job = CoroutineScope(Dispatchers.IO).launch {
        Glide.with(context)
          .asBitmap()
          .load(model)
          .apply(requestOption)
          .into(target)
      }
      target.imageLoadStateFlow
    },
    disposeImageRequest = {
      Glide.with(context).clear(target)
      job?.cancel()
    },
    modifier = modifier,
    content = content
  )
}

private inline val defaultRequestOptions: RequestOptions
  @JvmName("defaultRequestOptions")
  get() = RequestOptions()
