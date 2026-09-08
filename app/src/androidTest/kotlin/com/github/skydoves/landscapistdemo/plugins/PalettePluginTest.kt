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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.kmpalette.palette.graphics.Palette
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.palette.PalettePlugin
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@LargeTest
@RunWith(AndroidJUnit4::class)
class PalettePluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  private val palette = AtomicReference<Palette?>(null)

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/blue.png", solidPng(BlueFixture), PngContentType)
    server.serve("/red.png", solidPng(RedFixture), PngContentType)
    server.serve("/blue.jpg", ImageFixtures.solid(PluginRequestPx, PluginRequestPx, BLUE_ARGB))
    composeTestRule.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    server.close()
  }

  @Test
  fun theDominantColourIsTheColourTheImageIsMadeOf() {
    val loader = visualPluginLoader()

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/blue.png") },
          landscapist = loader,
          component = rememberImageComponent {
            +PalettePlugin { generated -> palette.set(generated) }
          },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    assertTheDominantColourIs(BlueFixture)
  }

  @Test
  fun theDominantColourIsFoundInAJpegToo() {
    // A JPEG has no alpha, which is what the decoder keys the hardware bitmap decision off, and a
    // hardware bitmap has no pixels the palette could read.
    val loader = visualPluginLoader()

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/blue.jpg") },
          landscapist = loader,
          component = rememberImageComponent {
            +PalettePlugin { generated -> palette.set(generated) }
          },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    assertTheDominantColourIs(BlueFixture)
  }

  @Test
  fun thePaletteStillRunsWithAPainterPluginInstalledAlongsideIt() {
    // A crossfade is a painter plugin and a palette a success state plugin, and the two settle
    // how the image is drawn between them. A different colour from the test above, on purpose.
    val loader = visualPluginLoader()

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/red.png") },
          landscapist = loader,
          component = rememberImageComponent {
            +CrossfadePlugin(duration = 200)
            +PalettePlugin { generated -> palette.set(generated) }
          },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    assertTheDominantColourIs(RedFixture)
  }

  private fun assertTheDominantColourIs(expected: Color) {
    composeTestRule.advanceUntil("the palette to be generated") { palette.get() != null }

    val swatch = palette.get()?.dominantSwatch
    assertTrue(
      "the palette was generated but has no dominant swatch at all",
      swatch != null,
    )
    // The quantizer keeps five bits per channel, so the colour comes back rounded down.
    val dominant = Color(swatch!!.rgb)
    assertTrue(
      "the dominant colour was $dominant rather than the $expected the image is made of",
      dominant.matches(expected),
    )

    // Frames enough for the image to settle.
    composeTestRule.mainClock.advanceTimeBy(FrameMs * 20)
    val pixels = composeTestRule.readVisualPixels()
    val wrong = pixels.samples().notMatching(expected)
    assertTrue(
      "the palette was right about an image that was never drawn: ${wrong.size} of " +
        "${pixels.samples().size} pixels were not the image, the first was ${wrong.firstOrNull()}",
      wrong.isEmpty(),
    )
  }

  private companion object {
    /** The same blue as [BlueFixture], as the ARGB int the fixtures take. */
    const val BLUE_ARGB: Int = 0xFF0000FF.toInt()
  }
}
