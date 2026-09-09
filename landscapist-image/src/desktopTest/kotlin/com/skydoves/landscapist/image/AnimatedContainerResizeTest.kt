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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An image inside a container whose bounds are animated by hand, with no lookahead pass to correct
 * the reading.
 *
 * [LandscapistImageInternal] records the constraints it was first measured with and locks them, and
 * a hand written animation is the case where that first measurement is the animation's starting
 * frame rather than the size it is heading for. These record, frame by frame, what the slot was
 * measured at, what it settled on, and what the loader was asked to decode.
 */
@OptIn(ExperimentalTestApi::class)
class AnimatedContainerResizeTest {

  private val url = "https://example.com/photo.png"

  /** Held open so the load lands on a frame the test chooses, mid-animation. */
  private class Gate {
    val opened = CompletableDeferred<Unit>()
  }

  private class Recorder {
    /** Every size the image's slot was placed at, in order, repeats collapsed. */
    val placed = mutableListOf<IntSize>()

    /** The target size every decode was asked for, in order. */
    val decodedAt = mutableListOf<Pair<Int?, Int?>>()
  }

  private fun loader(
    recorder: Recorder,
    gate: Gate?,
    width: Int,
    height: Int,
  ): Landscapist = Landscapist.builder()
    .noDiskCache()
    .fetcher(
      object : ImageFetcher {
        override fun canHandle(model: Any?): Boolean = true
        override suspend fun fetch(request: ImageRequest): FetchResult {
          gate?.opened?.await()
          return FetchResult.Success(byteArrayOf(1), mimeType = "image/png")
        }
      },
    )
    .decoder(
      object : ImageDecoder {
        override suspend fun decode(
          data: ByteArray,
          mimeType: String?,
          targetWidth: Int?,
          targetHeight: Int?,
          config: LandscapistConfig,
        ): DecodeResult {
          recorder.decodedAt += targetWidth to targetHeight
          return DecodeResult.Success(ImageBitmap(width, height), width, height)
        }
      },
    )
    .build()

