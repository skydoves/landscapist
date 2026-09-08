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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
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
import com.skydoves.landscapist.crossfade.rememberCrossfadePainter
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.plugins.composePainterPlugins
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.skydoves.landscapist.DataSource as PublicDataSource
import com.skydoves.landscapist.core.model.DataSource as CoreDataSource

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
  imageOptions: ImageOptions = ImageOptions.Default,
  onImageStateChanged: ((LandscapistImageState) -> Unit)? = null,
  loading: @Composable (BoxScope.(LandscapistImageState.Loading) -> Unit)? = null,
  success: @Composable (BoxScope.(LandscapistImageState.Success, Painter) -> Unit)? = null,
  failure: @Composable (BoxScope.(LandscapistImageState.Failure) -> Unit)? = null,
) {
  val model = imageModel()

  // Fast path: If model is already a Bitmap or ImageBitmap, render directly without loading pipeline
  if (isBitmapType(model)) {
    LandscapistImageFastPath(
      bitmap = model,
      modifier = modifier,
      component = component,
      imageOptions = imageOptions,
      onImageStateChanged = onImageStateChanged,
      success = success,
    )
    return
  }

  val request = remember(model, requestBuilder) {
    ImageRequest.builder().apply {
      model(model)
      requestBuilder?.invoke(this)
    }.build()
  }

  // Scanned every composition rather than remembered: an ImagePluginComponent is mutable and has
  // no equality, so remembering it would freeze a plugin set the caller can still add to.
  val plugins = component.imagePlugins
  // Nothing to compose inside: one layout node measures, draws and runs its own load, so the size
  // to decode at is read while measuring rather than written back as state, and a resolved image
  // invalidates one node's draw rather than recomposing. A DrawableResource is excluded because
  // painterResource is composable.
  val canOwnNode = success == null && loading == null && failure == null &&
    plugins.isEmpty() && model !is DrawableResource
  if (canOwnNode) {
    // Set by the node when the data needs a composed painter, an animated drawable being the case
    // that matters, so this hands it back to the composed path. Only Android can produce one.
    val composedPainter = if (ComposedPainterEverNeeded) {
      remember(request) { mutableStateOf(false) }
    } else {
      null
    }
    if (composedPainter?.value != true) {
      val ratio = imageOptions.placeholderAspectRatio
      Layout(
        modifier = (if (ratio != null && ratio > 0f) modifier.aspectRatio(ratio) else modifier)
          .imageSemantics(imageOptions)
          .landscapistImageNode(
            landscapist = landscapist,
            request = request,
            imageOptions = imageOptions,
            onState = onImageStateChanged,
            unpaintable = composedPainter,
          ),
        measurePolicy = LandscapistImageMeasurePolicy,
      )
      return
    }
  }

  val crossfadePlugin = plugins.firstInstanceOrNull<CrossfadePlugin>()
  // Only a SuccessStatePlugin is handed the decoded pixels; a painter plugin gets a function to
  // call instead, since it usually ignores them.
  val needsImageBitmap = plugins.anyIs<ImagePlugin.SuccessStatePlugin>()
  // Only content composed inside success can read the source locals, so providing them otherwise
  // costs a composition local per image. Decided from the plugin set rather than from the source
  // itself, so the success subtree never moves between groups when a disk path appears.
  val hasComposablePlugin = plugins.anyIs<ImagePlugin.ComposablePlugin>()
  val providesImageSource = success != null || hasComposablePlugin
  // The container can fade the painter itself when there was no loading or failure content to fade
  // out from. Anything that has some still stacks the two states in CrossfadeWithEffect.
  val fadesWhilePainting = crossfadePlugin != null &&
    loading == null &&
    failure == null &&
    !plugins.anyIs<ImagePlugin.LoadingStatePlugin>() &&
    !plugins.anyIs<ImagePlugin.FailureStatePlugin>()
  // The container can draw the image itself only when nothing else claims the inside of it.
  val canPaintOnContainer = success == null &&
    (crossfadePlugin == null || fadesWhilePainting) &&
    !hasComposablePlugin &&
    !plugins.anyIs<ImagePlugin.SuccessStatePlugin>()
  val requestHolder = remember(request) { StableHolder(request) }
  val landscapistHolder = remember(landscapist) { StableHolder(landscapist) }

  LandscapistImageInternal(
    request = requestHolder,
    landscapist = landscapistHolder,
    modifier = modifier,
    component = component,
    imageOptions = imageOptions,
    onState = { onImageStateChanged?.invoke(it) },
    // Anything that has to be composed inside needs a real child.
    paintOnContainer = canPaintOnContainer,
    needsImageBitmap = needsImageBitmap,
    containerFadeMs = if (fadesWhilePainting && canPaintOnContainer) {
      crossfadePlugin?.duration ?: 0
    } else {
      0
    },
  ) { landscapistState ->
    // Wrap with CrossfadeWithEffect when CrossfadePlugin is present
    CrossfadeWithEffect(
      targetState = landscapistState,
      durationMs = crossfadePlugin?.duration ?: 0,
      contentKey = { it.crossfadeKey() },
      // Off only when the container is actually doing the fading, not merely when it could.
      enabled = crossfadePlugin != null && !(fadesWhilePainting && canPaintOnContainer),
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
                recomposeKey = requestHolder,
                landscapist = landscapistHolder,
                imageOptions = imageOptions,
              )
            },
          )
          loading?.invoke(this, LandscapistImageState.Loading)
        }

        is LandscapistImageState.Success -> {
          val imageBitmap = pluginImageBitmap(state, needsImageBitmap)
          val painter = rememberSuccessPainter(state, component, imageBitmap)

          if (needsImageBitmap) {
            component.ComposeSuccessStatePlugins(
              modifier = Modifier,
              imageModel = model,
              imageOptions = imageOptions,
              imageBitmap = imageBitmap,
            )
          }

          if (providesImageSource) {
            // One provider rather than nested composables: each is a composition group per image.
            CompositionLocalProvider(
              imageSourceProvidedValue(
                diskCachePath = state.diskCachePath,
                rawData = state.rawData,
              ),
            ) {
              // With nothing to wrap it would be one more composition group per image.
              if (hasComposablePlugin) {
                component.ComposeWithComposablePlugins {
                  successContent(success, state, painter, imageOptions)
                }
              } else {
                successContent(success, state, painter, imageOptions)
              }
            }
          } else {
            successContent(success, state, painter, imageOptions)
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
 * Fast path for pre-decoded Bitmap/ImageBitmap inputs.
 * Skips the entire loading pipeline (Flow, coroutines, state transitions) for maximum performance.
 * This makes Landscapist competitive with direct Coil3 for bitmap pass-through scenarios.
 */
@Composable
private fun LandscapistImageFastPath(
  bitmap: Any?,
  modifier: Modifier,
  component: ImageComponent,
  imageOptions: ImageOptions,
  onImageStateChanged: ((LandscapistImageState) -> Unit)?,
  success: @Composable (BoxScope.(LandscapistImageState.Success, Painter) -> Unit)?,
) {
  // Create success state immediately - no loading state needed for pre-decoded bitmaps
  val successState = remember(bitmap) {
    LandscapistImageState.Success(
      data = bitmap,
      dataSource = com.skydoves.landscapist.core.model.DataSource.MEMORY,
      originalWidth = getBitmapWidth(bitmap),
      originalHeight = getBitmapHeight(bitmap),
      rawData = null,
      diskCachePath = null,
    )
  }

  // Notify state change immediately
  LaunchedEffect(successState) {
    onImageStateChanged?.invoke(successState)
  }

  // Render directly without CrossfadeWithEffect for fast path (bitmap is already available)
  val painter = rememberLandscapistPainter(bitmap)
  val imageBitmap = remember(bitmap) {
    bitmap?.let { convertToImageBitmap(it) }
  }

  // Apply painter plugins if needed
  val finalPainter = if (imageBitmap != null) {
    painter.composePainterPlugins(
      imagePlugins = component.imagePlugins,
      imageBitmap = { imageBitmap },
    )
  } else {
    painter
  }

  component.ComposeSuccessStatePlugins(
    modifier = Modifier,
    imageModel = bitmap,
    imageOptions = imageOptions,
    imageBitmap = imageBitmap,
  )

  Box(
    modifier = modifier.imageSemantics(imageOptions),
  ) {
    component.ComposeWithComposablePlugins {
      if (success != null) {
        success.invoke(this, successState, finalPainter)
      } else {
        DefaultSuccessContent(
          modifier = Modifier.fillMaxSize(),
          painter = finalPainter,
          imageOptions = imageOptions,
        )
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
  onState: (LandscapistImageState) -> Unit,
  paintOnContainer: Boolean,
  needsImageBitmap: Boolean,
  containerFadeMs: Int,
  content: @Composable BoxScope.(state: LandscapistImageState) -> Unit,
) {
  val loadingKey = imageOptions.loadingOptionsKey

  // Read the memory cache during composition so an image that is already decoded is drawn in the
  // very first frame. Waiting for the flow costs a frame of empty content even on a cache hit,
  // which is what makes images blink when a composable enters, most visibly when a shared element
  // transition animates the bounds of what is still an empty box.
  var state by remember(request, loadingKey) {
    val cached = landscapist.value.peekMemoryCache(request.value)
    mutableStateOf(cached?.toImageLoadState() ?: ImageLoadState.None)
  }

  // Packed into one state: both axes are written together, once, and locked afterwards so a later
  // constraint change does not restart the load.
  var incomingConstraints by remember { mutableLongStateOf(NOT_MEASURED) }
  val hasMeasured = incomingConstraints != NOT_MEASURED
  val incomingMaxWidth = (incomingConstraints ushr 32).toInt()
  val incomingMaxHeight = (incomingConstraints and 0xFFFFFFFFL).toInt()

  // Keyed on the measured bounds so the request is built once the first measurement lands.
  val sizedRequest = remember(request.value, imageOptions, incomingConstraints) {
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

  // Start loading once we have a sized request and measurement is done (or request has size already).
  // The key includes sizedRequest so if the model changes, loading restarts with proper size.
  val canLoad = hasMeasured ||
    (request.value.targetWidth != null && request.value.targetHeight != null) ||
    imageOptions.isValidSize

  if (canLoad) {
    LaunchedEffect(sizedRequest, loadingKey) {
      executeImageLoading(landscapist.value, sizedRequest).collect { next ->
        // Once measurement lands, the request restarts at its real target size. Dropping back to a
        // loading state would blink away an image the user can already see, so an image on screen
        // holds until the resized one resolves.
        if (next is ImageLoadState.Loading && state is ImageLoadState.Success) return@collect
        state = next
      }
    }
  }

  // Converting allocates, and the loader's state only changes when the flow emits.
  val landscapistState = state.toLandscapistImageState()
  // After the composition and only on a change, so a caller that writes state from it does not
  // recompose this image into calling it again. A SideEffect, since no coroutine is needed.
  val dispatched = remember { arrayOfNulls<LandscapistImageState>(1) }
  SideEffect {
    if (dispatched[0] != landscapistState) {
      dispatched[0] = landscapistState
      onState(landscapistState)
    }
  }
  // Drawn by this node rather than a child, which is one layout node per image rather than two.
  val loaded = if (paintOnContainer && landscapistState is LandscapistImageState.Success) {
    rememberSuccessPainter(
      landscapistState,
      component,
      pluginImageBitmap(landscapistState, needsImageBitmap),
    )
  } else {
    null
  }
  // Called with or without a painter, so what it remembers survives the image leaving success.
  val painter = rememberCrossfadePainter(loaded, containerFadeMs)
  // Rebuilding the chain every composition allocates two modifier elements per image.
  val paintModifier = remember(painter, imageOptions) {
    if (painter != null) imageOptions.paintModifier(painter) else Modifier
  }

  // Modifier.layout reads what the parent offered, unlike onSizeChanged which reports what was
  // rendered, so a bounded width is known while the content is still empty. Dropped once the value
  // is locked, since a node that stayed would measure every frame to compute nothing.
  val constraintProbe = if (hasMeasured) {
    Modifier
  } else {
    Modifier.layout { measurable, constraints ->
      if (incomingConstraints == NOT_MEASURED) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        incomingConstraints = (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)
      }
      val placeable = measurable.measure(constraints)
      layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
      }
    }
  }

  Box(
    modifier = baseModifier
      .imageSemantics(imageOptions)
      // The probe reads what the parent offered, so it has to sit outside the fill and the paint:
      // those hand their own constraints down, and the decode target size would follow them
      // instead of the layout.
      .then(constraintProbe)
      .then(paintModifier),
    propagateMinConstraints = true,
  ) {
    if (painter == null) {
      content(landscapistState)
    }
  }
}

/**
 * Builds an ImageRequest with appropriate target size based on constraints.
 * Uses constraints to downsample large images and prevent memory issues.
 */
internal fun buildSizedRequest(
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
    return originalRequest.copy(
      targetWidth = imageOptions.requestSize.width,
      targetHeight = imageOptions.requestSize.height,
    )
  }

  // Calculate target size from constraints
  val targetWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else null
  val targetHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else null

  // If we have at least one bounded dimension, use it for downsampling.
  // For unbounded dimensions, use Int.MAX_VALUE to signal "no constraint" while still
  // passing the decoder's > 0 check. The decoder will maintain aspect ratio.
  if (targetWidth != null || targetHeight != null) {
    return originalRequest.copy(
      targetWidth = targetWidth ?: Int.MAX_VALUE,
      targetHeight = targetHeight ?: Int.MAX_VALUE,
    )
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
  // No loading state is emitted up front: Landscapist.load emits one only when the image is not
  // already in memory, so a cached image never passes through one.

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
}.distinctUntilChanged()

/**
 * The decoded pixels, but only when a plugin actually reads them.
 *
 * Materialising this can mean a second full decode on the skia targets, so the common case of no
 * such plugin never pays for it.
 */
@Composable
private inline fun pluginImageBitmap(
  state: LandscapistImageState.Success,
  needsImageBitmap: Boolean,
): ImageBitmap? = remember(state.data, needsImageBitmap) {
  if (needsImageBitmap) state.data?.let { convertToImageBitmap(it) } else null
}

/**
 * The painter for a loaded image, with any painter plugins already applied.
 *
 * [decoded] is the bitmap a success state plugin was already given, or null when none asked for
 * one. Either way the pixels are only materialised if a painter plugin turns out to want them:
 * composePainterPlugins returns before it calls for them when there is no painter plugin at all,
 * and converting is a full pixel copy on the platforms where the decoder does not hand back
 * something the painter can draw directly.
 */
@Composable
private fun rememberSuccessPainter(
  state: LandscapistImageState.Success,
  component: ImageComponent,
  decoded: ImageBitmap?,
): Painter {
  val basePainter = if (state.data is DrawableResource) {
    painterResource(state.data)
  } else {
    rememberLandscapistPainter(state.data)
  }
  // Composing plugins onto the painter is a composable call, so with none installed it is a
  // composition group per image that hands back the painter it was given.
  if (!component.imagePlugins.anyIs<ImagePlugin.PainterPlugin>()) return basePainter
  return basePainter.composePainterPlugins(
    imagePlugins = component.imagePlugins,
    imageBitmap = {
      decoded ?: pluginImageBitmap(state, needsImageBitmap = true) ?: EmptyImageBitmap
    },
  )
}

/**
 * The first plugin of type [T], scanned by index.
 *
 * These run on every composition of every image, and `filterIsInstance` and `firstOrNull` each
 * allocate a list to answer a question about a list that usually holds nothing.
 */
private inline fun <reified T> List<ImagePlugin>.firstInstanceOrNull(): T? {
  for (index in indices) {
    val plugin = this[index]
    if (plugin is T) return plugin
  }
  return null
}

/** Whether any plugin is a [T]. */
private inline fun <reified T> List<ImagePlugin>.anyIs(): Boolean {
  for (index in indices) {
    if (this[index] is T) return true
  }
  return false
}

/** Stands in for a plugin that was handed pixels it never reads. */
private val EmptyImageBitmap: ImageBitmap by lazy { ImageBitmap(1, 1) }

/**
 * Converts the loader's state straight to the state the caller sees.
 *
 * Going through [ImageResult] on the way allocated a second object on every composition of every
 * image, and nothing in between needed it. [ImageLoadState.None] still surfaces as
 * [LandscapistImageState.Loading]: they render the same, and callers have never seen None here.
 */
private fun ImageLoadState.toLandscapistImageState(): LandscapistImageState = when (this) {
  is ImageLoadState.None, is ImageLoadState.Loading -> LandscapistImageState.Loading
  is ImageLoadState.Success -> {
    val successData = data as? LandscapistSuccessData
    LandscapistImageState.Success(
      data = successData?.bitmap ?: data ?: Unit,
      dataSource = dataSource.toCoreDataSource(),
      originalWidth = successData?.originalWidth ?: 0,
      originalHeight = successData?.originalHeight ?: 0,
      rawData = successData?.rawData,
      diskCachePath = successData?.diskCachePath,
    )
  }
  is ImageLoadState.Failure -> LandscapistImageState.Failure(reason = reason)
}

/**
 * Draws [painter] on the node this modifies, the same way [androidx.compose.foundation.Image] does.
 */
private fun ImageOptions.paintModifier(painter: Painter): Modifier = Modifier
  // The fill comes first, because Modifier.paint sizes the node to the painter unless given fixed
  // constraints, and without it a caller with no size modifier shrinks to the decoded image.
  // Anything but a plain bitmap painter may animate as it draws and needs a layer of its own.
  .fillAndClip(ownLayer = painter !is BitmapPainter)
  .paint(
    painter = painter,
    alignment = alignment,
    contentScale = contentScale,
    alpha = alpha,
    colorFilter = colorFilter,
  )

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
    dataSource = dataSource.toLandscapistDataSource(),
  )
  is ImageResult.Failure -> ImageLoadState.Failure(
    data = null,
    reason = throwable,
  )
}

/**
 * What identifies this state to the crossfade.
 *
 * Keying on the state itself means `key()` hashes it every composition, and a success state hashes
 * the encoded image with it. The decoded image is what the crossfade actually distinguishes.
 */
private fun LandscapistImageState.crossfadeKey(): Any = when (this) {
  is LandscapistImageState.Success -> data ?: this
  else -> this
}

// Enum.valueOf goes through a name lookup, and these conversions run on every composition. A when
// is a table switch.
private fun CoreDataSource.toLandscapistDataSource(): PublicDataSource = when (this) {
  CoreDataSource.MEMORY -> PublicDataSource.MEMORY
  CoreDataSource.DISK -> PublicDataSource.DISK
  CoreDataSource.NETWORK -> PublicDataSource.NETWORK
  CoreDataSource.LOCAL -> PublicDataSource.LOCAL
  CoreDataSource.RESOURCE -> PublicDataSource.RESOURCE
  CoreDataSource.INLINE -> PublicDataSource.INLINE
  CoreDataSource.UNKNOWN -> PublicDataSource.UNKNOWN
}

private fun PublicDataSource.toCoreDataSource(): CoreDataSource = when (this) {
  PublicDataSource.MEMORY -> CoreDataSource.MEMORY
  PublicDataSource.DISK -> CoreDataSource.DISK
  PublicDataSource.NETWORK -> CoreDataSource.NETWORK
  PublicDataSource.LOCAL -> CoreDataSource.LOCAL
  PublicDataSource.RESOURCE -> CoreDataSource.RESOURCE
  PublicDataSource.INLINE -> CoreDataSource.INLINE
  PublicDataSource.UNKNOWN -> CoreDataSource.UNKNOWN
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
      if (rawData !== other.rawData) return false
    } else if (other.rawData != null) return false
    if (diskCachePath != other.diskCachePath) return false

    return true
  }

  override fun hashCode(): Int {
    var result = bitmap.hashCode()
    result = 31 * result + originalWidth
    result = 31 * result + originalHeight
    result = 31 * result + (rawData?.size ?: 0)
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
    onState = {},
    paintOnContainer = false,
    needsImageBitmap = false,
    containerFadeMs = 0,
  ) { state ->
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

/**
 * The caller's success slot, or the image drawn as it would be with no slot at all.
 *
 * Inline, so the slot is composed in the group the image already has rather than in one of its own.
 */
@Composable
private inline fun BoxScope.successContent(
  noinline success: @Composable (BoxScope.(LandscapistImageState.Success, Painter) -> Unit)?,
  state: LandscapistImageState.Success,
  painter: Painter,
  imageOptions: ImageOptions,
) {
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

/** The packed constraints before any layout pass has run. Neither axis can be negative once set. */
private const val NOT_MEASURED = -1L
