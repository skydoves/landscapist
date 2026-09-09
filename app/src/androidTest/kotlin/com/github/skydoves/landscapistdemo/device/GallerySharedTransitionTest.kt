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
package com.github.skydoves.landscapistdemo.device

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.builder
import com.skydoves.landscapist.gallery.ImageGallery
import com.skydoves.landscapist.gallery.ImageSharedTransitionConfig
import com.skydoves.landscapist.gallery.ImageViewer
import com.skydoves.landscapist.gallery.rememberImageViewerState
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.image.LocalLandscapist
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the viewer's image is measured at, placed at and drawn at on every frame of the gallery's
 * shared element transition.
 *
 * The screen is the demo's [com.github.skydoves.landscapistdemo.ui.GalleryDemoScreen]: an
 * [ImageGallery] and an [ImageViewer] in one [SharedTransitionLayout], swapped by an
 * [AnimatedContent], with the bounds wired through [ImageSharedTransitionConfig]. The viewer
 * composes its own page, so what is measured is the code the demo runs.
 *
 * A shared transition is the one case where a composable is measured over and over with bounds
 * that change every frame, which is what makes it worth pinning:
 *
 *  - [LandscapistImage] records the constraints its first measure sees and decodes at that size.
 *    Under a shared transition the approach pass offers the animation's *current* bounds, which
 *    start at the thumbnail's. Reading those instead of the destination's would decode the
 *    full-screen page at thumbnail size and draw it across the screen.
 *  - Anything that re-keys the request while the bounds animate decodes once per frame.
 *  - A container whose size follows the loaded image would move the shared element's target
 *    mid-flight, and the picture would go backwards on screen.
 *
 * The clock is paused at the tap and stepped one frame at a time, so each assertion is about a
 * sequence of frames rather than about an end state.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class GallerySharedTransitionTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  private val timeline = Collections.synchronizedList(mutableListOf<String>())
  private val cellSuccesses = AtomicInteger()

  /** Every painter the viewer's pages were handed, in order, by the size it draws from. */
  private val viewerPainters = Collections.synchronizedList(mutableListOf<IntSize>())

  /** The size the viewer's image composable was laid out at, per frame. */
  private val laidOutAt = Collections.synchronizedList(mutableListOf<IntSize>())

  /** The rect that layout occupied on the screen, per frame. */
  private val onScreenSize = Collections.synchronizedList(mutableListOf<IntSize>())

  private val startedAt = System.currentTimeMillis()

  private fun log(line: String) {
    timeline += "${System.currentTimeMillis() - startedAt}ms| $line"
  }

  @Before
  fun start() {
    server = LocalImageServer()
    SHAPES.forEachIndexed { index, shape ->
      server.serve(
        "/p$index.png",
        ImageFixtures.solid(shape.first, shape.second, TINTS[index], Bitmap.CompressFormat.PNG),
        delayMs = 20,
      )
    }
  }

  @After
  fun stop() = server.close()

  private fun urls(): List<String> = List(SHAPES.size) { server.url("/p$it.png") }

  private fun newLandscapist(): Landscapist {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val landscapist = Landscapist.builder(context).build()
    runBlocking { landscapist.clearCaches() }
    return landscapist
  }

  /**
   * The demo's gallery screen. The gallery's cells go through the caller's content slot only so
   * they can be tagged and their loads logged; the viewer composes its own page.
   */
  @Composable
  private fun Screen(urls: List<String>, showViewer: Boolean, onOpen: (Int) -> Unit) {
    var selectedPage by remember { mutableStateOf(0) }
    val galleryComponent = rememberImageComponent {
      +ShimmerPlugin(
        shimmer = Shimmer.Resonate(baseColor = Color.DarkGray, highlightColor = Color.LightGray),
      )
    }
    val viewerComponent = rememberImageComponent {
      +ShimmerPlugin(
        shimmer = Shimmer.Resonate(baseColor = Color.DarkGray, highlightColor = Color.LightGray),
      )
      +DrawProbePlugin
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
      AnimatedContent(
        targetState = showViewer,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "gallery-viewer",
      ) { viewerVisible ->
        val animatedContentScope = this
        if (!viewerVisible) {
          ImageGallery(
            images = urls,
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            component = galleryComponent,
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
            onImageClick = { index, _ ->
              selectedPage = index
              onOpen(index)
            },
            content = { index, imageModel ->
              LandscapistImage(
                imageModel = { imageModel },
                modifier = Modifier.fillMaxWidth().testTag("cell-$index"),
                component = galleryComponent,
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                onImageStateChanged = { state ->
                  if (state is LandscapistImageState.Success) {
                    cellSuccesses.incrementAndGet()
                    log("cell $index Success ${pixels(state.data)} source=${state.dataSource}")
                  }
                },
              )
            },
            sharedTransition = ImageSharedTransitionConfig(
              sharedTransitionScope = this@SharedTransitionLayout,
              animatedContentScope = animatedContentScope,
            ),
          )
        } else {
          val viewerState = rememberImageViewerState(
            initialPage = selectedPage,
            pageCount = { urls.size },
          )
          ImageViewer(
            images = urls,
            modifier = Modifier.fillMaxSize(),
            state = viewerState,
            component = viewerComponent,
            imageOptions = VIEWER_OPTIONS,
            sharedTransition = ImageSharedTransitionConfig(
              sharedTransitionScope = this@SharedTransitionLayout,
              animatedContentScope = animatedContentScope,
            ),
          )
        }
      }
    }
  }

  /** Records every painter the viewer's pages are handed, and the size each one draws at. */
  private val DrawProbePlugin = object : ImagePlugin.PainterPlugin {
    @Composable
    override fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter =
      remember(painter) {
        viewerPainters += IntSize(imageBitmap.width, imageBitmap.height)
        log("viewer painter ${imageBitmap.width}x${imageBitmap.height}")
        ProbePainter(painter)
      }
  }

  private class ProbePainter(private val delegate: Painter) : Painter() {
    override val intrinsicSize: Size get() = delegate.intrinsicSize
    override fun DrawScope.onDraw() {
      with(delegate) { draw(size) }
    }
  }

  private fun pixels(data: Any?): String = when (data) {
    is Bitmap -> "${data.width}x${data.height}"
    is ImageBitmap -> "${data.width}x${data.height}"
    is android.graphics.drawable.BitmapDrawable -> "${data.bitmap.width}x${data.bitmap.height}"
    else -> data?.let { it::class.simpleName }.orEmpty()
  }

  /**
   * Records the tapped page's image for this frame: what it was laid out at, and how wide that
   * layout is on the screen once the shared bounds have scaled it.
   *
   * The viewer pre-composes its neighbours, so the tapped page is the one the shared bounds have
   * placed: the only one with a non empty rect.
   */
  private fun sampleFrame(frame: Int) {
    val nodes = compose
      .onAllNodesWithContentDescription(VIEWER_DESCRIPTION, useUnmergedTree = true)
      .fetchSemanticsNodes()
    val placed = nodes.firstOrNull { it.boundsInRoot.width > 0f } ?: return
    laidOutAt += placed.size
    onScreenSize += IntSize(
      placed.boundsInRoot.width.toInt(),
      placed.boundsInRoot.height.toInt(),
    )
    log(
      "frame $frame laidOut=${placed.size.width}x${placed.size.height} " +
        "onScreen=${placed.boundsInRoot.width.toInt()}x${placed.boundsInRoot.height.toInt()}",
    )
  }

  private fun dump(label: String) {
    println("$MARK $label ----------------------------------------")
    timeline.toList().forEachIndexed { i, line -> println("$MARK $label [$i] $line") }
    println("$MARK $label viewerPainters=${viewerPainters.toList()}")
    println(
      "$MARK $label fetches=" +
        List(SHAPES.size) { "p$it=${server.hitCount("/p$it.png")}" }.joinToString(" "),
    )
  }

  /** Opens the viewer on [cell] with the clock paused and steps [frames] frames. */
  private fun runTransition(label: String, cell: Int, frames: Int = 60) {
    val landscapist = newLandscapist()
    val list = urls()
    var showViewer by mutableStateOf(false)

    compose.setContent {
      CompositionLocalProvider(LocalLandscapist provides landscapist) {
        Screen(urls = list, showViewer = showViewer, onOpen = { showViewer = true })
      }
    }

    compose.waitUntil(20_000) { cellSuccesses.get() >= SHAPES.size }
    compose.waitForIdle()
    log("--- gallery settled ---")

    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("cell-$cell", useUnmergedTree = true).performClick()
    repeat(frames) { frame ->
      compose.mainClock.advanceTimeByFrame()
      sampleFrame(frame)
    }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    dump(label)
  }

  /**
   * The image is measured against the page it is going to fill, not against the bounds the
   * animation happens to be at, and it is measured at that size on every frame.
   */
  @Test
  fun theViewersImageIsMeasuredAgainstTheDestination() {
    runTransition("measure", cell = LANDSCAPE_CELL)

    val sizes = laidOutAt.toList()
    assertTrue("no frame recorded the viewer's image", sizes.size >= 30)
    val distinct = sizes.distinct()
    assertEquals(
      "the viewer's image was laid out at more than one size while the bounds animated, so its " +
        "decode target moves with the animation: $distinct",
      1,
      distinct.size,
    )
    val laidOut = distinct.single()
    assertTrue(
      "the viewer's image was laid out at $laidOut, which is the thumbnail's bounds rather than " +
        "the page it fills",
      laidOut.width >= 1000 && laidOut.height >= 1000,
    )
  }

  /**
   * The image never goes backwards on the screen. A container that resized itself once the image
   * resolved would move the shared element's target and the picture would shrink mid flight.
   */
  @Test
  fun theViewersImageNeverGoesBackwardsOnScreen() {
    runTransition("monotonic", cell = LANDSCAPE_CELL)

    val sizes = onScreenSize.toList()
    assertTrue("no frame recorded the viewer's image", sizes.size >= 30)
    val backwards = sizes.zipWithNext().withIndex()
      .filter { (_, pair) ->
        pair.second.width < pair.first.width - 1 || pair.second.height < pair.first.height - 1
      }
      .map { (index, pair) -> "frame $index: ${pair.first} -> ${pair.second}" }
    assertTrue("the image shrank on screen during the transition: $backwards", backwards.isEmpty())
    assertTrue("the transition never reached the page's width: $sizes", sizes.last().width >= 1000)
  }

  /**
   * The page decodes the image once at the size it fills, whatever the animation is doing.
   *
   * The first painter is whatever the memory cache already held for that url, which is the
   * gallery's cell sized decode; the second is the page's own. A request that followed the
   * animated bounds would produce one per frame instead.
   */
  @Test
  fun theViewersPageDecodesAtThePagesSize() {
    runTransition("decode", cell = LANDSCAPE_CELL)

    val painters = viewerPainters.toList()
    val source = IntSize(SHAPES[LANDSCAPE_CELL].first, SHAPES[LANDSCAPE_CELL].second)
    val forThisImage = painters.filter { it.width * source.height == it.height * source.width }
    assertTrue(
      "the tapped page was never handed a painter for its own image: $painters",
      forThisImage.isNotEmpty(),
    )
    assertTrue(
      "the tapped page was handed ${forThisImage.size} painters, so it decoded once per frame " +
        "of the animation rather than once for the page: $forThisImage",
      forThisImage.size <= 2,
    )
    assertEquals(
      "the page settled on ${forThisImage.last()} rather than on the image at the size the page " +
        "asked for: $forThisImage",
      source,
      forThisImage.last(),
    )
    assertEquals(
      "the tapped image was downloaded more than once",
      1,
      server.hitCount("/p$LANDSCAPE_CELL.png"),
    )
  }

  private companion object {
    const val MARK = "GALLERYTX"
    const val VIEWER_DESCRIPTION = "viewer-image"
    val VIEWER_OPTIONS = ImageOptions(
      contentScale = ContentScale.Fit,
      contentDescription = VIEWER_DESCRIPTION,
    )

    /**
     * Mixed shapes, so a decode at the cell's size and one at the page's size are different
     * numbers rather than the same one twice.
     */
    val SHAPES = listOf(
      1600 to 1200,
      1200 to 1600,
      2000 to 1000,
      1000 to 2000,
      1800 to 1200,
      1200 to 1800,
    )

    /** A wide image in the middle row, so the tap is not on the pager's first page either. */
    const val LANDSCAPE_CELL = 4

    val TINTS = intArrayOf(
      android.graphics.Color.RED,
      android.graphics.Color.GREEN,
      android.graphics.Color.BLUE,
      android.graphics.Color.YELLOW,
      android.graphics.Color.CYAN,
      android.graphics.Color.MAGENTA,
    )
  }
}
