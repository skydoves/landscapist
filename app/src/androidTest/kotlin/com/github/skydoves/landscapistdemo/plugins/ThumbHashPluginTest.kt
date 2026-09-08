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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.placeholder.thumbhash.ThumbHashDecoder
import com.skydoves.landscapist.placeholder.thumbhash.ThumbHashPlugin
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

@LargeTest
@RunWith(AndroidJUnit4::class)
class ThumbHashPluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  private val gate = CountDownLatch(1)

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/held.png", solidPng(BlueFixture), PngContentType, gate = gate)
  }

  @After
  fun stop() {
    gate.countDown()
    server.close()
  }

  private fun held(): String = server.url("/held.png")

  @Test
  fun theThumbHashIsOnScreenBeforeTheImageAndTheImageReplacesIt() {
    val loader = contentPluginLoader()
    val state = StateRecorder()
    val plugin = checkNotNull(ThumbHashPlugin.fromBase64(Hash)) {
      "the plugin could not read the hash from its own documentation"
    }
    val decoded = checkNotNull(ThumbHashDecoder.decodeBase64(Hash)) {
      "the hash under test does not decode, so nothing below would mean anything"
    }
    val expected = decoded.toArgbIntArray().centreColour(decoded.width, decoded.height)
    val component = pluginComponent(plugin)

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { held() },
          landscapist = loader,
          component = component,
          modifier = contentImageModifier(),
          requestBuilder = PluginRequestBuilder,
          onImageStateChanged = state::record,
        )
      }
    }

    composeTestRule.waitForIdle()
    val whileLoading = composeTestRule.readContentPixels()
    val centre = whileLoading.centre()

    assertFalse(
      "the image arrived while it was supposed to be held, so nothing here was a placeholder: " +
        "$state",
      state.isSuccess,
    )
    assertFalse(
      "the thumb hash never reached the screen, the node was still the backdrop",
      centre.matches(Backdrop, tolerance = 0.15f),
    )
    assertTrue(
      "what was drawn is not what the hash decodes to: the screen held ${centre.describe()} " +
        "where the decoder produced ${expected.describe()}",
      centre.matches(expected, tolerance = 0.1f),
    )
    // The hash is a bright sky over a dark foreground, so a flat fill would pass the above.
    val top = whileLoading.at(x = 0.5f, y = 0.05f)
    val bottom = whileLoading.at(x = 0.5f, y = 0.95f)
    assertFalse(
      "the placeholder was a flat fill rather than the decoded hash: its top was " +
        "${top.describe()} and its bottom was ${bottom.describe()}",
      top.matches(bottom, tolerance = 0.15f),
    )

    gate.countDown()
    composeTestRule.awaitUntil("the image to arrive") { state.isSuccess }

    val settled = composeTestRule.readContentPixels()
    val wrong = settled.samples().notMatching(BlueFixture)
    assertTrue(
      "the image did not replace the thumb hash: ${wrong.size} of ${settled.samples().size} " +
        "pixels were not the image, the first was ${wrong.firstOrNull()?.describe()}",
      wrong.isEmpty(),
    )
  }

  @Test
  fun aHashTooShortToDecodeDrawsNothingRatherThanBringingTheImageDown() {
    val loader = contentPluginLoader()
    val state = StateRecorder()
    val component = pluginComponent(ThumbHashPlugin(byteArrayOf(1, 2)))

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { held() },
          landscapist = loader,
          component = component,
          modifier = contentImageModifier(),
          requestBuilder = PluginRequestBuilder,
          onImageStateChanged = state::record,
        )
      }
    }

    composeTestRule.waitForIdle()
    val covered = composeTestRule.readContentPixels().covered()

    assertTrue(
      "a hash with no image in it drew over ${(covered * 100).toInt()}% of the node",
      covered < 0.01f,
    )

    gate.countDown()
    composeTestRule.awaitUntil("the image to arrive") { state.isSuccess }

    val wrong = composeTestRule.readContentPixels().samples().notMatching(BlueFixture)
    assertTrue(
      "the image did not load behind a placeholder that could not decode, the first wrong pixel " +
        "was ${wrong.firstOrNull()?.describe()}",
      wrong.isEmpty(),
    )
  }

  private companion object {
    /** The hash from the plugin's own documentation. */
    private const val Hash = "1QcSHQRnh493V4dIh4eXh1h4kJUI"
  }
}
