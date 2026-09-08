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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.transformation.blur.BlurTransformationPlugin
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

@LargeTest
@RunWith(AndroidJUnit4::class)
class BlurTransformationPluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/detail.png", quadrantPng(), PngContentType)
    // The same picture as a JPEG, which decodes to a hardware bitmap the blur has to copy back
    // before it can read it.
    server.serve("/detail.jpg", ImageFixtures.photo(PluginRequestPx, PluginRequestPx))
    server.serve("/flat.png", solidPng(BlueFixture), PngContentType)
  }

  @After
  fun stop() {
    server.close()
  }

  @Test
  fun aBlurredImageHasLessDetailThanTheImageItWasMadeFrom() {
    val (plain, blurred) = detailOfPlainAndBlurred(server.url("/detail.png"))

    assertTrue(
      "the unblurred image has no detail to lose, so nothing below could be shown: $plain",
      plain > 0.01f,
    )
    assertTrue(
      "the blur did not smooth the image: detail was $plain unblurred and $blurred blurred",
      blurred < plain * 0.6f,
    )
  }

  @Test
  fun aJpegThatDecodedIntoAHardwareBitmapIsStillBlurred() {
    val (plain, blurred) = detailOfPlainAndBlurred(server.url("/detail.jpg"))

    assertTrue(
      "the JPEG never drew anything to blur: $plain",
      plain > 0.01f,
    )
    assertTrue(
      "a JPEG was not blurred, so the blur cannot read a hardware bitmap: detail was $plain " +
        "unblurred and $blurred blurred",
      blurred < plain * 0.6f,
    )
  }

  @Test
  fun changingTheRadiusChangesTheBlur() {
    val loader = contentPluginLoader()
    val url = server.url("/detail.png")
    loader.warm(url)
    var radius by mutableStateOf(1)

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          component = pluginComponent(BlurTransformationPlugin(radius = radius)),
          modifier = contentImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    val gentle = composeTestRule.readContentPixels().localContrast()
    composeTestRule.runOnIdle { radius = 21 }
    composeTestRule.waitForIdle()
    val heavy = composeTestRule.readContentPixels().localContrast()

    assertTrue(
      "a radius of 1 already flattened the image, or the node is empty, so a larger radius " +
        "cannot be shown to do any more: detail was $gentle",
      gentle > 0.005f,
    )
    assertTrue(
      "the larger radius did not blur any further, so the first result was kept: detail was " +
        "$gentle at radius 1 and $heavy at radius 21",
      heavy < gentle * 0.6f,
    )
  }

  @Test
  fun aBlurStillRunsWithALoadingPlaceholderInstalled() {
    // Two kinds of plugin at once: the drawing path is chosen from the plugins installed.
    val gate = CountDownLatch(1)
    server.serve("/held.png", quadrantPng(), PngContentType, gate = gate)
    val url = server.url("/held.png")
    val loader = contentPluginLoader()
    val plain = StateRecorder()
    val blurred = StateRecorder()
    val placeholder = solidPng(BlueFixture).decodeToImageBitmap()

    composeTestRule.setContent {
      OnBackdrop {
        Column {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            component = pluginComponent(IdentityPainterPlugin),
            modifier = contentImageModifier(PlainTag),
            requestBuilder = PluginRequestBuilder,
            onImageStateChanged = plain::record,
          )
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            component = pluginComponent(
              BlurTransformationPlugin(radius = 20),
              PlaceholderPlugin.Loading(placeholder),
            ),
            modifier = contentImageModifier(BlurredTag),
            requestBuilder = PluginRequestBuilder,
            onImageStateChanged = blurred::record,
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    val held = composeTestRule.readContentPixels(BlurredTag).centre()
    val neighbour = composeTestRule.readContentPixels(PlainTag).centre()

    assertTrue(
      "the placeholder did not render beside a painter plugin, ${held.describe()} was drawn",
      held.matches(BlueFixture),
    )
    assertTrue(
      "the image with no placeholder drew ${neighbour.describe()} while the response was held",
      neighbour.isBackdrop(),
    )

    gate.countDown()
    composeTestRule.awaitUntil("both images to arrive") { plain.isSuccess && blurred.isSuccess }

    val plainPixels = composeTestRule.readContentPixels(PlainTag)
    val blurredPixels = composeTestRule.readContentPixels(BlurredTag)
    val plainDetail = plainPixels.localContrast()
    val blurredDetail = blurredPixels.localContrast()
    val settled = blurredPixels.centre()

    assertTrue(
      "the placeholder is still on screen after the image arrived: ${settled.describe()}",
      !settled.matches(BlueFixture),
    )
    assertTrue(
      "the blur stopped running once a loading plugin was installed beside it: detail was " +
        "$plainDetail unblurred and $blurredDetail blurred",
      blurredDetail < plainDetail * 0.6f,
    )
  }

  @Test
  fun aRadiusOfTwentyFourStillRenders() {
    // The plugin splits a radius with `(radius + 1) % 25`, so 24 asks the toolkit for a pass of
    // radius 0, which it refuses.
    val loader = contentPluginLoader()
    val url = server.url("/flat.png")
    loader.warm(url)

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          component = pluginComponent(BlurTransformationPlugin(radius = 24)),
          modifier = contentImageModifier(),
          requestBuilder = PluginRequestBuilder,
        )
      }
    }

    // A blur of a flat colour is the same flat colour, so the radius cannot change what this reads.
    val pixels = composeTestRule.readContentPixels()
    val wrong = pixels.samples().notMatching(BlueFixture)

    assertTrue(
      "a radius of 24 drew ${wrong.size} of ${pixels.samples().size} pixels wrong, the first was " +
        "${wrong.firstOrNull()?.describe()}",
      wrong.isEmpty(),
    )
  }

  /** The control carries a painter plugin too, so both are drawn by the same path. */
  private fun detailOfPlainAndBlurred(url: String): Pair<Float, Float> {
    val loader = contentPluginLoader()
    loader.warm(url)

    composeTestRule.setContent {
      OnBackdrop {
        Column {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            component = pluginComponent(IdentityPainterPlugin),
            modifier = contentImageModifier(PlainTag),
            requestBuilder = PluginRequestBuilder,
          )
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            component = pluginComponent(BlurTransformationPlugin(radius = 20)),
            modifier = contentImageModifier(BlurredTag),
            requestBuilder = PluginRequestBuilder,
          )
        }
      }
    }

    return composeTestRule.readContentPixels(PlainTag).localContrast() to
      composeTestRule.readContentPixels(BlurredTag).localContrast()
  }

  private companion object {
    private const val PlainTag = "plainImage"
    private const val BlurredTag = "blurredImage"
  }
}

/** Changes nothing, so a control is drawn by the path a blur is drawn by. */
private object IdentityPainterPlugin : ImagePlugin.PainterPlugin {

  @Composable
  override fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter = painter
}
