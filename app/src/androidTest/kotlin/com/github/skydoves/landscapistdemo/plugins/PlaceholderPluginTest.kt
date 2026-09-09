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
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/** Loading is blue, failure red, the image magenta, and a node that drew nothing is green. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PlaceholderPluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  private val gate = CountDownLatch(1)

  private val imageColour = Color.Magenta

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/held.png", solidPng(imageColour), PngContentType, gate = gate)
    server.fail("/missing.png", status = 404)
  }

  @After
  fun stop() {
    gate.countDown()
    server.close()
  }

  private fun placeholders() = pluginComponent(
    PlaceholderPlugin.Loading(solidPng(BlueFixture).decodeToImageBitmap()),
    PlaceholderPlugin.Failure(solidPng(RedFixture).decodeToImageBitmap()),
  )

  @Test
  fun theLoadingPlaceholderIsOnScreenUntilTheImageArrives() {
    val loader = contentPluginLoader()
    val state = StateRecorder()
    val component = placeholders()

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/held.png") },
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
    val wrongWhileLoading = whileLoading.samples().notMatching(BlueFixture)

    assertFalse(
      "the image arrived while it was supposed to be held: $state",
      state.isSuccess,
    )
    assertTrue(
      "the loading placeholder did not cover the image while the response was held: " +
        "${wrongWhileLoading.size} of ${whileLoading.samples().size} pixels were not the " +
        "placeholder, the first was ${wrongWhileLoading.firstOrNull()?.describe()}",
      wrongWhileLoading.isEmpty(),
    )

    gate.countDown()
    composeTestRule.awaitUntil("the image to arrive") { state.isSuccess }

    val settled = composeTestRule.readContentPixels()
    val wrong = settled.samples().notMatching(imageColour)

    assertTrue(
      "the image did not replace the loading placeholder: ${wrong.size} of " +
        "${settled.samples().size} pixels were not the image, the first was " +
        "${wrong.firstOrNull()?.describe()}",
      wrong.isEmpty(),
    )
  }

  @Test
  fun theFailurePlaceholderIsOnScreenAfterANotFound() {
    val loader = contentPluginLoader()
    val state = StateRecorder()
    val component = placeholders()

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/missing.png") },
          landscapist = loader,
          component = component,
          modifier = contentImageModifier(),
          requestBuilder = PluginRequestBuilder,
          onImageStateChanged = state::record,
        )
      }
    }

    composeTestRule.awaitUntil("the load to fail") { state.isFailure }
    composeTestRule.waitForIdle()

    val settled = composeTestRule.readContentPixels()
    val wrong = settled.samples().notMatching(RedFixture)

    assertTrue(
      "the server was never asked for the missing image",
      server.hitCount("/missing.png") > 0,
    )
    assertTrue(
      "the failure placeholder was not drawn after a 404: ${wrong.size} of " +
        "${settled.samples().size} pixels were not it, the first was " +
        "${wrong.firstOrNull()?.describe()}",
      wrong.isEmpty(),
    )
  }
}
