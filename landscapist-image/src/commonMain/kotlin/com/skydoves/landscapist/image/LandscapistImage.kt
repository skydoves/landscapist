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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.InternalLandscapistApi
import com.skydoves.landscapist.LandscapistImage
import com.skydoves.landscapist.StableHolder
import com.skydoves.landscapist.components.ComposeFailureStatePlugins
import com.skydoves.landscapist.components.ComposeLoadingStatePlugins
import com.skydoves.landscapist.components.ComposeSuccessStatePlugins
import com.skydoves.landscapist.components.ComposeWithComposablePlugins
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.imagePlugins
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.crossfade.CrossfadeWithEffect
import com.skydoves.landscapist.plugins.composePainterPlugins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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

  // Check for CrossfadePlugin to enable crossfade animation
  val crossfadePlugin = component.imagePlugins.filterIsInstance<CrossfadePlugin>().firstOrNull()

  LandscapistImageInternal(
    request = StableHolder(request),
    landscapist = StableHolder(landscapist),
    modifier = modifier,
    component = component,
    imageOptions = imageOptions,
  ) { imageResult ->
    val landscapistState = imageResult.toLandscapistImageState()
    onImageStateChanged?.invoke(landscapistState)

    // Wrap with CrossfadeWithEffect when CrossfadePlugin is present
    CrossfadeWithEffect(
      targetState = landscapistState,
      durationMs = crossfadePlugin?.duration ?: 0,
      contentKey = { it },
      enabled = crossfadePlugin != null,
    ) { state ->
      when (state) {
        is LandscapistImageState.None,
        is LandscapistImageState.Loading,
        -> {
          component.ComposeLoadingStatePlugins(
            modifier = Modifier.fillMaxSize(),
            imageOptions = imageOptions,
            executor = { size ->
              LandscapistThumbnail(
                requestSize = size,
                recomposeKey = StableHolder(request),
                landscapist = StableHolder(landscapist),
                imageOptions = imageOptions,
              )
            },
          )
          loading?.invoke(this, LandscapistImageState.Loading)
        }

        is LandscapistImageState.Success -> {
          val basePainter = if (state.data is DrawableResource) {
            painterResource(state.data)
          } else {
            rememberLandscapistPainter(state.data)
          }
          val imageBitmap = remember(state.data) {
            state.data?.let { convertToImageBitmap(it) }
          }

          // Apply PainterPlugins (like CircularRevealPlugin) to the painter
          val painter = if (imageBitmap != null) {
            basePainter.composePainterPlugins(
              imagePlugins = component.imagePlugins,
              imageBitmap = imageBitmap,
            )
          } else {
            basePainter
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
                success.invoke(this, state, painter)
              } else {
                DefaultSuccessContent(
                  modifier = Modifier.fillMaxSize(),
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
}

/**
 * Internal implementation that uses ImageResult directly to preserve rawData and diskCachePath.
 * Uses Modifier.layout to capture incoming parent constraints for downsampling, then starts loading.
 * This avoids SubcomposeLayout overhead while still providing proper downsampling.
 */
@Composable
private fun LandscapistImageInternal(
  request: StableHolder<ImageRequest>,
  landscapist: StableHolder<Landscapist>,
  modifier: Modifier,
  component: ImageComponent,
  imageOptions: ImageOptions,
  content: @Composable BoxScope.(imageResult: ImageResult) -> Unit,
) {
  val loadingKey = imageOptions.loadingOptionsKey

  var state by remember(request, loadingKey) {
    mutableStateOf<ImageLoadState>(ImageLoadState.None)
  }

  // Capture incoming parent constraints for downsampling. Initialized to -1 meaning "not yet measured".
  // Once measured, the value is locked to prevent LaunchedEffect restarts.
  var incomingMaxWidth by remember { mutableIntStateOf(-1) }
  var incomingMaxHeight by remember { mutableIntStateOf(-1) }
  val hasMeasured = incomingMaxWidth >= 0

  // Auto-calculate aspect ratio from loaded image dimensions for sub-sampling support.
  // Priority: explicit placeholderAspectRatio > auto from loaded image > none
  val autoAspectRatio = remember(state) {
    val successData = (state as? ImageLoadState.Success)?.data as? LandscapistSuccessData
    if (successData != null && successData.originalWidth > 0 && successData.originalHeight > 0) {
      successData.originalWidth.toFloat() / successData.originalHeight.toFloat()
    } else {
      null
    }
  }
  // Only apply auto aspect ratio when incoming height is unbounded (e.g., scrollable Column).
  // In bounded contexts (e.g., .size(50.dp)), the parent already provides proper constraints.
  val needsAutoAspectRatio = hasMeasured && incomingMaxHeight == 0
  val effectiveAspectRatio = imageOptions.placeholderAspectRatio
    ?: if (needsAutoAspectRatio) autoAspectRatio else null

  // Apply aspect ratio modifier if available to reserve space / ensure bounded height
  val baseModifier = remember(modifier, effectiveAspectRatio) {
    if (effectiveAspectRatio != null && effectiveAspectRatio > 0f) {
      modifier.aspectRatio(effectiveAspectRatio)
    } else {
      modifier
    }
  }

  // Build request with target size from captured layout dimensions.
  // The remember keys include incomingMaxWidth/incomingMaxHeight so the request is built
  // once the first measurement happens. After that, size changes won't rebuild.
  val sizedRequest = remember(request.value, imageOptions, incomingMaxWidth, incomingMaxHeight) {
    val constraints = if (hasMeasured) {
      Constraints(
        maxWidth = if (incomingMaxWidth > 0) incomingMaxWidth else Constraints.Infinity,
        maxHeight = if (incomingMaxHeight > 0) incomingMaxHeight else Constraints.Infinity,
      )
    } else {
      Constraints() // unbounded fallback (should rarely happen)
    }
    buildSizedRequest(request.value, imageOptions, constraints)
  }

  // Start loading once we have a sized request and measurement is done (or request has size already).
  // The key includes sizedRequest so if the model changes, loading restarts with proper size.
  val canLoad = hasMeasured ||
    (request.value.targetWidth != null && request.value.targetHeight != null) ||
    imageOptions.isValidSize

  if (canLoad) {
    LaunchedEffect(sizedRequest, loadingKey) {
      executeImageLoading(landscapist.value, sizedRequest).collect {
        state = it
      }
    }
  }

  // Use Box with propagateMinConstraints + Modifier.layout to capture incoming parent constraints.
  // Unlike onSizeChanged (which reports actual rendered size), Modifier.layout reads the
  // incoming constraints from the parent, giving us bounded width even when content is empty.
  // This is critical for sub-sampling/zoomable plugins in unbounded contexts (e.g., scrollable Column).
  Box(
    modifier = baseModifier
      .imageSemantics(imageOptions)
      .layout { measurable, constraints ->
        if (incomingMaxWidth < 0) {
          incomingMaxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
          incomingMaxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        }
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
          placeable.placeRelative(0, 0)
        }
      },
    propagateMinConstraints = true,
  ) {
    val imageResult = state.toImageResult()
    content(imageResult)
  }
}

/**
 * Builds an ImageRequest with appropriate target size based on constraints.
 * Uses constraints to downsample large images and prevent memory issues.
 */
private fun buildSizedRequest(
  originalRequest: ImageRequest,
  imageOptions: ImageOptions,
  constraints: Constraints,
): ImageRequest {
  // If request already has size set, use it
  if (originalRequest.targetWidth != null && originalRequest.targetHeight != null) {
    return originalRequest
  }

  // If imageOptions has valid size, use it
  if (imageOptions.isValidSize) {
    return ImageRequest.builder().apply {
      model(originalRequest.model)
      memoryCachePolicy(originalRequest.memoryCachePolicy)
      diskCachePolicy(originalRequest.diskCachePolicy)
      headers(originalRequest.headers)
      transformations(originalRequest.transformations)
      tag(originalRequest.tag)
      size(imageOptions.requestSize.width, imageOptions.requestSize.height)
    }.build()
  }

  // Calculate target size from constraints
  val targetWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else null
  val targetHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else null

  // If we have at least one bounded dimension, use it for downsampling
  if (targetWidth != null || targetHeight != null) {
    return ImageRequest.builder().apply {
      model(originalRequest.model)
      memoryCachePolicy(originalRequest.memoryCachePolicy)
      diskCachePolicy(originalRequest.diskCachePolicy)
      headers(originalRequest.headers)
      transformations(originalRequest.transformations)
      tag(originalRequest.tag)
      // Use the bounded dimension(s) for sizing
      // For unbounded dimensions, use Int.MAX_VALUE to signal "no constraint" while still
      // passing the decoder's > 0 check. The decoder will maintain aspect ratio.
      size(targetWidth ?: Int.MAX_VALUE, targetHeight ?: Int.MAX_VALUE)
    }.build()
  }

  // No size constraints - load at original size (this should be rare)
  return originalRequest
}

/**
 * Executes image loading and emits states.
 */
private fun executeImageLoading(
  landscapist: Landscapist,
  request: ImageRequest,
) = flow {
  emit(ImageLoadState.Loading)

  // Handle DrawableResource from KMP Compose Resources directly.
  // DrawableResource is a local bundled resource that doesn't need network fetching or caching.
  if (request.model is DrawableResource) {
    emit(
      ImageLoadState.Success(
        data = request.model,
        dataSource = com.skydoves.landscapist.DataSource.RESOURCE,
      ),
    )
    return@flow
  }

  landscapist.load(request).collect { result ->
    emit(result.toImageLoadState())
  }
}.catch {
  emit(ImageLoadState.Failure(null, it))
}.distinctUntilChanged().flowOn(Dispatchers.Default)

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
 * Extension to add image semantics to a modifier.
 */
private fun Modifier.imageSemantics(imageOptions: ImageOptions): Modifier {
  return if (imageOptions.contentDescription != null) {
    this.semantics {
      contentDescription = imageOptions.contentDescription!!
      role = Role.Image
    }
  } else {
    this
  }
}

/**
 * A thumbnail composable used by loading state plugins to display a low-resolution
 * preview while the main image is loading.
 *
 * @param requestSize The target size for the thumbnail.
 * @param recomposeKey The image request to load.
 * @param landscapist The Landscapist instance to use for loading.
 * @param imageOptions Image display options.
 */
@OptIn(InternalLandscapistApi::class)
@Composable
private fun LandscapistThumbnail(
  requestSize: IntSize,
  recomposeKey: StableHolder<ImageRequest>,
  landscapist: StableHolder<Landscapist>,
  imageOptions: ImageOptions,
) {
  LandscapistImageInternal(
    request = recomposeKey,
    landscapist = landscapist,
    modifier = Modifier,
    component = rememberImageComponent {},
    imageOptions = imageOptions.copy(requestSize = requestSize),
  ) { imageResult ->
    val state = imageResult.toLandscapistImageState()
    if (state is LandscapistImageState.Success) {
      val data = state.data ?: return@LandscapistImageInternal
      val painter = rememberLandscapistPainter(data)
      imageOptions.LandscapistImage(
        modifier = Modifier,
        painter = painter,
      )
    }
  }
}
