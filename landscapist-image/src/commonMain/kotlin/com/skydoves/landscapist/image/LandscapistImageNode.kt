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
import androidx.compose.ui.layout.times
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
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
import kotlin.math.roundToInt

/**
 * Loads and draws an image on the node it modifies.
 *
 * This is the whole image, in one layout node: it reads the constraints it is measured with to pick
 * the size to decode at, runs the load, holds the painter and draws it. Nothing about the load
 * lives in composition, so an image resolving invalidates the draw of this one node rather than
 * recomposing anything, and the size it decodes at is read during measurement rather than written
 * back into composition as state.
 *
 * It only applies when there is genuinely nothing to compose inside the image: no caller slot, no
 * plugin, no crossfade. Everything else goes through [LandscapistImage]'s composed path, which this
 * does not replace.
 *
 * @param landscapist The loader to run the request against.
 * @param request The request to load, before a target size is known.
 * @param imageOptions How the image is drawn, and the size to ask for when the caller fixed one.
 * @param onState Called with each state the image reaches, in order.
 * @param unpaintable Set when the loaded data needs a composed painter, which this node has no way
 * to build. The caller reads it and falls back to the composed path. A state rather than a callback
 * because a state is what the caller has to hold anyway, and a callback would be a second object
 * per image to say the same thing.
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
   * The image, once there is one.
   *
   * A plain field rather than snapshot state. The only reader is this node, so it can invalidate
   * its own measurement and drawing directly, and a snapshot read from inside measure and draw
   * costs the observer a subscription per node per pass for a value that changes once.
   */
  private var painter: Painter? = null

  /** The last state handed to [onState], or null when none has been yet. */
  private var state: LandscapistImageState? = null
  private var started = false

  /**
   * The load in flight, so a reload can stop it.
   *
   * Without this, rebinding a list item to another image left the first load running: it came back
   * later, published, and put the previous image over the one the item now shows. The node's own
   * scope only cancels on detach, and a rebind is not a detach.
   */
  private var loadJob: Job? = null

  override fun onAttach() {
    peek()
  }

  override fun onDetach() {
    clear()
  }

  override fun onReset() {
    clear()
  }

  /** Drops what was loaded, without asking a node that is on its way out to lay out again. */
  private fun clear() {
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
    val reload = this.landscapist != landscapist ||
      this.request != request ||
      this.imageOptions.loadingOptionsKey != imageOptions.loadingOptionsKey
    this.landscapist = landscapist
    this.request = request
    this.imageOptions = imageOptions
    this.onState = onState
    this.unpaintable = unpaintable
    if (reload) {
      loadJob?.cancel()
      loadJob = null
      started = false
      state = null
      // Through setPainter, so the image on screen is taken down in the same pass that puts the new
      // one up rather than being left there until the load comes back.
      setPainter(null)
      peek()
    }
  }

  /**
   * The synchronous memory cache read.
   *
   * An image already in memory is drawn in the very first frame this way. Waiting for the load to
   * come back costs a frame of empty content even on a hit, which is what makes an image blink when
   * it enters, most visibly inside a shared element transition.
   */
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
    val sized = buildSizedRequest(request, imageOptions, constraints)
    loadJob?.cancel()
    loadJob = coroutineScope.launch {
      // Collected directly rather than through flow {}, catch {} and distinctUntilChanged(). Each
      // of those is another flow, another collector and another continuation per image, and none of
      // them is needed here: the conversion happens in the collector, publish already drops a state
      // equal to the one on screen, and a try around the collection is what catch would compile to.
      //
      // No loading state is emitted up front either: Landscapist.load emits one only when the image
      // is not already in memory, so a cached image never passes through one.
      try {
        landscapist.load(sized).collect { publish(it.toLandscapistImageState()) }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (throwable: Throwable) {
        publish(LandscapistImageState.Failure(reason = throwable))
      }
    }
  }

  private fun publish(next: LandscapistImageState) {
    if (next == state) return
    // Once measurement lands, the request restarts at its real target size. Dropping back to a
    // loading state would blink away an image the user can already see, so an image on screen holds
    // until the resized one resolves.
    if (next is LandscapistImageState.Loading && state is LandscapistImageState.Success) return
    if (next is LandscapistImageState.Success) {
      val painted = landscapistPainterOrNull(next.data)
      val handOver = unpaintable
      if (painted == null && handOver != null) {
        // An animated drawable draws by reading state and has a lifecycle to dispatch, neither of
        // which this node can give it. The composed path can, and this is what sends it there.
        handOver.value = true
        return
      }
      // With nowhere to hand it to, an image with no painter draws nothing, which is what a
      // composed painter for an unrecognised type did too. The state still reaches the caller.
      setPainter(painted)
    } else {
      setPainter(null)
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
  private fun setPainter(next: Painter?) {
    if (painter == next) return
    painter = next
    if (!isAttached) return
    invalidateMeasurement()
    invalidateDraw()
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
    return Constraints(
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
