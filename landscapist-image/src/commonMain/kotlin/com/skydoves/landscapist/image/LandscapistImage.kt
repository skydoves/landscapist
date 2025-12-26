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
package com.skydoves.landscapist.image

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.skydoves.landscapist.ImageLoad
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.StableHolder
import com.skydoves.landscapist.components.ComposeFailureStatePlugins
import com.skydoves.landscapist.components.ComposeLoadingStatePlugins
import com.skydoves.landscapist.components.ComposeSuccessStatePlugins
import com.skydoves.landscapist.components.ComposeWithComposablePlugins
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.constraints.Constrainable
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.channelFlow

/**
 * Loads and displays an image using the Landscapist core image loading engine.
 *
 * @param imageModel A lambda that returns the image model to load (URL, Uri, etc).
 * @param modifier The modifier to apply to this layout.
 * @param landscapist The Landscapist instance to use for loading. Defaults to composition local.
 * @param requestBuilder Optional builder for customizing the image request.
 * @param component The image component with plugins to use.
 * @param imageOptions Image display options.
 * @param onImageStateChanged Callback invoked when the image state changes.
 * @param loading Composable to display while loading.
 * @param success Composable to display on successful load.
 * @param failure Composable to display on load failure.
 */
@Composable
public fun LandscapistImage(
  imageModel: () -> Any?,
  modifier: Modifier = Modifier,
  landscapist: Landscapist = getLandscapist(),
  requestBuilder: (ImageRequest.Builder.() -> Unit)? = null,
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  onImageStateChanged: ((LandscapistImageState) -> Unit)? = null,
  loading: @Composable (BoxScope.(LandscapistImageState.Loading) -> Unit)? = null,
  success: @Composable (BoxScope.(LandscapistImageState.Success, Painter) -> Unit)? = null,
  failure: @Composable (BoxScope.(LandscapistImageState.Failure) -> Unit)? = null,
) {
  val model = imageModel()
  val request = remember(model, requestBuilder) {
    ImageRequest.builder().apply {
      model(model)
      requestBuilder?.invoke(this)
    }.build()
  }

  LandscapistImageInternal(
    request = StableHolder(request),
    landscapist = StableHolder(landscapist),
    modifier = modifier,
    component = component,
    imageOptions = imageOptions,
  ) { imageResult ->
    val landscapistState = imageResult.toLandscapistImageState()
    onImageStateChanged?.invoke(landscapistState)

    when (val state = landscapistState) {
      is LandscapistImageState.None,
      is LandscapistImageState.Loading,
      -> {
        component.ComposeLoadingStatePlugins(
          modifier = Modifier,
          imageOptions = imageOptions,
          executor = { _ ->
            loading?.invoke(this, LandscapistImageState.Loading)
          },
        )
      }

      is LandscapistImageState.Success -> {
        val boxScope = this
        val painter = rememberLandscapistPainter(state.data)
        val imageBitmap = remember(state.data) {
          state.data?.let { convertToImageBitmap(it) }
        }

        component.ComposeSuccessStatePlugins(
          modifier = Modifier,
          imageModel = model,
          imageOptions = imageOptions,
          imageBitmap = imageBitmap,
        )

        // Provide source data for sub-sampling support (zoomable plugin)
        ProvideImageSource(
          diskCachePath = state.diskCachePath,
          rawData = state.rawData,
        ) {
          component.ComposeWithComposablePlugins {
            if (success != null) {
              success.invoke(boxScope, state, painter)
            } else {
              DefaultSuccessContent(
                painter = painter,
                imageOptions = imageOptions,
              )
            }
          }
        }
      }

      is LandscapistImageState.Failure -> {
        component.ComposeFailureStatePlugins(
          modifier = Modifier,
          imageOptions = imageOptions,
          reason = state.reason,
        )

        failure?.invoke(this, state)
      }
    }
  }
}

/**
 * Internal implementation that uses ImageResult directly to preserve rawData and diskCachePath.
 */
