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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class CrossfadePluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  /** The green channel a fully covered composite can reach on its own, drawn over the backdrop. */
  private val opaqueGreenCeiling = 0.3f

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/red.png", solidPng(RedFixture), PngContentType)
    server.serve("/blue.png", solidPng(BlueFixture), PngContentType)
    // Only this test advances the clock, so an animation can be read at any point along it.
    composeTestRule.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    server.close()
  }

  private fun red(): String = server.url("/red.png")

  private fun blue(): String = server.url("/blue.png")

  @Test
  fun anImageAlreadyInMemoryIsDrawnWholeInTheFrameItFirstAppearsIn() {
    val loader = visualPluginLoader()
    loader.warm(red())

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { red() },
          landscapist = loader,
          component = rememberImageComponent { +CrossfadePlugin(duration = 400) },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    // The clock has not moved since the content was set, so this is the first frame.
    val pixels = composeTestRule.readVisualPixels()
    val wrong = pixels.samples().notMatching(RedFixture)

    assertTrue(
      "the cached image was faded in rather than drawn: ${wrong.size} of " +
        "${pixels.samples().size} pixels were not the image, the first was ${wrong.firstOrNull()}",
      wrong.isEmpty(),
    )
  }

  @Test
  fun anImageThatReplacesOneOnScreenDissolvesOverItWithoutGoingTransparent() {
    val loader = visualPluginLoader()
    loader.warm(red())
    loader.warm(blue())
    val model = mutableStateOf(red())

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { model.value },
          landscapist = loader,
          component = rememberImageComponent { +CrossfadePlugin(duration = 400) },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    assertTrue(
      "the first image was not on screen, so there is nothing for the second to replace",
      composeTestRule.readVisualPixels().centre().matches(RedFixture),
    )

    val frames = replaceModelAndSampleTheFade(model)

    // The backdrop is the only green thing on screen, so the green channel says how much of the
    // node the image stopped covering.
    val transparent = frames.filter { it.green > opaqueGreenCeiling }
    assertTrue(
      "the image went transparent while the new one faded in, ${transparent.size} of " +
        "${frames.size} frames showed the backdrop, the first was ${transparent.firstOrNull()}",
      transparent.isEmpty(),
    )
    // A frame that is neither colour is what only a dissolve produces.
    assertTrue(
      "no frame was part way between the two images, so nothing faded: $frames",
      frames.any { !it.matches(RedFixture) && !it.matches(BlueFixture) },
    )
    assertTrue(
      "the replacing image never arrived, the last frame was ${frames.last()}",
      frames.last().matches(BlueFixture),
    )
  }

  @Test
  fun aCallerSuccessSlotStillCrossfades() {
    // A success slot moves the fade into the composition. Coverage is not asserted here: that
    // path stacks two independently faded composables, so a dip part way through is expected.
    val loader = visualPluginLoader()
    loader.warm(red())
    loader.warm(blue())
    val model = mutableStateOf(red())

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { model.value },
          landscapist = loader,
          component = rememberImageComponent { +CrossfadePlugin(duration = 400) },
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

    assertTrue(
      "the first image was not on screen, so there is nothing for the second to replace",
      composeTestRule.readVisualPixels().centre().matches(RedFixture),
    )

    val frames = replaceModelAndSampleTheFade(model)

    assertTrue(
      "the image with a success slot did not fade, every frame was one colour or the other: " +
        "$frames",
      frames.any { !it.matches(RedFixture) && !it.matches(BlueFixture) },
    )
    assertTrue(
      "the replacing image never arrived, the last frame was ${frames.last()}",
      frames.last().matches(BlueFixture),
    )
  }

  /** Sixteen frames at 40ms reaches past the 400ms fade, so the last frame is the settled one. */
  private fun replaceModelAndSampleTheFade(model: MutableState<String>): List<Color> {
    composeTestRule.runOnUiThread { model.value = blue() }
    return (1..16).map {
      composeTestRule.mainClock.advanceTimeBy(40L)
      composeTestRule.readVisualPixels().centre()
    }
  }
}
