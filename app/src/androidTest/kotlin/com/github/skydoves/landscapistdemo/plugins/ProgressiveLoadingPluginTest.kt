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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.placeholder.progressive.ProgressiveLoadingPlugin
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The plugin never reads its blur radius or transition, so only the preview is testable. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ProgressiveLoadingPluginTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var server: LocalImageServer

  /** Anything larger than a preview is the full sized request. */
  private val fetcher = RecordingFetcher(holdLargerThan = 64)

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve("/first.png", quadrantPng(), PngContentType)
    server.serve("/second.png", quadrantPng(), PngContentType)
  }

  @After
  fun stop() {
    fetcher.release()
    server.close()
  }

  @Test
  fun thePreviewIsOnScreenBeforeTheImageAndTheImageReplacesIt() {
    val loader = contentPluginLoader(fetcher)
    val state = StateRecorder()
    val url = server.url("/first.png")
    // Outside the composition: a plugin rebuilt each time would restart the preview load.
    val component = pluginComponent(ProgressiveLoadingPlugin())

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

    composeTestRule.awaitUntil("the preview to be drawn") {
      composeTestRule.readContentPixels().quadrantCoverage() > 0.3f
    }
    val previewDetail = composeTestRule.readContentPixels().localContrast()

    assertFalse(
      "the image arrived while it was supposed to be held, so what was drawn was not a preview: " +
        "$state",
      state.isSuccess,
    )
    assertTrue(
      "the preview was not asked for at the size the plugin names, the sizes asked for were " +
        "${fetcher.sizes}",
      fetcher.sizes.contains(IntSize(32, 32)),
    )

    fetcher.release()
    composeTestRule.awaitUntil("the image to arrive") { state.isSuccess }
    val fullDetail = composeTestRule.readContentPixels().localContrast()

    assertTrue(
      "the image was no sharper than the preview, so the preview was never replaced or was the " +
        "image all along: detail was $previewDetail while loading and $fullDetail after",
      fullDetail > previewDetail * 2f,
    )
  }

  @Test
  fun theTunedFactoriesAskForTheSamePreviewAsTheDefaultOne() {
    // Two URLs rather than one: two loads of the same image at the same size are coalesced into
    // a single fetch, and only one would be seen.
    val loader = contentPluginLoader(fetcher)
    val first = StateRecorder()
    val second = StateRecorder()
    val byDefault = pluginComponent(ProgressiveLoadingPlugin.default())
    val bySmooth = pluginComponent(ProgressiveLoadingPlugin.smooth())

    composeTestRule.setContent {
      OnBackdrop {
        Column {
          LandscapistImage(
            imageModel = { server.url("/first.png") },
            landscapist = loader,
            component = byDefault,
            modifier = contentImageModifier(FirstTag),
            requestBuilder = UnsizedRequestBuilder,
            onImageStateChanged = first::record,
          )
          LandscapistImage(
            imageModel = { server.url("/second.png") },
            landscapist = loader,
            component = bySmooth,
            modifier = contentImageModifier(SecondTag),
            requestBuilder = UnsizedRequestBuilder,
            onImageStateChanged = second::record,
          )
        }
      }
    }

    composeTestRule.awaitUntil("both previews to be drawn") {
      composeTestRule.readContentPixels(FirstTag).quadrantCoverage() > 0.3f &&
        composeTestRule.readContentPixels(SecondTag).quadrantCoverage() > 0.3f
    }

    assertFalse("the first image arrived anyway: $first", first.isSuccess)
    assertFalse("the second image arrived anyway: $second", second.isSuccess)
    val previews = fetcher.fetches.filter { it.size == IntSize(32, 32) }
    assertTrue(
      "the default factory did not ask for a preview: ${fetcher.fetches}",
      previews.any { it.model.endsWith("/first.png") },
    )
    assertTrue(
      "the smooth factory asked for a different preview to the default one: ${fetcher.fetches}",
      previews.any { it.model.endsWith("/second.png") },
    )
  }

  private companion object {
    private const val FirstTag = "firstImage"
    private const val SecondTag = "secondImage"
  }
}
