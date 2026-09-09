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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ZoomablePluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/blue.png", solidPng(BlueFixture), PngContentType)
    server.serve("/red.png", solidPng(RedFixture), PngContentType)
    server.serve("/quadrants.png", quadrantPng(), PngContentType)
    composeTestRule.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    server.close()
  }

  @Test
  fun theImageIsStillDrawnWithZoomableInstalled() {
    val loader = visualPluginLoader()
    loader.warm(server.url("/blue.png"))

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/blue.png") },
          landscapist = loader,
          component = rememberImageComponent { +ZoomablePlugin() },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    val pixels = composeTestRule.readVisualPixels()
    val wrong = pixels.samples().notMatching(BlueFixture)

    assertTrue(
      "the zoomable plugin left the image undrawn: ${wrong.size} of " +
        "${pixels.samples().size} pixels were not the image, the first was ${wrong.firstOrNull()}",
      wrong.isEmpty(),
    )
  }

  @Test
  fun aPinchGestureChangesWhatIsDrawn() {
    // The four quadrant fixture, since a pinch on a solid colour is invisible however well it
    // worked. Pinching into the red quadrant pulls it over the whole node.
    val loader = visualPluginLoader()
    loader.warm(server.url("/quadrants.png"))

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/quadrants.png") },
          landscapist = loader,
          component = rememberImageComponent { +ZoomablePlugin() },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    val before = composeTestRule.readVisualPixels()
    val redBefore = before.redFraction()
    val greenBefore = before.greenFraction()
    assertTrue(
      "the four quadrant fixture was not on screen, so a gesture cannot be shown to have " +
        "changed it: the red quadrant covered ${percent(redBefore)} of the node and the green " +
        "one ${percent(greenBefore)}",
      redBefore > 0.12f && greenBefore > 0.12f,
    )
    assertTrue(
      "the image was already zoomed into the red quadrant before anything was pinched, it " +
        "covered ${percent(redBefore)} of the node",
      redBefore < 0.4f,
    )

    pinchAroundTheRedQuadrant()

    val after = composeTestRule.readVisualPixels()
    val redAfter = after.redFraction()
    assertTrue(
      "the pinch changed nothing that is drawn: the red quadrant covered " +
        "${percent(redBefore)} of the node before and ${percent(redAfter)} after",
      redAfter > 0.6f,
    )
    assertTrue(
      "the pinch left ${percent(after.greenFraction())} of the node green, so it neither " +
        "zoomed away from the green quadrant nor kept the node painted",
      after.greenFraction() < 0.05f,
    )
  }

  @Test
  fun aCrossfadeStillDissolvesWithTheZoomablePluginInstalled() {
    // A wrapping plugin stops the container painting, so the composable crossfade has to run.
    val loader = visualPluginLoader()
    loader.warm(server.url("/red.png"))
    loader.warm(server.url("/blue.png"))
    val model = mutableStateOf(server.url("/red.png"))

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { model.value },
          landscapist = loader,
          component = rememberImageComponent {
            +ZoomablePlugin()
            +CrossfadePlugin(duration = 400)
          },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    assertTrue(
      "the first image was not on screen, so there is nothing for the second to replace",
      composeTestRule.readVisualPixels().centre().matches(RedFixture),
    )

    composeTestRule.runOnUiThread { model.value = server.url("/blue.png") }
    val frames = (1..16).map {
      composeTestRule.mainClock.advanceTimeBy(40L)
      composeTestRule.readVisualPixels().centre()
    }

    assertTrue(
      "no frame held both images at once, so the zoomable plugin cost the crossfade: $frames",
      frames.any { it.isRedBlueDissolve() },
    )
    assertTrue(
      "the replacing image never arrived, the last frame was ${frames.last()}",
      frames.last().matches(BlueFixture),
    )
  }

  /** Both pointers move the same distance in opposite directions, so it is a pure zoom. */
  private fun pinchAroundTheRedQuadrant() {
    composeTestRule.onNodeWithTag(PluginImageTag).performTouchInput {
      // Distances scale with the node, so the gesture is the same at any density.
      val focusX = width * 0.3f
      val focusY = height * 0.3f
      val grip = (width * 0.03f).coerceAtLeast(6f)
      val spread = width * 0.3f
      down(0, Offset(focusX - grip, focusY))
      down(1, Offset(focusX + grip, focusY))
      updatePointerTo(0, Offset(focusX - spread, focusY))
      updatePointerTo(1, Offset(focusX + spread, focusY))
      move()
      up(0)
      up(1)
    }
    // The zoom only reaches the screen on a frame, and the clock only moves when asked.
    composeTestRule.mainClock.advanceTimeBy(FrameMs * 5)
  }

  private fun PixelMap.redFraction(): Float {
    val pixels = samples()
    val red = pixels.count { it.red > 0.5f && it.green < 0.4f && it.blue < 0.4f }
    return red.toFloat() / pixels.size
  }

  /** Counts the fixture's green quadrant and the backdrop alike, since neither should show. */
  private fun PixelMap.greenFraction(): Float {
    val pixels = samples()
    val green = pixels.count { it.green > 0.5f && it.red < 0.4f && it.blue < 0.4f }
    return green.toFloat() / pixels.size
  }

  private fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"
}
