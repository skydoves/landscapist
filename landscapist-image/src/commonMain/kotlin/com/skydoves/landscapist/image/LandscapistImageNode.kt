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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.times
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

/**
 * Loads and draws an image on the node it modifies.
 *
 * The whole image in one layout node: it reads the constraints it is measured with to pick the
 * size to decode at, runs the load, holds the painter and draws it. Nothing lives in composition,
 * so an image resolving invalidates this node's draw rather than recomposing.
 *
 * Only when there is nothing to compose inside the image: no caller slot, no plugin, no crossfade.
 * Everything else goes through [LandscapistImage]'s composed path.
 *
 * @param landscapist The loader to run the request against.
 * @param request The request to load, before a target size is known.
 * @param imageOptions How the image is drawn, and the size to ask for when the caller fixed one.
 * @param onState Called with each state the image reaches, in order.
 * @param unpaintable Set when the loaded data needs a composed painter, which this node cannot
 * build. The caller reads it and falls back to the composed path.
 */
internal fun Modifier.landscapistImageNode(
  landscapist: Landscapist,
  request: ImageRequest,
  imageOptions: ImageOptions,
  onState: ((LandscapistImageState) -> Unit)?,
  unpaintable: MutableState<Boolean>?,
): Modifier = this.then(
  LandscapistImageElement(landscapist, request, imageOptions, onState, unpaintable),
)

/**
 * Takes the size the node above it settled on.
 *
 * [LandscapistImageNode] fixes both axes while measuring, so there is nothing left to decide here.
 * A content-less layout is what `Image` and Coil's `AsyncImage` both use, and it costs one node.
 */
internal val LandscapistImageMeasurePolicy: MeasurePolicy = MeasurePolicy { _, constraints ->
  layout(constraints.minWidth, constraints.minHeight) {}
}

private data class LandscapistImageElement(
  val landscapist: Landscapist,
  val request: ImageRequest,
  val imageOptions: ImageOptions,
  val onState: ((LandscapistImageState) -> Unit)?,
  val unpaintable: MutableState<Boolean>?,
) : ModifierNodeElement<LandscapistImageNode>() {

  override fun create(): LandscapistImageNode = LandscapistImageNode(
    landscapist = landscapist,
    request = request,
    imageOptions = imageOptions,
    onState = onState,
    unpaintable = unpaintable,
  )

  override fun update(node: LandscapistImageNode) {
    node.update(landscapist, request, imageOptions, onState, unpaintable)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "landscapistImage"
    properties["model"] = request.model
    properties["imageOptions"] = imageOptions
  }
}

