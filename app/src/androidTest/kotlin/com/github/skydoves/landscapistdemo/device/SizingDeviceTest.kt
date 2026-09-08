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

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sizes are compared against a sibling measured the same way, so they hold on any screen. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SizingDeviceTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  /** Built per test, so each starts with an empty memory cache. */
  private lateinit var loader: Landscapist

  @Before fun start() {
    server = LocalImageServer()
    loader = Landscapist.builder().noDiskCache().build()
  }

  @After fun stop() = server.close()

  /** Collects what one image reported and what it measured to. */
  private class Probe {
    val states: MutableList<LandscapistImageState> = mutableListOf()
    var size: IntSize = IntSize.Zero

    val settled: Boolean
      get() = states.any {
        it is LandscapistImageState.Success || it is LandscapistImageState.Failure
      }

    val success: LandscapistImageState.Success?
      get() = states.filterIsInstance<LandscapistImageState.Success>().lastOrNull()

    fun trace(): String = states.joinToString { it::class.simpleName ?: "?" }
  }

  private fun awaitLoaded(vararg probes: Probe) {
    try {
      compose.waitUntil(LOAD_TIMEOUT_MS) { probes.all { probe -> probe.settled } }
    } catch (timeout: ComposeTimeoutException) {
      throw AssertionError(
        "an image never reached a terminal state, saw ${probes.map { it.trace() }}",
        timeout,
      )
    }
    compose.waitForIdle()
    for (probe in probes) {
      val failure = probe.states.filterIsInstance<LandscapistImageState.Failure>().lastOrNull()
      if (failure != null) throw AssertionError("the image failed to load: ${failure.reason}")
    }
  }

  private fun Probe.loaded(): LandscapistImageState.Success =
    success ?: throw AssertionError("no success reached the caller, saw ${trace()}")

  private fun assertSameSize(what: String, expected: IntSize, actual: IntSize) {
    assertTrue(
      "$what was compared against a parent that never laid out, which proves nothing",
      expected.width > 0 && expected.height > 0,
    )
    assertEquals(
      "$what is ${actual.width}px wide, not the ${expected.width}px its parent offered",
      expected.width,
      actual.width,
    )
    assertEquals(
      "$what is ${actual.height}px tall, not the ${expected.height}px its parent offered",
      expected.height,
      actual.height,
    )
  }

  @Test
  fun aSizeModifierIsHonoured() {
    server.serve(PHOTO, ImageFixtures.photo(400, 300))
    val url = server.url(PHOTO)
    val probe = Probe()
    var reference = IntSize.Zero

    compose.setContent {
      Row {
        // A plain box under the same modifier, so the expected size is measured, not computed.
        Box(Modifier.size(120.dp).onGloballyPositioned { reference = it.size })
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.size(120.dp).onGloballyPositioned { probe.size = it.size },
          onImageStateChanged = { probe.states += it },
        )
      }
    }
    awaitLoaded(probe)

    assertSameSize("an image under Modifier.size(120.dp)", reference, probe.size)
  }

  @Test
  fun withNoSizeModifierTheImageFillsItsParent() {
    // The painter's intrinsic size must not reach the layout: a 400x300 image fills a 200.dp
    // parent, as a child Image would.
    server.serve(PHOTO, ImageFixtures.photo(400, 300))
    val url = server.url(PHOTO)
    val probe = Probe()
    var parent = IntSize.Zero

    compose.setContent {
      Box(Modifier.size(200.dp).onGloballyPositioned { parent = it.size }) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.onGloballyPositioned { probe.size = it.size },
          onImageStateChanged = { probe.states += it },
        )
      }
    }
    awaitLoaded(probe)

    assertSameSize("an image with no size modifier", parent, probe.size)
    val loaded = probe.loaded()
    assertTrue(
      "the image measured to its parent but never decoded anything, so the fill proves nothing",
      loaded.originalWidth > 0 && loaded.originalHeight > 0,
    )
  }

  @Test
  fun fillMaxWidthAloneStillFillsTheParent() {
    server.serve(PHOTO, ImageFixtures.photo(400, 300))
    val url = server.url(PHOTO)
    val probe = Probe()
    var parent = IntSize.Zero

    compose.setContent {
      Box(Modifier.size(200.dp).onGloballyPositioned { parent = it.size }) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.fillMaxWidth().onGloballyPositioned { probe.size = it.size },
          onImageStateChanged = { probe.states += it },
        )
      }
    }
    awaitLoaded(probe)

    assertSameSize("an image under fillMaxWidth in a bounded parent", parent, probe.size)
  }

  @Test
  fun anUnboundedHeightFollowsTheImageAspectRatio() {
    // The scrolling column leaves the height unbounded, and the 80x40 source is 2:1.
    server.serve(WIDE, ImageFixtures.photo(80, 40))
    val url = server.url(WIDE)
    val probe = Probe()

    compose.setContent {
      Column(Modifier.width(200.dp).verticalScroll(rememberScrollState())) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.fillMaxWidth().onGloballyPositioned { probe.size = it.size },
          onImageStateChanged = { probe.states += it },
        )
      }
    }
    awaitLoaded(probe)

    val measured = probe.size
    assertTrue("the image never laid out inside the scrolling column", measured.width > 0)
    val expectedHeight = measured.width / 2
    assertTrue(
      "a 2:1 image ${measured.width}px wide measured ${measured.height}px tall, not the " +
        "${expectedHeight}px its own shape asks for",
      abs(measured.height - expectedHeight) <= 1,
    )
    assertTrue(
      "the image took the decoded bitmap's own ${probe.loaded().originalHeight}px height rather " +
        "than the height its measured width implies",
      measured.height != probe.loaded().originalHeight,
    )
  }

  @Test
  fun anAspectRatioModifierIsHonouredAndReachesTheDecode() {
    // A 4:3 source in a 16:9 box, so the ratio the layout settles on is not the image's own.
    val source = 2048
    server.serve(RATIO, ImageFixtures.solid(source, source * 3 / 4, Color.CYAN))
    val url = server.url(RATIO)
    val probe = Probe()

    compose.setContent {
      Box(Modifier.size(200.dp)) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(WIDESCREEN)
            .onGloballyPositioned { probe.size = it.size },
          onImageStateChanged = { probe.states += it },
        )
      }
    }
    awaitLoaded(probe)

    val measured = probe.size
    assertTrue("the image never laid out under Modifier.aspectRatio", measured.width > 0)
    assertRatio(measured)
    val decoded = probe.loaded().originalWidth
    assertTrue(
      "a ${source}px source drawn into a ${measured.width}px wide box was decoded at " +
        "${decoded}px: the box the ratio settled on never reached the request",
      decoded <= source / 2,
    )
    assertTrue(
      "decoded at ${decoded}px for a ${measured.width}px wide box, less than the box draws",
      decoded >= measured.width,
    )
    assertTrue(
      "decoded at ${decoded}px for a ${measured.width}px wide box, more than twice what is " +
        "drawn, so one further halving was available and was not taken",
      decoded < measured.width * 2,
    )
  }

  @Test
  fun anAspectRatioBeatsTheImagesOwnShapeWhereTheHeightIsUnbounded() {
    // The scrolling column leaves the height unbounded, which is where the 2:1 source would win.
    server.serve(WIDE, ImageFixtures.photo(80, 40))
    val url = server.url(WIDE)
    val probe = Probe()

    compose.setContent {
      Column(Modifier.width(200.dp).verticalScroll(rememberScrollState())) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(WIDESCREEN)
            .onGloballyPositioned { probe.size = it.size },
          onImageStateChanged = { probe.states += it },
        )
      }
    }
    awaitLoaded(probe)

    val measured = probe.size
    assertTrue("the image never laid out inside the scrolling column", measured.width > 0)
    assertRatio(measured)
    assertTrue(
      "the image took its own 2:1 shape rather than the ratio the caller asked for",
      abs(measured.height - measured.width / 2) > 1,
    )
  }

  private fun assertRatio(measured: IntSize) {
    val expectedHeight = (measured.width / WIDESCREEN).roundToInt()
    assertTrue(
      "an image under aspectRatio(16:9) measured ${measured.width}x${measured.height}, not the " +
        "${measured.width}x$expectedHeight the ratio asks for",
      abs(measured.height - expectedHeight) <= 1,
    )
  }

  @Test
  fun everyContentScaleStillMeasuresToItsParent() {
    // How the pixels are fitted is a drawing decision, not a layout one.
    server.serve(PHOTO, ImageFixtures.photo(320, 240))
    val url = server.url(PHOTO)
    val scales = listOf(
      "Crop" to ImageOptions(contentScale = ContentScale.Crop),
      "Fit" to ImageOptions(contentScale = ContentScale.Fit),
      "Inside" to ImageOptions(contentScale = ContentScale.Inside),
      "None" to ImageOptions(contentScale = ContentScale.None),
      "FillBounds" to ImageOptions(contentScale = ContentScale.FillBounds),
    )
    val probes = scales.associate { (name, _) -> name to Probe() }
    val parents = mutableMapOf<String, IntSize>()

    compose.setContent {
      Row {
        for ((name, options) in scales) {
          val probe = probes.getValue(name)
          Box(Modifier.size(48.dp).onGloballyPositioned { parents[name] = it.size }) {
            LandscapistImage(
              imageModel = { url },
              landscapist = loader,
              modifier = Modifier.onGloballyPositioned { probe.size = it.size },
              imageOptions = options,
              onImageStateChanged = { probe.states += it },
            )
          }
        }
      }
    }
    awaitLoaded(*probes.values.toTypedArray())

    for ((name, _) in scales) {
      assertSameSize(
        "an image under ContentScale.$name",
        parents[name] ?: IntSize.Zero,
        probes.getValue(name).size,
      )
    }
  }

  @Test
  fun theImageIsDecodedAtTheSizeItIsDrawnAt() {
    // The platform decoder samples in powers of two, so it lands in [slot, 2 * slot): at or above
    // the slot because it may not lose detail the slot can show, and under twice because one more
    // halving would have.
    val source = 2048
    server.serve(BIG, ImageFixtures.solid(source, source, Color.MAGENTA))
    val url = server.url(BIG)
    val probe = Probe()

    compose.setContent {
      Box(Modifier.size(120.dp)) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.onGloballyPositioned { probe.size = it.size },
          onImageStateChanged = { probe.states += it },
        )
      }
    }
    awaitLoaded(probe)

    val drawn = probe.size.width
    val decoded = probe.loaded().originalWidth
    assertTrue("the image never laid out, so there is no drawn size to compare against", drawn > 0)
    assertTrue(
      "a ${source}px source drawn into a ${drawn}px slot was decoded at ${decoded}px: the size " +
        "the layout measured never reached the request",
      decoded <= source / 2,
    )
    assertTrue(
      "decoded at ${decoded}px for a ${drawn}px slot, which is less than the slot draws",
      decoded >= drawn,
    )
    assertTrue(
      "decoded at ${decoded}px for a ${drawn}px slot, more than twice what is drawn, so one " +
        "further halving was available and was not taken",
      decoded < drawn * 2,
    )
  }

  private companion object {
    const val PHOTO = "/photo.jpg"
    const val WIDE = "/wide.jpg"
    const val BIG = "/big.jpg"
    const val RATIO = "/ratio.jpg"
    const val WIDESCREEN = 16f / 9f
    const val LOAD_TIMEOUT_MS = 20_000L
  }
}
