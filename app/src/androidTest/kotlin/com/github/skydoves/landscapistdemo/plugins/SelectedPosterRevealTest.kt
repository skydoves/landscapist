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
package com.github.skydoves.landscapistdemo.plugins

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.builder
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.zoomable.ProvideImageRegionDecoder
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import com.skydoves.landscapist.zoomable.rememberZoomableState
import com.skydoves.landscapist.zoomable.subsampling.ImageRegionDecoder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The demo's detail image, with the plugin set the demo installs on it.
 *
 * [CircularRevealPluginTest] runs the reveal on its own and off a warmed memory cache, which is the
 * case that already worked. This one is the screen the reveal is reported missing on: the four
 * plugins together, a disk cache behind them, and a poster far bigger than the slot, which is what
 * lets the zoomable plugin sub-sample.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SelectedPosterRevealTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  /** Long enough that a reveal cannot be mistaken for a frame of latency. */
  private val revealMs = 1000

  private val successes = AtomicInteger()
  private val reveals = AtomicInteger()
  private val diskPaths = Collections.synchronizedList(mutableListOf<String>())
  private val lastState = AtomicReference<String>("")

  @Before
  fun start() {
    server = LocalImageServer()
    // A poster, not a thumbnail: sub-sampling only engages on an image bigger than its slot.
    server.serve(
      "/poster.png",
      ImageFixtures.solid(960, 1280, BlueFixture.toArgb(), Bitmap.CompressFormat.PNG),
      PngContentType,
    )
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    server.close()
  }

  @Test
  fun theRevealRunsWithTheDemosPluginSet() {
    val trace = run(withZoomable = true, subSampling = true)
    assertTheRevealAnimates("the demo's four plugins", trace)
  }

  @Test
  fun theRevealRunsWithoutTheZoomablePlugin() {
    val trace = run(withZoomable = false, subSampling = false)
    assertTheRevealAnimates("shimmer, palette and the reveal", trace)
  }

  @Test
  fun theRevealRunsWithTheZoomablePluginAndNoSubSampling() {
    val trace = run(withZoomable = true, subSampling = false)
    assertTheRevealAnimates("the four plugins with sub-sampling off", trace)
  }

  /**
   * A crossfade puts the content key back on the decoded bitmap, which is what the image keyed the
   * success subtree on before it was keyed on the kind of state. Both keyings are covered, so a
   * reveal that goes missing cannot be blamed on which of the two is in use.
   */
  @Test
  fun theRevealRunsWithTheContentKeyedOnTheBitmapAndSubSampling() {
    val trace = run(withZoomable = true, subSampling = true, crossfade = true)
    assertTheRevealAnimates("the four plugins, sub-sampling on, keyed on the bitmap", trace)
  }

  @Test
  fun theRevealRunsWithTheContentKeyedOnTheBitmapAndNoSubSampling() {
    val trace = run(withZoomable = true, subSampling = false, crossfade = true)
    assertTheRevealAnimates("the four plugins, sub-sampling off, keyed on the bitmap", trace)
  }

  /**
   * Which of the two pictures in a sub-sampled node is on screen, and when.
   *
   * The image the reveal is composed onto is blue, and the region decoder handed to the zoomable
   * plugin holds a red image of the same size, so the colour of the node says which one is being
   * drawn. Unzoomed, the tiles are one sample of the whole image and the content is a full decode
   * of it carrying the caller's plugins, so the content is what should be there. Zoomed in, the
   * tiles are the only thing that has the resolution, so they have to take over.
   */
  @Test
  fun theSubSampledTilesTakeOverOnlyOnceTheImageIsZoomed() {
    val red = ImageRegionDecoder.create(
      ImageFixtures.solid(960, 1280, RedFixture.toArgb(), Bitmap.CompressFormat.PNG),
    )
    val trace = run(withZoomable = true, subSampling = true, regionDecoder = red)
    val report = trace.filterIndexed { i, _ -> i % 2 == 0 }.joinToString(" ")

    assertTrue(
      "the image never settled, so there is nothing to say about what covered it: $report",
      trace.last().covered > 0.99f,
    )
    assertTrue(
      "the tiles were drawn over an unzoomed image, which is where they cost quality and cut " +
        "short whatever the caller's painter plugins are animating: $report",
      trace.none { it.centre.matches(RedFixture) || it.corner.matches(RedFixture) },
    )

    zoomWellPastTheTileThreshold()

    val zoomed = compose.readVisualPixels()
    assertTrue(
      "the tiles never took over once the image was zoomed past 1.5x, so sub-sampling no longer " +
        "does the one thing it is there for: the middle of the node was " +
        zoomed.centre().describe(),
      zoomed.centre().matches(RedFixture),
    )
  }

  /** A centred pinch, well past the zoom the tiles start carrying more than the content does. */
  private fun zoomWellPastTheTileThreshold() {
    compose.onNodeWithTag(PluginImageTag).performTouchInput {
      val grip = (width * 0.02f).coerceAtLeast(4f)
      val spread = width * 0.35f
      down(0, Offset(center.x - grip, center.y))
      down(1, Offset(center.x + grip, center.y))
      updatePointerTo(0, Offset(center.x - spread, center.y))
      updatePointerTo(1, Offset(center.x + spread, center.y))
      move()
      up(0)
      up(1)
    }
    repeat(30) {
      compose.mainClock.advanceTimeBy(FrameMs)
      Thread.sleep(4)
    }
  }

  /** Composes the screen, waits for the image, then reads a frame every [SampleMs]. */
  private fun run(
    withZoomable: Boolean,
    subSampling: Boolean,
    regionDecoder: ImageRegionDecoder? = null,
    crossfade: Boolean = false,
  ): List<Frame> {
    val landscapist = newLandscapist()
    val url = server.url("/poster.png")

    compose.setContent {
      Box(
        modifier = Modifier.fillMaxSize().background(Backdrop),
        contentAlignment = Alignment.Center,
      ) {
        ProvideImageRegionDecoder(regionDecoder) {
          Poster(url, landscapist, withZoomable, subSampling, crossfade)
        }
      }
    }

    compose.advanceUntil("the poster to load") { successes.get() >= 1 }

    return buildList {
      repeat(SampleCount) { index ->
        val pixels = compose.readVisualPixels()
        add(
          Frame(
            atMs = index * SampleMs,
            covered = pixels.coveredBy(BlueFixture),
            centre = pixels.centre(),
            corner = pixels.corner(),
            finished = reveals.get(),
          ),
        )
        compose.mainClock.advanceTimeBy(SampleMs.toLong())
      }
    }
  }

  @Composable
  private fun Poster(
    url: String,
    landscapist: Landscapist,
    withZoomable: Boolean,
    subSampling: Boolean,
    crossfade: Boolean,
  ) {
    val zoomableState = rememberZoomableState(
      config = ZoomableConfig(
        enableSubSampling = subSampling,
        maxZoom = 40f,
        doubleTapZoom = 20f,
      ),
      resetKey = url,
    )

    val component = rememberImageComponent {
      +ShimmerPlugin(
        Shimmer.Resonate(baseColor = Color.White, highlightColor = Color.LightGray),
      )
      if (withZoomable) {
        +ZoomablePlugin(state = zoomableState)
      }
      +PalettePlugin { }
      if (crossfade) {
        +CrossfadePlugin(duration = 1)
      }
      +CircularRevealPlugin(
        duration = revealMs,
        onFinishListener = { reveals.incrementAndGet() },
      )
    }

    LandscapistImage(
      imageModel = { url },
      landscapist = landscapist,
      modifier = Modifier
        .width(PosterWidth)
        .aspectRatio(0.75f)
        .testTag(PluginImageTag),
      component = component,
      onImageStateChanged = { state ->
        lastState.set(state::class.simpleName.orEmpty())
        if (state is LandscapistImageState.Success) {
          successes.incrementAndGet()
          diskPaths += describe(state.diskCachePath)
        }
      },
    )
  }

  private fun describe(path: String?): String = when {
    path == null -> "no disk path"
    File(path).exists() -> "$path (on disk)"
    else -> "$path (not written yet)"
  }

  private fun newLandscapist(): Landscapist {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val landscapist = Landscapist.builder(context).build()
    // A shared on disk cache would otherwise carry one test's poster into the next.
    runBlocking { landscapist.clearCaches() }
    return landscapist
  }

  /**
   * A reveal is a circle growing from the middle of the node, so it is read off the corners, which
   * fill in last, rather than off a callback that says an animation it never drew has finished.
   */
  private fun assertTheRevealAnimates(what: String, trace: List<Frame>) {
    val report = "$what: ${trace.filterIndexed { i, _ -> i % 2 == 0 }.joinToString(" ")} " +
      "(successes=${successes.get()}, reveals=${reveals.get()}, last=${lastState.get()}, " +
      "disk=$diskPaths)"

    assertTrue(
      "the image never reached the screen at all, so there is no reveal to judge. $report",
      trace.last().covered > 0.99f,
    )
    assertTrue(
      "the node was fully covered on the frame the image arrived on, so nothing was revealed. " +
        report,
      trace.first().covered < 0.5f,
    )
    val partial = trace.filter { it.centre.matches(BlueFixture) && it.corner.isBackdrop() }
    assertTrue(
      "no frame had the middle of the node revealed and a corner still bare, so whatever the " +
        "node drew, it was not a circle growing out of the middle. $report",
      partial.isNotEmpty(),
    )
    assertTrue(
      "the reveal was over within a frame of arriving, so it did not last the ${revealMs}ms it " +
        "was asked for. $report",
      partial.last().atMs >= revealMs / 4,
    )
  }

  private data class Frame(
    val atMs: Int,
    val covered: Float,
    val centre: Color,
    val corner: Color,
    val finished: Int,
  ) {
    override fun toString(): String = "[$atMs=${(covered * 100).toInt()}%" +
      (if (corner.isBackdrop()) " bare" else "") +
      (if (finished > 0) " done" else "") +
      "]"
  }

  private companion object {
    val PosterWidth = 180.dp
    const val SampleMs = 16
    const val SampleCount = 80
  }
}
