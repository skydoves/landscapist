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
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Loads [model] and hands back a [androidx.compose.ui.graphics.painter.Painter] for the caller to
 * draw wherever it likes.
 *
 * The caller owns the layout node, so an image costs one node rather than a container plus its
 * contents. In exchange there is no loading or failure slot, no plugin and no crossfade, since each
 * of those needs something composed around the image. Use [LandscapistImage] when any matter.
 *
 * The painter keeps its identity across recompositions, and an image already in the memory cache is
 * drawn on the first frame.
 *
 * The size to decode at comes from the first draw and is then held. A painter learns nothing else
 * about its bounds, so set a size through [requestBuilder] when the first draw is not the size the
 * image ends up at, and whenever the caller bounds one axis and leaves the other to the image: a
 * node measured to nothing on the open axis is never drawn, so the request goes out unsized and the
 * image is decoded at its own size, capped by `maxBitmapSize`. [LandscapistImage] reads the
 * constraints instead and has no such case.
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
  // Read rather than waited for, so a decoded image is drawn in this painter's first frame.
  val painter = remember(request, landscapist) { LandscapistImagePainter(landscapist, request) }
  painter.onImageStateChanged = onImageStateChanged
  LaunchedEffect(painter) { painter.load() }

  // Made drawable here rather than inside the painter: this is where a resource can be resolved
  // and where an animated drawable keeps what it remembers.
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
   * Not snapshot state: it is written while drawing, where a snapshot write would invalidate the
   * frame being drawn.
   */
  private val drawSize = MutableStateFlow(IntSize.Zero)

  override val intrinsicSize: Size
    get() = delegate?.intrinsicSize ?: Size.Unspecified

  override fun DrawScope.onDraw() {
    if (drawSize.value == IntSize.Zero) {
      val width = size.width.roundToInt()
      val height = size.height.roundToInt()
      // Either axis is enough. A caller who bounds one and leaves the other to the image, which is
      // fillMaxWidth in a column, measures to nothing high until there is an image, so waiting for
      // both would fall through to the unsized request and decode at the source's own size.
      if (width > 0 || height > 0) drawSize.value = IntSize(width, height)
    }
    delegate?.run { draw(size) }
  }

  /** Loads at the size this painter is drawn at, then follows the loader until it is cancelled. */
  suspend fun load() {
    // Before the size wait: a resource is drawn at whatever size it is given.
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
      // The first draw is what tells a painter how big the image has to be, and it is held from
      // then on. A caller who bounds neither axis is never drawn at a size, so the wait gives up
      // and loads at the image's own size, which is Coil's fallback too.
      //
      // Counted in frames, not milliseconds: a clock cannot tell that layout apart from a slow
      // first frame, and giving up on a slow cold start would decode every image at full size.
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
        // An axis that was never drawn is left open rather than sent as zero, which the decoders
        // read as a box of no size.
        request.copy(
          targetWidth = if (size.width > 0) size.width else Int.MAX_VALUE,
          targetHeight = if (size.height > 0) size.height else Int.MAX_VALUE,
        )
      }
    }
    // The dispatcher this effect runs on. The collector below does not stay on it, because a flow's
    // collector runs wherever the emission happens, and the caller's callback has to arrive where
    // the caller expects it. `data` is snapshot state and is safe either way.
    val ui = coroutineContext[ContinuationInterceptor] ?: EmptyCoroutineContext
    landscapist.load(sized)
      .catch { throwable ->
        withContext(ui) {
          onImageStateChanged?.invoke(LandscapistImageState.Failure(reason = throwable))
        }
      }
      .collect { result ->
        // An image on screen is kept while a resized one resolves, the same way LandscapistImage
        // keeps it: dropping back to nothing would blink away something the user can already see.
        if (result is ImageResult.Success) data = result.data
        val next = result.toLandscapistImageState()
        withContext(ui) { onImageStateChanged?.invoke(next) }
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
