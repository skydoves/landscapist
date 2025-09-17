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
@file:JvmMultifileClass
@file:JvmName("GlideImage")
@file:Suppress("unused", "UNCHECKED_CAST")

package com.skydoves.landscapist.glide

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
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
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.crossfade.CrossfadeWithEffect
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.rememberBitmapPainter
import com.skydoves.landscapist.rememberDrawablePainter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Load and render an image with the given [imageModel] from the network or local storage.
 *
 * ```
 * GlideImage(
 * imageModel = { imageUrl },
 * requestBuilder = {
 *  Glide
 *   .with(LocalContext.current)
 *   .asBitmap()
 *   .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
 *   .thumbnail(0.6f)
 *   .transition(withCrossFade())
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
 * @param imageModel The data model to request image. See [RequestBuilder.load] for types allowed.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param glideRequestType Glide image request type, which decides the result of image data.
 * @param requestBuilder Most options in Glide can be applied directly on the RequestBuilder object returned by Glide.with().
 * @param requestOptions Provides type independent options to customize loads with Glide.
 * @param requestListener A class for monitoring the status of a request while images load.
 * @param component An image component that conjuncts pluggable [ImagePlugin]s.
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param clearTarget Whether clear the target or not.
 * @param onImageStateChanged An image state change listener will be triggered whenever the image state is changed.
 * @param previewPlaceholder A painter that is specifically rendered when this function operates in preview mode.
 * @param loading Content to be displayed when the request is in progress.
 * @param success Content to be displayed when the request is succeeded.
 * @param failure Content to be displayed when the request is failed.
 */
@Composable
public fun GlideImage(
  imageModel: () -> Any?,
  modifier: Modifier = Modifier,
  glideRequestType: GlideRequestType = GlideRequestType.DRAWABLE,
  requestBuilder: @Composable () -> RequestBuilder<*> = {
    LocalGlideProvider.getGlideRequestBuilder()
  },
  requestOptions: @Composable () -> RequestOptions = {
    LocalGlideProvider.getGlideRequestOptions()
  },
  requestListener: (() -> RequestListener<Any>)? = null,
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  clearTarget: Boolean = false,
  onImageStateChanged: (GlideImageState) -> Unit = {},
  previewPlaceholder: Painter? = null,
  loading: @Composable (BoxScope.(imageState: GlideImageState.Loading) -> Unit)? = null,
  success: @Composable (
    BoxScope.(
      imageState: GlideImageState.Success,
      painter: Painter,
    ) -> Unit
  )? = null,
  failure: @Composable (BoxScope.(imageState: GlideImageState.Failure) -> Unit)? = null,
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

  GlideImage(
    recomposeKey = StableHolder(imageModel.invoke()),
    imageOptions = imageOptions,
    builder = StableHolder(
      requestBuilder.invoke()
        .apply(requestOptions.invoke())
        .load(imageModel.invoke()) as RequestBuilder<Any>,
    ),
    glideRequestType = glideRequestType,
    requestListener = StableHolder(requestListener?.invoke()),
    clearTarget = clearTarget,
    modifier = modifier,
  ) ImageRequest@{ imageState ->

    val crossfadePlugin = component.imagePlugins.filterIsInstance<CrossfadePlugin>().firstOrNull()

    CrossfadeWithEffect(
      targetState = imageState,
      durationMs = crossfadePlugin?.duration ?: 0,
      contentKey = { imageState },
      enabled = crossfadePlugin != null,
    ) {
      val glideImageState: GlideImageState = remember(imageState) {
        imageState.toGlideImageState(
          glideRequestType = glideRequestType,
        ).apply { onImageStateChanged.invoke(this) }
      }

      when (glideImageState) {
        is GlideImageState.None -> Unit

        is GlideImageState.Loading -> {
          component.ComposeLoadingStatePlugins(
            modifier = Modifier.constraint(this),
            imageOptions = imageOptions,
            executor = { size ->
              GlideThumbnail(
                requestSize = size,
                recomposeKey = StableHolder(imageModel.invoke()),
                imageOptions = imageOptions,
                builder = StableHolder(
                  requestBuilder.invoke().load(imageModel.invoke()) as RequestBuilder<Any>,
                ),
              )
            },
          )
          loading?.invoke(this, glideImageState)
        }

        is GlideImageState.Failure -> {
          component.ComposeFailureStatePlugins(
            modifier = Modifier.constraint(this),
            imageOptions = imageOptions,
            reason = glideImageState.reason,
          )
          failure?.invoke(this, glideImageState)
        }

        is GlideImageState.Success -> {
          component.ComposeSuccessStatePlugins(
            modifier = Modifier.constraint(this),
            imageModel = imageModel,
            imageOptions = imageOptions,
            imageBitmap = glideImageState.data.toImageBitmap(
              glideRequestType = glideRequestType,
            ),
          )

          val data = glideImageState.data ?: return@CrossfadeWithEffect
          val painter = if (data is Drawable) {
            rememberDrawablePainter(
              drawable = data,
              imagePlugins = component.imagePlugins,
            )
          } else {
            rememberBitmapPainter(
              imageBitmap = data.toImageBitmap(glideRequestType = glideRequestType),
              imagePlugins = component.imagePlugins,
            )
          }

          if (success != null) {
            success.invoke(this, glideImageState, painter)
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
}

/**
 * Requests loading an image and create a composable that provides the current state [ImageLoadState] of the content.
 *
 * ```
 * GlideImage(
 * requestBuilder = Glide
 *   .with(LocalContext.current)
 *   .asBitmap()
 *   .load(poster.poster)
 *   .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
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
 * @param recomposeKey request to execute image loading asynchronously.
 * @param modifier [Modifier] used to adjust the layout or drawing content.
 * @param imageOptions Represents parameters to load generic [Image] Composable.
 * @param glideRequestType Glide image request type, which decides the result of image data.
 * @param clearTarget Whether clear the target or not.
 * @param builder The request to execute.
 * @param requestListener A class for monitoring the status of a request while images load.
 * @param content Content to be displayed for the given state.
 */
@Composable
private fun GlideImage(
  recomposeKey: StableHolder<Any?>,
  modifier: Modifier = Modifier,
  imageOptions: ImageOptions,
  glideRequestType: GlideRequestType,
  clearTarget: Boolean = false,
  builder: StableHolder<RequestBuilder<Any>>,
  requestListener: StableHolder<RequestListener<Any>?> = StableHolder(null),
  content: @Composable BoxWithConstraintsScope.(imageState: ImageLoadState) -> Unit,
) {
  val requestManager = LocalGlideProvider.getGlideRequestManager()
  val target = rememberTarget(
    target = FlowCustomTarget(imageOptions = imageOptions),
    imageOptions = imageOptions,
    clearTarget = clearTarget,
  )

  ImageLoad(
    recomposeKey = recomposeKey.value,
    constrainable = target,
    executeImageRequest = {
      callbackFlow {
        target.setProducerScope(this)

        val flowRequestListener = FlowRequestListener(this) {
          target.updateFailException(it)
        }

        // start the image request into the target.
        val typedBuilder = requestManager.buildRequestBuilder(
          glideRequestType = glideRequestType,
          recomposeKey = recomposeKey,
          flowRequestListener = flowRequestListener,
          requestListener = requestListener,
          builder = builder,
        )

        // override this size.
        val sizedBuilder = if (imageOptions.isValidSize) {
          typedBuilder.override(imageOptions.requestSize.width, imageOptions.requestSize.height)
        } else {
          typedBuilder
        }

        sizedBuilder.into(target)

        awaitClose {
          // intentionally do not clear using the Glide.clear for recycling internal bitmaps.
        }
      }
    },
    imageOptions = imageOptions,
    modifier = modifier,
    content = content,
  )
}

@Composable
private fun GlideThumbnail(
  requestSize: IntSize,
  recomposeKey: StableHolder<Any?>,
  imageOptions: ImageOptions,
  builder: StableHolder<RequestBuilder<Any>>,
) {
  val glideRequestType = GlideRequestType.DRAWABLE
  GlideImage(
    recomposeKey = recomposeKey,
    imageOptions = imageOptions.copy(requestSize = requestSize),
    builder = builder,
    glideRequestType = glideRequestType,
    requestListener = StableHolder(null),
  ) ImageRequest@{ imageState ->
    val glideImageState = imageState.toGlideImageState(
      glideRequestType = glideRequestType,
    )
    if (glideImageState is GlideImageState.Success) {
      val data = glideImageState.data ?: return@ImageRequest
      imageOptions.LandscapistImage(
        modifier = Modifier,
        painter = rememberDrawablePainter(
          drawable = data as Drawable,
          imagePlugins = emptyList(),
        ),
      )
    }
  }
}

private fun RequestManager.buildRequestBuilder(
  glideRequestType: GlideRequestType,
  recomposeKey: StableHolder<Any?>,
  flowRequestListener: FlowRequestListener,
  builder: StableHolder<RequestBuilder<Any>>,
  requestListener: StableHolder<RequestListener<Any>?> = StableHolder(null),
): RequestBuilder<Any> {
  return when (glideRequestType) {
    GlideRequestType.DRAWABLE -> asDrawable()
      .load(recomposeKey.value)
      .apply(builder.value)
      .addListener(flowRequestListener as RequestListener<Drawable>)
      .addListener(requestListener.value as RequestListener<Drawable>?)

    GlideRequestType.GIF -> asGif()
      .load(recomposeKey.value)
      .apply(builder.value)
      .addListener(flowRequestListener as RequestListener<GifDrawable>)
      .addListener(requestListener.value as RequestListener<GifDrawable>?)

    GlideRequestType.BITMAP -> asBitmap()
      .load(recomposeKey.value)
      .apply(builder.value)
      .addListener(flowRequestListener as RequestListener<Bitmap>)
      .addListener(requestListener.value as RequestListener<Bitmap>?)
  } as RequestBuilder<Any>
}
