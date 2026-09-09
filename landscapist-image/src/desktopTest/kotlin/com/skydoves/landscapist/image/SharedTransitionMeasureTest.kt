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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.ImageComponent
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
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a shared element transition actually hands the image it is animating.
 *
 * The claim under test is that a container animated by `sharedBounds` measures its content at a
 * different size on every frame, so an image that reads its constraints once and locks them reads
 * the animation's starting bounds and asks the decoder for a picture that size. Everything here
 * records what the image was really measured with, per pass and per frame.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class SharedTransitionMeasureTest {

  private val url = "https://example.com/photo.png"

  /** One measurement of the image's own slot. */
  private data class Measured(val lookahead: Boolean, val constraints: String)

  private class Recorder {
    /** Every measurement of the slot, in order, repeats collapsed. */
    val measured = mutableListOf<Measured>()

    /** Every size the slot was placed at, in order, repeats collapsed. */
    val placed = mutableListOf<IntSize>()

    /** The target size every decode was asked for, in order. */
    val decodedAt = mutableListOf<Pair<Int?, Int?>>()

    /** The slot's rectangle on screen, which carries any scale layer above it. */
    val onScreen = mutableListOf<String>()

    fun record(lookahead: Boolean, constraints: Constraints) {
      val text = "${constraints.minWidth}..${bound(constraints.maxWidth)} x " +
        "${constraints.minHeight}..${bound(constraints.maxHeight)}"
      val next = Measured(lookahead, text)
      if (measured.lastOrNull() != next) measured += next
    }

    private fun bound(value: Int) = if (value == Constraints.Infinity) "inf" else "$value"
  }

  /** Held open so the image lands on a frame the test chooses, inside the transition. */
  private class Gate {
    val opened = CompletableDeferred<Unit>()
  }

  private fun loader(
    recorder: Recorder,
    width: Int,
    height: Int,
    gate: Gate? = null,
  ): Landscapist =
    Landscapist.builder()
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

  /** Records what the parent offered this slot, in whichever pass is running. */
  private fun Modifier.spy(recorder: Recorder): Modifier = this
    .layout { measurable, constraints ->
      recorder.record(isLookingAhead, constraints)
      val placeable = measurable.measure(constraints)
      layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
    .onGloballyPositioned {
      if (recorder.placed.lastOrNull() != it.size) recorder.placed += it.size
      val bounds = it.boundsInRoot()
      val text = "${bounds.left.toInt()},${bounds.top.toInt()} " +
        "${bounds.width.toInt()}x${bounds.height.toInt()}"
      if (recorder.onScreen.lastOrNull() != text) recorder.onScreen += text
    }

  /**
   * The gallery-to-viewer navigation, driven a frame at a time.
   *
   * [destinationBounds] is what the destination puts around the image: `sharedBounds` in the
   * gallery's own shape, or `sharedElement`, which is the remeasuring mode.
   */
  private fun runTransition(
    imageWidth: Int,
    imageHeight: Int,
    loadOnFrame: Int = -1,
    component: @Composable () -> ImageComponent = { ShimmerOnly() },
    destinationBounds: @Composable SharedTransitionScope.(AnimatedContentScope) -> Modifier,
  ): Recorder {
    val recorder = Recorder()
    val gate = if (loadOnFrame >= 0) Gate() else null
    val landscapist = loader(recorder, imageWidth, imageHeight, gate)
    runComposeUiTest {
      var showViewer by mutableStateOf(false)
      setContent {
        SharedTransitionLayout(modifier = Modifier.size(300.dp, 600.dp)) {
          AnimatedContent(
            targetState = showViewer,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "gallery-viewer",
          ) { viewerVisible ->
            val animatedContentScope = this
            if (!viewerVisible) {
              // The gallery cell: a small square in the corner, the way a grid item is.
              Box(modifier = Modifier.fillMaxSize()) {
                Box(
                  modifier = Modifier
                    .size(100.dp)
                    .sharedBounds(
                      sharedContentState = rememberSharedContentState(key = url),
                      animatedVisibilityScope = animatedContentScope,
                      boundsTransform = LinearBounds,
                    ),
                )
              }
            } else {
              // The viewer page: the shared bounds, then fillMaxSize, then the image, which is
              // exactly what ViewerPage builds.
              Box(
                modifier = destinationBounds(animatedContentScope).fillMaxSize(),
              ) {
                LandscapistImage(
                  imageModel = { url },
                  landscapist = landscapist,
                  modifier = Modifier.fillMaxSize().spy(recorder),
                  component = component(),
                  imageOptions = ImageOptions(contentScale = ContentScale.Fit),
                  requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
                )
              }
            }
          }
        }
      }
      waitForIdle()
      mainClock.autoAdvance = false
      recorder.measured.clear()
      recorder.placed.clear()
      showViewer = true
      // A frame at a time across the whole 300ms transition, so every intermediate bound is seen.
      repeat(24) { frame ->
        mainClock.advanceTimeByFrame()
        waitForIdle()
        if (frame == loadOnFrame) {
          gate?.opened?.complete(Unit)
          mainClock.advanceTimeByFrame()
          waitForIdle()
        }
      }
      mainClock.autoAdvance = true
      waitForIdle()
    }
    return recorder
  }

  @Test
  fun `sharedBounds measures the image at the destination size, never at the animating bounds`() {
    val recorder = runTransition(1200, 900) { scope ->
      Modifier.sharedBounds(
        sharedContentState = rememberSharedContentState(key = url),
        animatedVisibilityScope = scope,
        boundsTransform = LinearBounds,
      )
    }

    println("sharedBounds measured: ${recorder.measured}")
    println("sharedBounds placed: ${recorder.placed}")
    println("sharedBounds decoded at: ${recorder.decodedAt}")

    // 300x600 dp at density 1 on desktop. Both passes, every frame, the destination size.
    assertEquals(
      listOf("300..300 x 600..600"),
      recorder.measured.map { it.constraints }.distinct(),
      "the image was measured at something other than the destination size: ${recorder.measured}",
    )
    assertEquals(
      listOf(IntSize(300, 600)),
      recorder.placed,
      "the image slot changed size during the transition: ${recorder.placed}",
    )
  }

  @Test
  fun `sharedElement remeasures the image at every animating bound`() {
    val recorder = runTransition(1200, 900) { scope ->
      Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(key = url),
        animatedVisibilityScope = scope,
        boundsTransform = LinearBounds,
      )
    }

    println("sharedElement measured: ${recorder.measured}")
    println("sharedElement placed: ${recorder.placed}")
    println("sharedElement decoded at: ${recorder.decodedAt}")

    assertTrue(
      recorder.placed.size > 2,
      "sharedElement was expected to resize the image every frame: ${recorder.placed}",
    )
  }

  @Test
  fun `the request asks for the size the first lookahead measured, whatever the bounds do`() {
    val recorder = runTransition(1200, 900) { scope ->
      Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(key = url),
        animatedVisibilityScope = scope,
        boundsTransform = LinearBounds,
      )
    }

    println("remeasuring decode targets: ${recorder.decodedAt}")
    assertEquals(
      listOf(300 to 600),
      recorder.decodedAt.distinct(),
      "the decode target followed the animating bounds: ${recorder.decodedAt}",
    )
  }

  /**
   * The image landing while the bounds are still moving, which is the sequence the report is about:
   * the destination composes empty, the transition starts, and the picture arrives part way in.
   */
  @Test
  fun `an image that lands mid transition does not resize the slot under sharedBounds`() {
    val recorder = runTransition(1200, 900, loadOnFrame = 8) { scope ->
      Modifier.sharedBounds(
        sharedContentState = rememberSharedContentState(key = url),
        animatedVisibilityScope = scope,
        boundsTransform = LinearBounds,
      )
    }

    println("gated sharedBounds placed: ${recorder.placed}")
    println("gated sharedBounds measured: ${recorder.measured.map { it.constraints }.distinct()}")
    println("gated sharedBounds decoded at: ${recorder.decodedAt}")

    assertEquals(
      listOf(IntSize(300, 600)),
      recorder.placed,
      "the slot resized when the image landed mid transition: ${recorder.placed}",
    )
  }

  /** The same, remeasuring. The slot follows the bounds and never goes backwards. */
  @Test
  fun `an image that lands mid transition does not reverse the slot under sharedElement`() {
    val recorder = runTransition(1200, 900, loadOnFrame = 8) { scope ->
      Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(key = url),
        animatedVisibilityScope = scope,
        boundsTransform = LinearBounds,
      )
    }

    println("gated sharedElement placed: ${recorder.placed}")
    println("gated sharedElement decoded at: ${recorder.decodedAt}")

    assertEquals(
      recorder.placed.sortedBy { it.width },
      recorder.placed,
      "the slot shrank when the image landed mid transition: ${recorder.placed}",
    )
  }

  /**
   * The plugin set `ViewerPage` actually builds: the caller's shimmer plus the zoomable it adds.
   *
   * A `ComposablePlugin` is what takes `LandscapistImage` off the container-paint path and puts the
   * image inside a composed subtree, so this is the stack the gallery really runs.
   */
  @Test
  fun `the viewer page stack does not resize the slot when the image lands mid transition`() {
    val recorder = runTransition(
      imageWidth = 1200,
      imageHeight = 900,
      loadOnFrame = 8,
      component = { ShimmerAndZoomable() },
    ) { scope ->
      Modifier.sharedBounds(
        sharedContentState = rememberSharedContentState(key = url),
        animatedVisibilityScope = scope,
        boundsTransform = LinearBounds,
      )
    }

    println("viewer stack placed: ${recorder.placed}")
    println("viewer stack measured: ${recorder.measured.map { it.constraints }.distinct()}")
    println("viewer stack decoded at: ${recorder.decodedAt}")
    println("viewer stack on screen: ${recorder.onScreen}")

    assertEquals(
      listOf(IntSize(300, 600)),
      recorder.placed,
      "the viewer page's image slot changed size during the transition: ${recorder.placed}",
    )
    // boundsInRoot carries the scale layer scaleToBounds places the content with, so this is the
    // rectangle the picture is actually drawn into. It only ever grows.
    val widths = recorder.onScreen.map { it.substringAfter(' ').substringBefore('x').toInt() }
    assertEquals(
      widths.sorted(),
      widths,
      "the drawn rectangle went backwards during the transition: ${recorder.onScreen}",
    )
  }

  /** The same stack under the remeasuring mode, which is the only one that moves the slot. */
  @Test
  fun `the viewer page stack under sharedElement grows and never goes backwards`() {
    val recorder = runTransition(
      imageWidth = 1200,
      imageHeight = 900,
      loadOnFrame = 8,
      component = { ShimmerAndZoomable() },
    ) { scope ->
      Modifier.sharedElement(
        sharedContentState = rememberSharedContentState(key = url),
        animatedVisibilityScope = scope,
        boundsTransform = LinearBounds,
      )
    }

    println("viewer stack remeasured placed: ${recorder.placed}")
    println("viewer stack remeasured decoded at: ${recorder.decodedAt}")

    assertEquals(
      recorder.placed.sortedBy { it.width },
      recorder.placed,
      "the viewer page's image slot shrank during the transition: ${recorder.placed}",
    )
    assertEquals(listOf(300 to 600), recorder.decodedAt.distinct())
  }

  private companion object {
    /** Linear and long, so a frame-stepped run lands squarely inside the animation. */
    val LinearBounds = BoundsTransform { _, _ -> tween(300, easing = LinearEasing) }

    @Composable
    fun ShimmerOnly(): ImageComponent = rememberImageComponent {
      +ShimmerPlugin(
        shimmer = Shimmer.Resonate(
          baseColor = Color.DarkGray,
          highlightColor = Color.LightGray,
        ),
      )
    }

    @Composable
    fun ShimmerAndZoomable(): ImageComponent = rememberImageComponent {
      +ShimmerPlugin(
        shimmer = Shimmer.Resonate(
          baseColor = Color.DarkGray,
          highlightColor = Color.LightGray,
        ),
      )
      +ZoomablePlugin()
    }
  }
}