internal class LandscapistImageNode(
  private var landscapist: Landscapist,
  private var request: ImageRequest,
  private var imageOptions: ImageOptions,
  private var onState: ((LandscapistImageState) -> Unit)?,
  private var unpaintable: MutableState<Boolean>?,
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode {

  /**
   * What the node draws.
   *
   * Snapshot state, because the load can finish on any dispatcher and invalidating the node
   * directly from there touches the owner, which off the main thread throws on Android.
   */
  private var painter: Painter? by mutableStateOf(null)

  /** The last state handed to [onState], or null when none has been yet. */
  private var state: LandscapistImageState? = null
  private var started = false

  /** The load in flight, so a rebind can stop it. The node's scope only cancels on detach. */
  private var loadJob: Job? = null

  /**
   * Whether [request] still belongs to a previous binding.
   *
   * A lazy list resets a reused node and attaches it again before the new composition reaches it,
   * so reading the cache in that window would publish the previous row's image.
   */
  private var awaitingRequest = false

  override fun onAttach() {
    if (!awaitingRequest) peek()
  }

  override fun onDetach() {
    clear()
  }

  override fun onReset() {
    clear()
    awaitingRequest = true
  }

  /** Only if it is still running: `cancel` builds a `CancellationException` either way. */
  private fun cancelLoad() {
    val running = loadJob
    loadJob = null
    if (running != null && running.isActive) running.cancel()
  }

  /** Drops what was loaded, without asking a node that is on its way out to lay out again. */
  private fun clear() {
    cancelLoad()
    started = false
    painter = null
    state = null
  }

  fun update(
    landscapist: Landscapist,
    request: ImageRequest,
    imageOptions: ImageOptions,
    onState: ((LandscapistImageState) -> Unit)?,
    unpaintable: MutableState<Boolean>?,
  ) {
    // A node that was reset is holding nothing, so it reloads even when it is handed back the
    // request it already had.
    val rebound = awaitingRequest
    awaitingRequest = false
    val reload = rebound ||
      this.landscapist != landscapist ||
      this.request != request ||
      this.imageOptions.loadingOptionsKey != imageOptions.loadingOptionsKey
    this.landscapist = landscapist
    this.request = request
    this.imageOptions = imageOptions
    this.onState = onState
    this.unpaintable = unpaintable
    if (reload) {
      cancelLoad()
      started = false
      state = null
      // Takes the old image down in the same pass, rather than leaving it until the load returns.
      showPainter(null)
      peek()
    }
  }

  /** The synchronous cache read, so an image already in memory is drawn in the first frame. */
  private fun peek() {
    val cached = landscapist.peekMemoryCache(request)
    // An image with nothing cached has nothing to show yet, and callers have always been told so
    // before the load starts.
    publish(cached?.toLandscapistImageState() ?: LandscapistImageState.Loading)
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    // The parent's constraints are what the image is decoded against, and they are read here rather
    // than recorded into composition. A probe that wrote them back as state invalidated the
    // composition that had just measured it, which is a second composition and a second layout pass
    // for every image on the first frame.
    if (!started) {
      started = true
      startLoad(constraints)
    }
    val resolved = resolveConstraints(constraints)
    val placeable = measurable.measure(resolved)
    return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
  }

  private fun startLoad(constraints: Constraints) {
    val sized = buildSizedRequest(request, imageOptions, constraints.withoutZeroBounds())
    cancelLoad()
    loadJob = coroutineScope.launch {
      // The UI dispatcher, captured because the collector does not stay on it: a flow's collector
      // runs wherever the emission happens, and publishing hands state to the caller.
      val ui = coroutineContext[ContinuationInterceptor] ?: EmptyCoroutineContext

      // Collected directly rather than through flow {}, catch {} and distinctUntilChanged(), which
      // would each add a flow and a continuation per image for something already done here.
      try {
        landscapist.load(sized).collect { result ->
          val next = result.toLandscapistImageState()
          withContext(ui) { publish(next) }
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (throwable: Throwable) {
        withContext(ui) { publish(LandscapistImageState.Failure(reason = throwable)) }
      }
    }
  }

  private fun publish(next: LandscapistImageState) {
    if (next.isSameAs(state)) return
    // An image on screen holds until the resized request resolves, rather than blinking away.
    if (next is LandscapistImageState.Loading && state is LandscapistImageState.Success) return
    if (next is LandscapistImageState.Success) {
      val painted = landscapistPainterOrNull(next.data)
      val handOver = unpaintable
      if (painted == null && handOver != null) {
        // An animated drawable needs a composed painter, which only the composed path can give it.
        handOver.value = true
        return
      }
      // With nowhere to hand it to, an image with no painter draws nothing, which is what a
      // composed painter for an unrecognised type did too. The state still reaches the caller.
      showPainter(painted)
    } else {
      showPainter(null)
    }
    state = next
    onState?.invoke(next)
  }

  /**
   * Swaps the image in and invalidates what depends on it.
   *
   * Measurement as well as drawing, because an axis the parent left unbounded is sized from the
   * image itself, so the first one to arrive changes how much space this node takes.
   */
  private fun showPainter(next: Painter?) {
    if (painter == next) return
    painter = next
  }

  /**
   * The constraints the drawing is sized against.
   *
   * Takes every axis the parent bounded, which is what `fillMaxSize` does, and sizes an unbounded
   * one from the image, which is what `Modifier.paint` does. Together they are what the child
   * `Image` this replaces was measured with.
   */
  private fun resolveConstraints(constraints: Constraints): Constraints {
    val filled = Constraints(
      minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth,
      maxWidth = constraints.maxWidth,
      minHeight = if (constraints.hasBoundedHeight) {
        constraints.maxHeight
      } else {
        constraints.minHeight
      },
      maxHeight = constraints.maxHeight,
    )
    val painter = painter ?: return filled
    if (filled.hasFixedWidth && filled.hasFixedHeight) return filled
    val intrinsic = painter.intrinsicSize
    // One axis bounded and one not is a feed row: fillMaxWidth in a scrolling column. The open axis
    // is sized against the box the image would fill at its own shape, which is what the composable
    // this replaced did by applying an aspect ratio before it painted. Asking the content scale
    // about that box rather than about the intrinsic size is what keeps both ends right: a scale
    // that fills the row answers with the box, and one that does not upscale answers with the
    // image. Measuring against the intrinsic size instead degenerates, since the destination on
    // the open axis is then the number being computed: a crop measures the whole intrinsic height
    // and draws the image wider than the row, losing both edges.
    //
    // fitPrioritizingWidth rather than Constraints, which throws when the two axes together need
    // more than 31 bits. A hairline tall image at 200 wide asks for a height of four million, and
    // a layout that cannot hold it should clamp rather than bring the window down.
    if (intrinsic.hasFiniteWidth() && intrinsic.hasFiniteHeight() &&
      intrinsic.width > 0f && intrinsic.height > 0f
    ) {
      if (filled.hasFixedWidth && !filled.hasBoundedHeight) {
        val shaped =
          Size(filled.maxWidth.toFloat(), filled.maxWidth * intrinsic.height / intrinsic.width)
        val height = (intrinsic.height * scaleFactor(intrinsic, shaped).scaleY).roundToInt()
        return Constraints.fitPrioritizingWidth(
          minWidth = filled.minWidth,
          maxWidth = filled.maxWidth,
          minHeight = filled.constrainHeight(height),
          maxHeight = filled.maxHeight,
        )
      }
      if (filled.hasFixedHeight && !filled.hasBoundedWidth) {
        val shaped =
          Size(filled.maxHeight * intrinsic.width / intrinsic.height, filled.maxHeight.toFloat())
        val width = (intrinsic.width * scaleFactor(intrinsic, shaped).scaleX).roundToInt()
        return Constraints.fitPrioritizingHeight(
          minWidth = filled.constrainWidth(width),
          maxWidth = filled.maxWidth,
          minHeight = filled.minHeight,
          maxHeight = filled.maxHeight,
        )
      }
    }
    val intrinsicWidth = if (intrinsic.hasFiniteWidth()) {
      intrinsic.width.roundToInt()
    } else {
      filled.minWidth
    }
    val intrinsicHeight = if (intrinsic.hasFiniteHeight()) {
      intrinsic.height.roundToInt()
    } else {
      filled.minHeight
    }
    val width = filled.constrainWidth(intrinsicWidth)
    val height = filled.constrainHeight(intrinsicHeight)
    val scaled = scaledSize(intrinsic, Size(width.toFloat(), height.toFloat()))
    return Constraints.fitPrioritizingWidth(
      minWidth = filled.constrainWidth(scaled.width.roundToInt()),
      maxWidth = filled.maxWidth,
      minHeight = filled.constrainHeight(scaled.height.roundToInt()),
      maxHeight = filled.maxHeight,
    )
  }

  override fun ContentDrawScope.draw() {
    val painter = painter
    if (painter == null) {
      drawContent()
      return
    }
    val scaled = scaledSize(painter.intrinsicSize, size)
    val position = imageOptions.alignment.align(
      IntSize(scaled.width.roundToInt(), scaled.height.roundToInt()),
      IntSize(size.width.roundToInt(), size.height.roundToInt()),
      layoutDirection,
    )
    // A content scale that crops draws past the edges of the node, and nothing else in the layout
    // stops that from covering whatever sits next to it. Clipping while drawing needs no graphics
    // layer, unlike Modifier.clipToBounds.
    clipRect {
      translate(position.x.toFloat(), position.y.toFloat()) {
        with(painter) { draw(scaled, imageOptions.alpha, imageOptions.colorFilter) }
      }
    }
    drawContent()
  }

  /** What the content scale does to an image of [intrinsic] size drawn into [destination]. */
  private fun scaleFactor(intrinsic: Size, destination: Size): ScaleFactor =
    imageOptions.contentScale.computeScaleFactor(intrinsic, destination)

  /** The size the image is drawn at inside a [destination] sized node. */
  private fun scaledSize(intrinsic: Size, destination: Size): Size {
    if (destination.width == 0f || destination.height == 0f) return Size.Zero
    val source = Size(
      width = if (intrinsic.hasFiniteWidth()) intrinsic.width else destination.width,
      height = if (intrinsic.hasFiniteHeight()) intrinsic.height else destination.height,
    )
    return source * imageOptions.contentScale.computeScaleFactor(source, destination)
  }

  // The four below answer with the image's own size, the way Modifier.paint does, so an
  // IntrinsicSize around a LandscapistImage measures the image rather than the empty node under it.

  override fun IntrinsicMeasureScope.minIntrinsicWidth(
    measurable: IntrinsicMeasurable,
    height: Int,
  ): Int = maxOf(
    resolveConstraints(Constraints(maxHeight = height)).minWidth,
    measurable.minIntrinsicWidth(height),
  )

  override fun IntrinsicMeasureScope.maxIntrinsicWidth(
    measurable: IntrinsicMeasurable,
    height: Int,
  ): Int = maxOf(
    resolveConstraints(Constraints(maxHeight = height)).minWidth,
    measurable.maxIntrinsicWidth(height),
  )

  override fun IntrinsicMeasureScope.minIntrinsicHeight(
    measurable: IntrinsicMeasurable,
    width: Int,
  ): Int = maxOf(
    resolveConstraints(Constraints(maxWidth = width)).minHeight,
    measurable.minIntrinsicHeight(width),
  )

  override fun IntrinsicMeasureScope.maxIntrinsicHeight(
    measurable: IntrinsicMeasurable,
    width: Int,
  ): Int = maxOf(
    resolveConstraints(Constraints(maxWidth = width)).minHeight,
    measurable.maxIntrinsicHeight(width),
  )
}

private fun Size.hasFiniteWidth(): Boolean =
  this != Size.Unspecified && !width.isNaN() && width != Float.POSITIVE_INFINITY

private fun Size.hasFiniteHeight(): Boolean =
  this != Size.Unspecified && !height.isNaN() && height != Float.POSITIVE_INFINITY

/**
 * The same constraints with a zero bound treated as no bound.
 *
 * A parent can measure at zero before it has room: a collapsed AnimatedVisibility, a lazy item
 * whose container is still empty. A zero target is not a small image, it is no answer at all, and
 * the decoders read it as one: Android's sample size loop overflows on it and desktop fits the
 * image into nothing and caches a single pixel under the url.
 */
private fun Constraints.withoutZeroBounds(): Constraints = if (maxWidth > 0 && maxHeight > 0) {
  this
} else {
  Constraints(
    minWidth = minWidth,
    maxWidth = if (maxWidth > 0) maxWidth else Constraints.Infinity,
    minHeight = minHeight,
    maxHeight = if (maxHeight > 0) maxHeight else Constraints.Infinity,
  )
}

/**
 * Whether these two states say the same thing, without comparing raw bytes.
 *
 * `LandscapistImageState.Success` compares its `rawData` by content, and this runs on the thread
 * that draws, so a multi megabyte image would be walked byte by byte to answer a question the
 * image's identity already answers.
 */
private fun LandscapistImageState.isSameAs(other: LandscapistImageState?): Boolean = when {
  this === other -> true
  other == null -> false
  this is LandscapistImageState.Success && other is LandscapistImageState.Success ->
    data === other.data &&
      dataSource == other.dataSource &&
      originalWidth == other.originalWidth &&
      originalHeight == other.originalHeight &&
      diskCachePath == other.diskCachePath
  else -> this == other
}
