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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * Loads [model] and hands back a [androidx.compose.ui.graphics.painter.Painter] for the caller to
 * draw wherever it likes.
 *
 * This is the same trade `Image` makes: the caller owns the layout node, so an image costs one node
 * rather than a container plus whatever is composed inside it. That is the reason to reach for it
 * over [LandscapistImage], and everything it gives up is the reason not to. A painter has no
 * loading or failure slot, runs no image plugin, and cannot crossfade, because each of those needs
 * something composed around the image and this composes nothing at all. Use [LandscapistImage] when
 * any of them matter.
 *
 * The painter keeps its identity across recompositions and across state changes, so an item drawing
 * it is not rebuilt when the image resolves. An image already in the memory cache is drawn on the
 * very first frame; anything else appears when it arrives.
 *
 * The size to decode at comes from the first draw, which is all a painter ever learns about its
 * bounds, and is then held, so an image whose bounds animate is not decoded again every frame. Set
 * a size through [requestBuilder] when the first draw is not the size the image ends up at.
 *
 * @param model The image model to load (URL, Uri, file path, byte array, and so on).
 * @param landscapist The Landscapist instance to load with. Defaults to the composition local.
 * @param requestBuilder Optional builder for customizing the image request.
 * @param onImageStateChanged Callback invoked when the image state changes.
 */
@Composable
public fun rememberLandscapistImagePainter(
  model: Any?,
  landscapist: Landscapist = getLandscapist(),
  requestBuilder: (ImageRequest.Builder.() -> Unit)? = null,
  onImageStateChanged: ((LandscapistImageState) -> Unit)? = null,
): Painter {
  val request = remember(model, requestBuilder) {
    ImageRequest.builder().apply {
      model(model)
      requestBuilder?.invoke(this)
    }.build()
  }
  // The memory cache is read here rather than waited for, so an image that is already decoded is
  // drawn in the frame this painter first appears in instead of the one after it.
  val painter = remember(request, landscapist) { LandscapistImagePainter(landscapist, request) }
  painter.onImageStateChanged = onImageStateChanged
  LaunchedEffect(painter) { painter.load() }

  // The image is turned into something drawable here rather than inside the painter, because that
  // is where a resource can be resolved and where an animated drawable gets to keep what it
  // remembers. The painter it is handed to keeps its own identity, so the caller's layout is not
  // rebuilt when this changes.
  val data = painter.data
  painter.delegate = if (data is DrawableResource) {
    painterResource(data)
  } else {
    rememberLandscapistPainter(data)
  }
  return painter
}

/**
 * A painter whose image arrives later.
 *
 * It draws nothing until one does, reports the size it was drawn at so the load can be made at that
 * size, and takes a decoded image without changing its own identity.
 */
private class LandscapistImagePainter(
  private val landscapist: Landscapist,
  private val request: ImageRequest,
) : Painter() {

  /** The loaded image, seeded from whatever the memory cache already holds. */
  var data: Any? by mutableStateOf(landscapist.peekMemoryCache(request)?.data)
    private set

  /** What actually draws, produced from [data] by the composable that owns this painter. */
  var delegate: Painter? by mutableStateOf(null)

  var onImageStateChanged: ((LandscapistImageState) -> Unit)? = null

  /**
   * The size this painter was last asked to draw at.
   *
   * Not a snapshot state: it is written while drawing, and a snapshot write there would invalidate
   * the frame being drawn. The load reads it once and then stops caring, so a flow is enough.
   */
  private val drawSize = MutableStateFlow(IntSize.Zero)

  override val intrinsicSize: Size
    get() = delegate?.intrinsicSize ?: Size.Unspecified

  override fun DrawScope.onDraw() {
    if (drawSize.value == IntSize.Zero) {
      val width = size.width.roundToInt()
      val height = size.height.roundToInt()
      if (width > 0 && height > 0) drawSize.value = IntSize(width, height)
    }
    delegate?.run { draw(size) }
  }

  /** Loads at the size this painter is drawn at, then follows the loader until it is cancelled. */
  suspend fun load() {
    // Checked before the size is waited for: a bundled resource is drawn at whatever size it is
    // given, so making it wait for the first draw left that draw with nothing to show.
    if (request.model is DrawableResource) {
      // A bundled resource is neither fetched nor cached, and painterResource draws it directly.
      data = request.model
      onImageStateChanged?.invoke(
        LandscapistImageState.Success(data = request.model, dataSource = DataSource.RESOURCE),
      )
      return
    }
    val sized = if (request.targetWidth != null && request.targetHeight != null) {
      request
    } else {
      // A painter has no constraints to read, so the first draw is what tells it how big the image
      // has to be. Held from then on, so bounds that animate do not decode again every frame.
      //
      // A painter with no image yet has no intrinsic size, so a caller who bounds neither axis
      // measures it to nothing, it is never drawn, and waiting here for a size would leave it
      // permanently blank with no error and no state to explain it. Past the wait it loads at the
      // image's own size, which is what Coil falls back to for the same reason.
      //
      // Counted in frames rather than milliseconds. A wall clock cannot tell that layout apart from
      // a first frame that is simply slow to arrive, and a cold start slow enough to trip the clock
      // would decode every image on the screen at its full size at the worst possible moment. A
      // painter that is being drawn at all is drawn on the frame after the one that composed it, so
      // a few frames with no draw means there is no draw coming.
      var size = drawSize.value
      var frames = 0
      while (size == IntSize.Zero && frames < FRAMES_BEFORE_UNSIZED) {
        withFrameNanos { }
        size = drawSize.value
        frames++
      }
      if (size == IntSize.Zero) {
        request
      } else {
        request.copy(
          targetWidth = size.width,
          targetHeight = size.height,
        )
      }
    }
    landscapist.load(sized)
      .catch { onImageStateChanged?.invoke(LandscapistImageState.Failure(reason = it)) }
      .collect { result ->
        // An image on screen is kept while a resized one resolves, the same way LandscapistImage
        // keeps it: dropping back to nothing would blink away something the user can already see.
        if (result is ImageResult.Success) data = result.data
        onImageStateChanged?.invoke(result.toLandscapistImageState())
      }
  }
}

/**
 * How many frames a painter waits for a draw before it gives up on being told a size.
 *
 * Two would do for the drawing itself. Three leaves room for a frame lost to composition before the
 * layout that would have drawn it.
 */
private const val FRAMES_BEFORE_UNSIZED = 3