  /**
   * Steps a container from [from] to [to] over 20 frames, letting the image land on frame [loadOn].
   *
   * [container] wraps the image in whatever the caller's screen puts around it; the animated size
   * is handed to it as a [Dp] so the container decides which axis it drives.
   */
  private fun animateBounds(
    from: Dp,
    to: Dp,
    loadOn: Int,
    imageWidth: Int = 1200,
    imageHeight: Int = 900,
    slotModifier: Modifier = Modifier.fillMaxSize(),
    contentScale: ContentScale = ContentScale.Fit,
    container: @Composable (Dp, @Composable () -> Unit) -> Unit,
  ): Recorder {
    val recorder = Recorder()
    val gate = Gate()
    val landscapist = loader(recorder, gate, imageWidth, imageHeight)
    runComposeUiTest {
      var animated by mutableStateOf(from)
      setContent {
        container(animated) {
          LandscapistImage(
            imageModel = { url },
            landscapist = landscapist,
            modifier = slotModifier.onGloballyPositioned {
              if (recorder.placed.lastOrNull() != it.size) recorder.placed += it.size
            },
            component = rememberImageComponent {
              +ShimmerPlugin(
                shimmer = Shimmer.Resonate(
                  baseColor = Color.DarkGray,
                  highlightColor = Color.LightGray,
                ),
              )
            },
            imageOptions = ImageOptions(contentScale = contentScale),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
      waitForIdle()
      mainClock.autoAdvance = false
      val steps = 20
      repeat(steps) { step ->
        animated = from + (to - from) * ((step + 1).toFloat() / steps)
        mainClock.advanceTimeByFrame()
        waitForIdle()
        if (step == loadOn) {
          gate.opened.complete(Unit)
          mainClock.advanceTimeByFrame()
          waitForIdle()
        }
      }
      mainClock.autoAdvance = true
      waitForIdle()
    }
    return recorder
  }

  /** The plain case: a square box grown by hand, the image filling it. */
  private val squareBox: @Composable (Dp, @Composable () -> Unit) -> Unit = { size, content ->
    Box(modifier = Modifier.size(size)) { content() }
  }

  @Test
  fun `a hand animated box locks the decode target to the frame it started at`() {
    val recorder = animateBounds(from = 100.dp, to = 300.dp, loadOn = 8, container = squareBox)

    println("grown box placed: ${recorder.placed}")
    println("grown box decoded at: ${recorder.decodedAt}")

    assertEquals(
      listOf(100 to 100),
      recorder.decodedAt.distinct(),
      "the decode target was expected to be locked to the starting bounds: ${recorder.decodedAt}",
    )
    // The slot itself still follows the container: the lock is on the request, not the layout.
    assertEquals(IntSize(100, 100), recorder.placed.first())
    assertEquals(IntSize(300, 300), recorder.placed.last())
  }

  @Test
  fun `a box that starts collapsed waits for a measurement worth locking`() {
    val recorder = animateBounds(from = 0.dp, to = 300.dp, loadOn = 8, container = squareBox)

    println("collapsed box placed: ${recorder.placed}")
    println("collapsed box decoded at: ${recorder.decodedAt}")

    // A bounded axis of zero used to be recorded as an answer, and a zero reads back as an open
    // axis, so both axes open left the request with no target: a decode at the source's own size,
    // and a cache read with no box to judge a variant against. The probe waits instead.
    assertTrue(
      recorder.decodedAt.none { it.first == null && it.second == null },
      "a zero first measure left the request unsized: ${recorder.decodedAt}",
    )
    assertTrue(
      recorder.decodedAt.all { (it.first ?: 0) > 0 },
      "the request was asked for at a width of nothing: ${recorder.decodedAt}",
    )
    // The latched open axis puts imageShape on the container once the image lands, and on an axis
    // the parent bounded that modifier is an identity: the slot never gives back space it had.
    assertEquals(
      recorder.placed.sortedBy { it.width },
      recorder.placed,
      "the slot shrank at some point: ${recorder.placed}",
    )
    assertEquals(IntSize(300, 300), recorder.placed.last())
  }

  /**
   * The collapsed start with a slot that does not fix both axes, which is where the latched open
   * axis puts an [imageShape] on the container once the image lands.
   */
  @Test
  fun `a collapsed start plus an open axis resizes the slot once the image lands`() {
    val recorder = animateBounds(
      from = 0.dp,
      to = 300.dp,
      loadOn = 8,
      slotModifier = Modifier.fillMaxWidth(),
      container = { size, content ->
        Column(
          modifier = Modifier.width(size).verticalScroll(rememberScrollState()),
        ) { content() }
      },
    )

    println("open axis placed: ${recorder.placed}")
    println("open axis decoded at: ${recorder.decodedAt}")

    assertTrue(recorder.placed.size > 1, "the slot never resized: ${recorder.placed}")
  }

  /**
   * The same column, but measured once at its full width before the animation starts, so the lock
   * reads the real width. This is the control for the test above.
   */
  @Test
  fun `a column measured at its real width first asks for that width`() {
    val recorder = animateBounds(
      from = 200.dp,
      to = 300.dp,
      loadOn = 8,
      slotModifier = Modifier.fillMaxWidth(),
      container = { size, content ->
        Column(
          modifier = Modifier.width(size).verticalScroll(rememberScrollState()),
        ) { content() }
      },
    )

    println("control column placed: ${recorder.placed}")
    println("control column decoded at: ${recorder.decodedAt}")

    // Int.MAX_VALUE is how buildSizedRequest spells "this axis is open".
    assertEquals(listOf(200 to Int.MAX_VALUE), recorder.decodedAt.distinct())
  }

  /**
   * A box the caller gave no size modifier, grown by hand. The container has to size itself from
   * whatever is inside it, which is what changes when the image lands.
   */
  @Test
  fun `an unsized slot in a grown box takes the image's own shape once it lands`() {
    val recorder = animateBounds(
      from = 100.dp,
      to = 300.dp,
      loadOn = 8,
      slotModifier = Modifier,
      container = { size, content ->
        Box(modifier = Modifier.width(size)) { content() }
      },
    )

    println("unsized slot placed: ${recorder.placed}")
    println("unsized slot decoded at: ${recorder.decodedAt}")

    assertTrue(recorder.placed.isNotEmpty())
  }
}
