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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.image.LandscapistImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class CircularRevealPluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  private val revealMs = 600

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/blue.png", solidPng(BlueFixture), PngContentType)
    composeTestRule.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    server.close()
  }

  @Test
  fun theRevealGrowsFromTheMiddleAndEndsCoveringTheNode() {
    val loader = visualPluginLoader()
    loader.warm(server.url("/blue.png"))

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/blue.png") },
          landscapist = loader,
          component = rememberImageComponent { +CircularRevealPlugin(duration = revealMs) },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    assertTheRevealAnimates()
  }

  @Test
  fun theRevealStillRunsWhenTheCallerTakesThePainter() {
    // A success slot takes the painter off the container, so the plugin must still reach the draw.
    val loader = visualPluginLoader()
    loader.warm(server.url("/blue.png"))

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/blue.png") },
          landscapist = loader,
          component = rememberImageComponent { +CircularRevealPlugin(duration = revealMs) },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
          success = { _, painter ->
            Image(
              painter = painter,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          },
        )
      }
    }

    assertTheRevealAnimates()
  }

  /** The first read is the frame the composable drew, with the image already in memory. */
  private fun assertTheRevealAnimates() {
    val first = composeTestRule.readVisualPixels()
    composeTestRule.mainClock.advanceTimeBy(revealMs / 3L)
    val middle = composeTestRule.readVisualPixels()
    composeTestRule.mainClock.advanceTimeBy(revealMs * 2L)
    val last = composeTestRule.readVisualPixels()

    assertTrue(
      "the reveal was already complete on the frame it started on, so it never animated: " +
        describe(first),
      first.coveredBy(BlueFixture) < 0.5f,
    )
    assertTrue(
      "the reveal never grew, so the painter's radius did not reach the drawing: " +
        "${describe(first)} then ${describe(middle)}",
      middle.coveredBy(BlueFixture) > first.coveredBy(BlueFixture),
    )
    // A circle, not a rectangle fading up: the middle is revealed long before a corner.
    assertTrue(
      "the middle of the node was not revealed first, it was ${middle.centre()}",
      middle.centre().matches(BlueFixture),
    )
    assertTrue(
      "the reveal reached the corners at the same time as the middle, so it is not circular: " +
        "the corner was ${middle.corner()}",
      middle.corner().isBackdrop(),
    )
    assertTrue(
      "the reveal never finished, it ended ${describe(last)}",
      last.coveredBy(BlueFixture) > 0.99f,
    )
    val wrong = last.samples().notMatching(BlueFixture)
    assertTrue(
      "the finished reveal did not leave the image as it is: ${wrong.size} pixels were not " +
        "the image, the first was ${wrong.firstOrNull()}",
      wrong.isEmpty(),
    )
  }

  /** The image's own colour, so a node painted anything else does not read as revealed. */
  private fun describe(pixels: PixelMap): String =
    "${(pixels.coveredBy(BlueFixture) * 100).toInt()}% covered by the image"
}
