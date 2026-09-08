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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.placeholder.blurhash.BlurHashDecoder
import com.skydoves.landscapist.placeholder.blurhash.BlurHashPlugin
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/**
 * [BlurHashPlugin], with the response held open so the placeholder can be photographed.
 *
 * The plugin's whole promise is that something is on screen before the image is: the assertions
 * are therefore about what the device drew while the socket was still waiting, and the backdrop
 * behind the image is what makes "it drew nothing" a colour rather than an absence.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BlurHashPluginTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  @Before
  fun start() {
    server = LocalImageServer()
  }

  @After
  fun stop() {
    server.close()
  }

  /** The image behind the placeholder, in a colour no placeholder here decodes to. */
  private fun serveHeldImage(gate: CountDownLatch): String {
    server.serve(
      path = "/photo.png",
      body = ImageFixtures.solid(120, 120, Color.Magenta.toArgb(), Bitmap.CompressFormat.PNG),
      contentType = "image/png",
      gate = gate,
    )
    return server.url("/photo.png")
  }

  @Test
  fun theBlurHashIsOnScreenBeforeTheImageAndTheImageReplacesIt() {
    val gate = CountDownLatch(1)
    val url = serveHeldImage(gate)
    val loader = pluginLoader()
    val state = StateRecorder()
    val decoded = checkNotNull(BlurHashDecoder.decode(HASH, 32, 32, 1f)) {
      "the hash under test does not decode, so nothing below would mean anything"
    }

    compose.setContent {
      PluginImage(
        url = url,
        loader = loader,
        component = component(BlurHashPlugin(blurHash = HASH, width = 32, height = 32)),
        onState = state::record,
      )
    }

    compose.waitForIdle()
    val whileLoading = compose.onNodeWithTag(IMAGE_TAG).pixels()

    assertFalse(
      "the image arrived while it was supposed to be held, so nothing here was a placeholder: " +
        "$state",
      state.isSuccess,
    )
    assertColourFar(
      "the blur hash never reached the screen.",
      backdrop,
      whileLoading.centreColour(),
    )
    assertColourNear(
      "what was drawn is not what the hash decodes to.",
      decoded.centreColour(32, 32),
      whileLoading.centreColour(),
    )
    // The hash holds a horizontal ramp, so the two edges are different colours. A flat fill of the
    // right average would pass everything above and is the shape a mistake here would take.
    val leftEdge = whileLoading.averageColour(left = 0.02f, right = 0.12f)
    val rightEdge = whileLoading.averageColour(left = 0.88f, right = 0.98f)
    assertTrue(
      "the placeholder was a flat fill rather than the decoded hash: its left edge was " +
        "${leftEdge.describe()} and its right edge was ${rightEdge.describe()}",
      leftEdge.distanceTo(rightEdge) > 0.15f,
    )

    gate.countDown()
    compose.waitUntil(LOAD_TIMEOUT_MS) { state.isSuccess }

    assertColourNear(
      "the image did not replace the blur hash.",
      Color.Magenta,
      compose.onNodeWithTag(IMAGE_TAG).pixels().centreColour(),
    )
  }

  @Test
  fun aHashThatDoesNotDecodeDrawsNothingRatherThanBringingTheImageDown() {
    // Also the control for every assertion above: with nothing composed, the capture is the
    // backdrop, so a test that reads a colour off this node is reading something a plugin drew.
    val gate = CountDownLatch(1)
    val url = serveHeldImage(gate)
    val loader = pluginLoader()
    val state = StateRecorder()

    compose.setContent {
      PluginImage(
        url = url,
        loader = loader,
        component = component(BlurHashPlugin(blurHash = "not a hash", width = 32, height = 32)),
        onState = state::record,
      )
    }

    compose.waitForIdle()

    assertColourNear(
      "an undecodable hash drew something.",
      backdrop,
      compose.onNodeWithTag(IMAGE_TAG).pixels().centreColour(),
    )

    gate.countDown()
    compose.waitUntil(LOAD_TIMEOUT_MS) { state.isSuccess }

    assertColourNear(
      "the image did not load behind a placeholder that could not decode.",
      Color.Magenta,
      compose.onNodeWithTag(IMAGE_TAG).pixels().centreColour(),
    )
  }

  private companion object {
    /**
     * One row of two components: an orange base colour with a red to amber ramp across it.
     *
     * Written out rather than taken from the BlurHash samples, because the well known ones decode
     * to a mid grey that is within tolerance of the backdrop, and an assertion that cannot tell
     * the placeholder from the empty node behind it is worth nothing.
     */
    private const val HASH = "1ZTMYr@N"
  }
}
