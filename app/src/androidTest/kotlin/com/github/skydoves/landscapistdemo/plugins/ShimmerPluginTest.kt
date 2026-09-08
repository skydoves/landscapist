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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Base and highlight are one colour, so the node is a known colour whatever phase it is in. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ShimmerPluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer
  private lateinit var gate: CountDownLatch

  private val loaded = AtomicBoolean(false)

  @Before
  fun start() {
    server = LocalImageServer()
    gate = CountDownLatch(1)
    server.serve("/held.png", solidPng(BlueFixture), PngContentType, gate = gate)
    // A shimmer repeats forever, so waiting for idle never returns; the test hands out frames.
    composeTestRule.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    gate.countDown()
    server.close()
  }

  @Test
  fun theFlashShimmerCoversTheImageWhileItLoadsAndIsGoneOnceItHas() {
    val loader = visualPluginLoader()

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/held.png") },
          landscapist = loader,
          component = rememberImageComponent {
            +ShimmerPlugin(
              Shimmer.Flash(
                baseColor = ShimmerFixture,
                highlightColor = ShimmerFixture,
              ),
            )
          },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
          onImageStateChanged = { state ->
            loaded.set(state is LandscapistImageState.Success)
          },
        )
      }
    }

    assertTheShimmerShowsThenGivesWayToTheImage()
  }

  @Test
  fun theFadeShimmerStillShowsWhenTheCallerTakesTheSuccessSlot() {
    // A success slot composes the loading content on a different path; both have to shimmer.
    val loader = visualPluginLoader()

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { server.url("/held.png") },
          landscapist = loader,
          component = rememberImageComponent {
            +ShimmerPlugin(
              Shimmer.Fade(
                baseColor = ShimmerFixture,
                highlightColor = ShimmerFixture,
              ),
            )
          },
          modifier = visualImageModifier(),
          requestBuilder = PluginRequestBuilder,
          onImageStateChanged = { state ->
            loaded.set(state is LandscapistImageState.Success)
          },
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

    assertTheShimmerShowsThenGivesWayToTheImage()
  }

  private fun assertTheShimmerShowsThenGivesWayToTheImage() {
    // Frames enough for the placeholder to settle; the response is still held.
    composeTestRule.mainClock.advanceTimeBy(240L)

    val loading = composeTestRule.readVisualPixels()
    assertTrue(
      "the image resolved before the shimmer could be read, so nothing here is about the shimmer",
      !loaded.get(),
    )
    val unshimmered = loading.samples().notMatching(ShimmerFixture)
    assertTrue(
      "the shimmer did not cover the image while it loaded: ${unshimmered.size} of " +
        "${loading.samples().size} pixels were not the shimmer, the first was " +
        "${unshimmered.firstOrNull()}",
      unshimmered.isEmpty(),
    )

    gate.countDown()
    composeTestRule.advanceUntil("the held image to load") { loaded.get() }

    val settled = composeTestRule.readVisualPixels()
    val notImage = settled.samples().notMatching(BlueFixture)
    assertTrue(
      "the shimmer was still on screen after the image loaded, or the image never drew: " +
        "${notImage.size} pixels were not the image, the first was ${notImage.firstOrNull()}",
      notImage.isEmpty(),
    )
  }
}
