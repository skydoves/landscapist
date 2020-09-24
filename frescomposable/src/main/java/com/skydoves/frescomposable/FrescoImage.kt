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

package com.skydoves.frescomposable

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FrameManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onCommit
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.stateFor
import androidx.compose.ui.Modifier
import androidx.compose.ui.WithConstraints
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.graphics.asImageAsset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ContextAmbient
import com.facebook.common.executors.CallerThreadExecutor
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder

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
fun FrescoImage(
  imageUrl: String?,
  modifier: Modifier = Modifier.fillMaxWidth(),
  imageRequest: ImageRequest = getDefaultImageRequest(Uri.parse(imageUrl)),
  contentScale: ContentScale = ContentScale.Crop,
  placeHolder: ImageAsset?,
  error: ImageAsset?
) {
  FrescoImage(
    imageUrl = imageUrl,
    modifier = modifier,
    imageRequest = imageRequest,
    contentScale = contentScale,
    loading = {
      placeHolder?.let {
        Image(
          asset = it,
          modifier = modifier,
          contentScale = contentScale
        )
      }
    },
    failure = {
      error?.let {
        Image(
          asset = it,
          modifier = modifier,
          contentScale = contentScale
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
fun FrescoImage(
  imageUrl: String?,
  modifier: Modifier = Modifier.fillMaxWidth(),
  imageRequest: ImageRequest = getDefaultImageRequest(Uri.parse(imageUrl)),
  contentScale: ContentScale = ContentScale.Crop,
  loading: @Composable (() -> Unit)? = null,
  success: @Composable ((imageState: FrescoImageState.Success) -> Unit)? = null,
  failure: @Composable ((imageState: FrescoImageState.Failure) -> Unit)? = null,
) {
  FrescoImage(
    imageUri = Uri.parse(imageUrl),
    modifier = modifier,
    imageRequest = imageRequest
  ) { imageState ->
    when (imageState) {
      is FrescoImageState.None -> Unit
      is FrescoImageState.Loading -> loading?.invoke()
      is FrescoImageState.Failure -> failure?.invoke(imageState)
      is FrescoImageState.Success -> {
        success?.invoke(imageState) ?: imageState.imageAsset?.let {
          Image(
            asset = it,
            modifier = modifier,
            contentScale = contentScale
          )
        }
      }
    }
  }
}

/**
 * Requests loading an image and create a composable that provides
 * the current state [FrescoImageState] of the content.
 *
 * ```
 * FrescoImage(
 * imageUri = Uri.parse(stringImageUrl),
 * modifier = modifier,
 * imageRequest = imageRequest
 * ) { imageState ->
 *   when (imageState) {
 *     is FrescoImageState.None -> // do something
 *     is FrescoImageState.Loading -> // do something
 *     is FrescoImageState.Failure -> // do something
 *     is FrescoImageState.Success ->  // do something
 *   }
 * }
 * ```
 */
@Composable
fun FrescoImage(
  imageUri: Uri,
  modifier: Modifier = Modifier.fillMaxWidth(),
  imageRequest: ImageRequest,
  content: @Composable (imageState: FrescoImageState) -> Unit
) {
  WithConstraints(modifier) {
    val context = ContextAmbient.current
    val image = remember { mutableStateOf<ImageAsset?>(null) }
    var state by stateFor<FrescoImageState>(imageRequest) { FrescoImageState.None }

    onCommit(imageUri) {
      state = FrescoImageState.Loading
      val datasource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, context)
      datasource.subscribe(
        object : BaseBitmapDataSubscriber() {
          override fun onNewResultImpl(bitmap: Bitmap?) {
            FrameManager.ensureStarted()
            image.value = bitmap?.asImageAsset()
            state = FrescoImageState.Success(image.value)
          }

          override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
            image.value = null
            state = FrescoImageState.Failure(dataSource)
          }
        },
        CallerThreadExecutor.getInstance()
      )

      onDispose {
        image.value = null
        datasource.close()
      }
    }

    content(state)
  }
}

/** returns a default [ImageRequest] without any options. */
private fun getDefaultImageRequest(imageUri: Uri) =
  ImageRequestBuilder.newBuilderWithSource(imageUri).build()