@Composable
private fun LandscapistImageInternal(
  request: StableHolder<ImageRequest>,
  landscapist: StableHolder<Landscapist>,
  modifier: Modifier,
  component: ImageComponent,
  imageOptions: ImageOptions,
  content: @Composable BoxWithConstraintsScope.(imageResult: ImageResult) -> Unit,
) {
  val constraintsSizeResolver = remember { ConstraintsSizeResolver() }

  // Build request with target size from constraints or imageOptions
  val sizedRequest = remember(request.value, imageOptions) {
    val originalRequest = request.value
    if (originalRequest.targetWidth != null && originalRequest.targetHeight != null) {
      // Request already has size set
      originalRequest
    } else if (imageOptions.isValidSize) {
      // Use size from imageOptions
      ImageRequest.builder().apply {
        model(originalRequest.model)
        memoryCachePolicy(originalRequest.memoryCachePolicy)
        diskCachePolicy(originalRequest.diskCachePolicy)
        headers(originalRequest.headers)
        transformations(originalRequest.transformations)
        tag(originalRequest.tag)
        size(imageOptions.requestSize.width, imageOptions.requestSize.height)
      }.build()
    } else {
      originalRequest
    }
  }

  ImageLoad(
    recomposeKey = StableHolder(sizedRequest),
    executeImageRequest = {
      channelFlow {
        // Wait for constraints if we don't have a target size yet
        val finalRequest = if (
          sizedRequest.targetWidth == null || sizedRequest.targetHeight == null
        ) {
          // Use constraints if available
          val targetSize = constraintsSizeResolver.getTargetSize()
          if (targetSize != null && (targetSize.first > 0 || targetSize.second > 0)) {
            ImageRequest.builder().apply {
              model(sizedRequest.model)
              memoryCachePolicy(sizedRequest.memoryCachePolicy)
              diskCachePolicy(sizedRequest.diskCachePolicy)
              headers(sizedRequest.headers)
              transformations(sizedRequest.transformations)
              tag(sizedRequest.tag)
              if (targetSize.first > 0 && targetSize.second > 0) {
                size(targetSize.first, targetSize.second)
              }
            }.build()
          } else {
            sizedRequest
          }
        } else {
          sizedRequest
        }

        landscapist.value.load(finalRequest).collect { result ->
          send(result.toImageLoadState())
        }
      }
    },
    modifier = modifier,
    imageOptions = imageOptions,
    constrainable = constraintsSizeResolver,
  ) { imageLoadState ->
    // Convert back to ImageResult to access all properties
    val imageResult = imageLoadState.toImageResult()
    content(imageResult)
  }
}

/**
 * Converts [ImageResult] to [ImageLoadState].
 * Note: We wrap the data to preserve additional fields for ImageLoad compatibility.
 */
private fun ImageResult.toImageLoadState(): ImageLoadState = when (this) {
  is ImageResult.Loading -> ImageLoadState.Loading
  is ImageResult.Success -> ImageLoadState.Success(
    data = LandscapistSuccessData(
      bitmap = data,
      originalWidth = originalWidth,
      originalHeight = originalHeight,
      rawData = rawData,
      diskCachePath = diskCachePath,
    ),
    dataSource = com.skydoves.landscapist.DataSource.valueOf(dataSource.name),
  )
  is ImageResult.Failure -> ImageLoadState.Failure(
    data = null,
    reason = throwable,
  )
}

/**
 * Converts [ImageLoadState] back to [ImageResult].
 */
private fun ImageLoadState.toImageResult(): ImageResult = when (this) {
  is ImageLoadState.None -> ImageResult.Loading
  is ImageLoadState.Loading -> ImageResult.Loading
  is ImageLoadState.Success -> {
    val successData = data as? LandscapistSuccessData
    ImageResult.Success(
      data = successData?.bitmap ?: data ?: Unit,
      dataSource = com.skydoves.landscapist.core.model.DataSource.valueOf(dataSource.name),
      originalWidth = successData?.originalWidth ?: 0,
      originalHeight = successData?.originalHeight ?: 0,
      rawData = successData?.rawData,
      diskCachePath = successData?.diskCachePath,
    )
  }
  is ImageLoadState.Failure -> ImageResult.Failure(
    throwable = reason,
  )
}

/**
 * Wrapper to preserve additional data through ImageLoadState conversion.
 */
private data class LandscapistSuccessData(
  val bitmap: Any,
  val originalWidth: Int,
  val originalHeight: Int,
  val rawData: ByteArray?,
  val diskCachePath: String?,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as LandscapistSuccessData

    if (bitmap != other.bitmap) return false
    if (originalWidth != other.originalWidth) return false
    if (originalHeight != other.originalHeight) return false
    if (rawData != null) {
      if (other.rawData == null) return false
      if (!rawData.contentEquals(other.rawData)) return false
    } else if (other.rawData != null) return false
    if (diskCachePath != other.diskCachePath) return false

    return true
  }

  override fun hashCode(): Int {
    var result = bitmap.hashCode()
    result = 31 * result + originalWidth
    result = 31 * result + originalHeight
    result = 31 * result + (rawData?.contentHashCode() ?: 0)
    result = 31 * result + (diskCachePath?.hashCode() ?: 0)
    return result
  }
}

/**
 * Size resolver for constraints.
 * Stores constraints from BoxWithConstraints and provides target size for image requests.
 */
internal class ConstraintsSizeResolver : Constrainable {
  private val _constraints = kotlinx.coroutines.flow.MutableStateFlow(
    androidx.compose.ui.unit.Constraints.fixed(0, 0),
  )

  /**
   * Returns the current constraints.
   */
  val constraints: androidx.compose.ui.unit.Constraints
    get() = _constraints.value

  /**
   * Returns true if valid constraints have been set.
   */
  val hasValidConstraints: Boolean
    get() = !_constraints.value.isZero

  /**
   * Returns the target size if constraints are already available, otherwise null.
   */
  fun getTargetSize(): Pair<Int, Int>? {
    val c = _constraints.value
    if (c.isZero) return null
    return if (c.hasBoundedWidth || c.hasBoundedHeight) {
      Pair(
        if (c.hasBoundedWidth) c.maxWidth else 0,
        if (c.hasBoundedHeight) c.maxHeight else 0,
      )
    } else {
      null
    }
  }

  override fun setConstraints(constraints: androidx.compose.ui.unit.Constraints) {
    _constraints.value = constraints
  }
}
