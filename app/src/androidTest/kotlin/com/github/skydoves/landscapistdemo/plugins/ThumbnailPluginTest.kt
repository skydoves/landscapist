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
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.thumbnail.ThumbnailPlugin
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ThumbnailPlugin] against the same URL loaded twice at two sizes.
 *
 * The plugin asks the loader for a small version of the image the composable is already loading,
 * and draws that until the real one arrives. Both halves of that are checked: the sizes the loader
 * was actually asked for, which never reach the wire and so are read off a fetcher rather than the
 * server, and what was on screen while the full sized response was held, which has to be a picture
 * with fifteen pixels of detail in it rather than the image itself.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ThumbnailPluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  /** Holds the full sized request, which is anything larger than a preview, until released. */
  private val fetcher = RecordingFetcher(holdLargerThan = 64)

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/detail.png", quadrantPng(), PngContentType)
  }

  @After
  fun stop() {
    fetcher.release()
    server.close()
  }

  @Test
  fun theThumbnailIsOnScreenBeforeTheFullImageAndIsAskedForAtItsOwnSize() {
    val loader = contentPluginLoader(fetcher)
    val state = StateRecorder()
    val url = server.url("/detail.png")
    // Held outside the composition: a plugin rebuilt on every composition is a new key inside the
    // image, and the thumbnail load would start again each time the state changed.
    val component = pluginComponent(ThumbnailPlugin())

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          component = component,
          modifier = contentImageModifier(),
          // No size on the request, because a request that already carries one is passed through
          // untouched and the size the plugin asks for would be dropped.
          requestBuilder = UnsizedRequestBuilder,
          onImageStateChanged = state::record,
        )
      }
    }

    composeTestRule.awaitUntil("the thumbnail to be drawn") {
      composeTestRule.readContentPixels().quadrantCoverage() > 0.3f
    }
    val thumbnailDetail = composeTestRule.readContentPixels().localContrast()

    assertFalse(
      "the full image arrived while it was supposed to be held, so what was drawn was not a " +
        "thumbnail: $state",
      state.isSuccess,
    )
    assertTrue(
      "the thumbnail was not asked for at its own size, the sizes asked for were ${fetcher.sizes}",
      fetcher.sizes.contains(IntSize(15, 15)),
    )
    assertTrue(
      "only the thumbnail size was ever asked for: ${fetcher.sizes}",
      fetcher.sizes.any { it.width > 15 },
    )

    fetcher.release()
    composeTestRule.awaitUntil("the full image to arrive") { state.isSuccess }
    val fullDetail = composeTestRule.readContentPixels().localContrast()

    assertTrue(
      "the full image was no sharper than the thumbnail, so the thumbnail was never replaced or " +
        "was the full image all along: detail was $thumbnailDetail while loading and $fullDetail " +
        "after",
      fullDetail > thumbnailDetail * 2f,
    )
  }

  @Test
  fun withoutThePluginNothingIsOnScreenWhileTheImageLoads() {
    // The control for the test above. The plugin installed here composes on the same state and
    // draws nothing, so the image is on the same drawing path with nothing to show, and both the
    // pixels and the single recorded request say the thumbnail above came from the plugin.
    val loader = contentPluginLoader(fetcher)
    val state = StateRecorder()
    val url = server.url("/detail.png")
    val component = pluginComponent(PlaceholderPlugin.Loading(null))

    composeTestRule.setContent {
      OnBackdrop {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          component = component,
          modifier = contentImageModifier(),
          requestBuilder = UnsizedRequestBuilder,
          onImageStateChanged = state::record,
        )
      }
    }

    composeTestRule.awaitUntil("the image to be asked for") { fetcher.sizes.isNotEmpty() }
    composeTestRule.waitForIdle()
    val covered = composeTestRule.readContentPixels().quadrantCoverage()

    assertFalse("the held image arrived anyway: $state", state.isSuccess)
    assertTrue(
      "something drew over ${(covered * 100).toInt()}% of the node with no thumbnail installed",
      covered < 0.05f,
    )
    assertTrue(
      "a second load was started with no thumbnail plugin installed: ${fetcher.sizes}",
      fetcher.sizes.size == 1,
    )
  }
}
