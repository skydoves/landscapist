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

/**
 * [CrossfadePlugin] against real bytes on a real screen.
 *
 * The plugin has two jobs that pull against each other. An image the viewer is already looking at,
 * because it came out of the memory cache, must appear whole in the frame the composable first
 * draws: fading that one in is the blink a crossfade exists to prevent. An image that replaces one
 * already on screen must dissolve over it, and the composite must stay covered the whole way, which
 * is what fading the new one in over nothing would lose.
 *
 * Which of those two runs depends on how the image is drawn. With nothing composed inside it the
 * container fades the painter itself; hand the painter to a caller's success slot instead and the
 * fade has to move into the composition. Both are covered here, because deciding it from "could
 * this image have faded a painter" rather than "is it going to" once left the second with no
 * crossfade at all.
 */
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
    // Nothing advances the clock but this test, so a frame only happens when it asks for one and
    // an animation can be read at any point along it.
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

    // The clock has not moved since the content was set, so no animation has had a frame to run in.
    // Whatever is on screen is the first frame, and a faded one would be mostly backdrop.
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

    // The arriving image dissolves over the one it replaces, which is drawn underneath at full
    // strength. Fading it in over nothing instead makes the image dip through transparent on its
    // way in, and the backdrop is the only green thing on screen, so the green channel says how
    // much of the node the image stopped covering.
    val transparent = frames.filter { it.green > opaqueGreenCeiling }
    assertTrue(
      "the image went transparent while the new one faded in, ${transparent.size} of " +
        "${frames.size} frames showed the backdrop, the first was ${transparent.firstOrNull()}",
      transparent.isEmpty(),
    )
    // Opacity alone cannot tell a dissolve from no crossfade at all, since an image that simply
    // appears covers the node on every frame too. A frame that is neither colour is what only a
    // dissolve produces.
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
    // A success slot means the container cannot draw the image itself, so the painter cannot be the
    // thing that fades and the composable crossfade has to run instead. Nothing is claimed here
    // about the composite staying covered: that path stacks two independently faded composables,
    // so a dip part way through is what it is built out of.
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

  /**
   * Points [model] at the blue image and reads the middle pixel on each frame of the fade.
   *
   * The animation runs on the test clock, so it only moves when this asks for a frame. Sixteen of
   * them at 40ms reaches past the 400ms the saturation takes, so the last frame is the settled one
   * rather than a shade short of it.
   */
  private fun replaceModelAndSampleTheFade(model: MutableState<String>): List<Color> {
    composeTestRule.runOnUiThread { model.value = blue() }
    return (1..16).map {
      composeTestRule.mainClock.advanceTimeBy(40L)
      composeTestRule.readVisualPixels().centre()
    }
  }
}
