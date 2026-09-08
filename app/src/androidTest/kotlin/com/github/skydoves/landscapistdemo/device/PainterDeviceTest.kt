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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.core.network.KtorImageFetcher
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.image.rememberLandscapistImagePainter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** The painter a caller draws in their own `Image`: no container, no slot, one layout node. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PainterDeviceTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  /** Built per test, so each starts with an empty memory cache. */
  private lateinit var loader: Landscapist

  /** Pure green, and the fixture has no green in it, so an unpainted pixel reads as backdrop. */
  private val backdrop = Color(0xFF00FF00)

  private val fixture = Color(0xFFFF0000)

  @Before fun start() {
    server = LocalImageServer()
    loader = Landscapist.builder().noDiskCache().build()
    server.serve(PHOTO, solidPng(), PNG)
  }

  @After fun stop() = server.close()

  private fun url(): String = server.url(PHOTO)

  /** PNG, not JPEG: lossless, so a pixel read back is the colour that was served. */
  private fun solidPng(): ByteArray = ImageFixtures.solid(
    width = SOURCE_PX,
    height = SOURCE_PX,
    color = fixture.toArgb(),
    format = Bitmap.CompressFormat.PNG,
  )

  /** The real network fetcher, with the size every request asked for recorded. */
  private class RecordingFetcher : ImageFetcher {
    private val delegate = KtorImageFetcher.create()
    val sizes: MutableList<Pair<Int?, Int?>> = CopyOnWriteArrayList()

    override fun canHandle(model: Any?): Boolean = delegate.canHandle(model)

    override suspend fun fetch(request: ImageRequest): FetchResult {
      sizes += request.targetWidth to request.targetHeight
      return delegate.fetch(request)
    }
  }

  private fun recordingLoader(fetcher: RecordingFetcher): Landscapist =
    Landscapist.builder().noDiskCache().fetcher(fetcher).build()

  /** Loads the image into the memory cache before any composable exists. */
  private fun warm(url: String) {
    val result = runBlocking {
      loader.load(
        ImageRequest.builder().model(url).diskCachePolicy(CachePolicy.DISABLED).build(),
      ).first { it is ImageResult.Success || it is ImageResult.Failure }
    }
    assertTrue("could not warm $url into the memory cache: $result", result is ImageResult.Success)
  }

  @Composable
  private fun OnBackdrop(content: @Composable () -> Unit) {
    Box(
      modifier = Modifier.fillMaxSize().background(backdrop),
      contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }

  private fun pixels(): PixelMap = compose.onNodeWithTag(IMAGE).captureToImage().toPixelMap()

  /** How much of the node the image covers, sampled inside the anti-aliased edge. */
  private fun PixelMap.covered(): Float {
    var painted = 0
    var total = 0
    var y = STEP
    while (y < height - STEP) {
      var x = STEP
      while (x < width - STEP) {
        // The backdrop is the only green thing on screen.
        if (this[x, y].green < 0.5f) painted++
        total++
        x += STEP
      }
      y += STEP
    }
    return if (total == 0) 0f else painted.toFloat() / total
  }

  /** Fails rather than hanging when the painter never resolves an image. */
  private fun awaitSuccess(states: List<LandscapistImageState>) {
    try {
      compose.waitUntil(LOAD_TIMEOUT_MS) {
        states.any { it is LandscapistImageState.Success || it is LandscapistImageState.Failure }
      }
    } catch (timeout: ComposeTimeoutException) {
      throw AssertionError("the painter never settled, it saw $states", timeout)
    }
    compose.waitForIdle()
    assertTrue(
      "the painter reported a failure rather than an image: $states",
      states.none { it is LandscapistImageState.Failure },
    )
  }

  @Test
  fun aCachedImageIsDrawnOnTheFirstFrame() {
    // The clock never moves after the content is set, so the capture below is the first frame.
    compose.mainClock.autoAdvance = false
    warm(url())

    compose.setContent {
      OnBackdrop {
        Image(
          painter = rememberLandscapistImagePainter(
            model = url(),
            landscapist = loader,
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          ),
          contentDescription = null,
          modifier = Modifier.size(IMAGE_DP.dp).testTag(IMAGE),
        )
      }
    }

    val covered = pixels().covered()
    assertTrue(
      "the cached image was not drawn in the frame the painter first appeared in, it covered " +
        "$covered of the node",
      covered > COVERED,
    )
  }

  @Test
  fun anImageThatIsNotCachedIsLoadedAndDrawn() {
    val states = CopyOnWriteArrayList<LandscapistImageState>()

    compose.setContent {
      OnBackdrop {
        Image(
          painter = rememberLandscapistImagePainter(
            model = url(),
            landscapist = loader,
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
            onImageStateChanged = { states += it },
          ),
          contentDescription = null,
          modifier = Modifier.size(IMAGE_DP.dp).testTag(IMAGE),
        )
      }
    }
    awaitSuccess(states)

    val covered = pixels().covered()
    assertTrue(
      "the loaded image was never drawn, it covered $covered of the node",
      covered > COVERED,
    )
  }

  @Test
  fun theImageIsAskedForAtTheSizeItIsDrawnAt() {
    val fetcher = RecordingFetcher()
    val recording = recordingLoader(fetcher)
    val states = CopyOnWriteArrayList<LandscapistImageState>()
    var drawn = IntSize.Zero

    compose.setContent {
      OnBackdrop {
        Image(
          painter = rememberLandscapistImagePainter(
            model = url(),
            landscapist = recording,
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
            onImageStateChanged = { states += it },
          ),
          contentDescription = null,
          modifier = Modifier
            .size(IMAGE_DP.dp)
            .onGloballyPositioned { drawn = it.size }
            .testTag(IMAGE),
        )
      }
    }
    awaitSuccess(states)

    assertTrue(
      "the image never laid out, so there is no drawn size to compare against",
      drawn.width > 0,
    )
    assertEquals(
      "the painter asked for the image at ${fetcher.sizes} rather than the " +
        "${drawn.width}x${drawn.height} it is drawn at",
      listOf(drawn.width to drawn.height),
      fetcher.sizes.distinct(),
    )
  }

  @Test
  fun thePainterKeepsItsIdentityWhileTheImageResolves() {
    val states = CopyOnWriteArrayList<LandscapistImageState>()
    val painters = CopyOnWriteArrayList<Painter>()

    compose.setContent {
      OnBackdrop {
        val painter = rememberLandscapistImagePainter(
          model = url(),
          landscapist = loader,
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { states += it },
        )
        painters += painter
        Image(
          painter = painter,
          contentDescription = null,
          modifier = Modifier.size(IMAGE_DP.dp).testTag(IMAGE),
        )
      }
    }
    awaitSuccess(states)

    assertTrue("the painter was only ever composed once, so nothing was proven", painters.size > 1)
    for (painter in painters) {
      assertSame("the painter was replaced when the image resolved", painters.first(), painter)
    }
    assertTrue(
      "the state callback never reported a success",
      states.any { it is LandscapistImageState.Success },
    )
  }

  @Test
  fun aPainterTheCallerNeverBoundsStillLoads() {
    // A painter with no image has no intrinsic size, so an unbounded caller never gives it one.
    val fetcher = RecordingFetcher()
    val recording = recordingLoader(fetcher)
    val states = CopyOnWriteArrayList<LandscapistImageState>()

    compose.setContent {
      // Width from the parent, height from the image, so with no image it is zero high.
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Image(
          painter = rememberLandscapistImagePainter(
            model = url(),
            landscapist = recording,
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
            onImageStateChanged = { states += it },
          ),
          contentDescription = null,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    awaitSuccess(states)

    assertTrue(
      "the painter never loaded, it saw $states",
      states.any { it is LandscapistImageState.Success },
    )
    assertEquals(
      "a painter that was never drawn at a size did not fall back to the image's own size",
      listOf<Pair<Int?, Int?>>(null to null),
      fetcher.sizes.distinct(),
    )
  }

  private companion object {
    const val PHOTO = "/painter.png"
    const val PNG = "image/png"
    const val IMAGE = "landscapistPainterImage"
    const val SOURCE_PX = 240
    const val IMAGE_DP = 96
    const val STEP = 4
    const val COVERED = 0.9f
    const val LOAD_TIMEOUT_MS = 20_000L
  }
}
