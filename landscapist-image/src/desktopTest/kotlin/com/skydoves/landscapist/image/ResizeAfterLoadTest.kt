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
package com.skydoves.landscapist.image

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The size a slot reports to its parent, from the frame it appears in to the frame after it loads.
 *
 * A slot that measures one way while it loads and another once it has the image is a relayout the
 * user watches happen: everything under it in a scrolling column slides. These record every size
 * the slot took, in order, rather than only the settled one.
 */
@OptIn(ExperimentalTestApi::class)
class ResizeAfterLoadTest {

  private val url = "https://example.com/poster.png"

  /** Held open so the loading state is a state the test can measure rather than a lost frame. */
  private class Gate {
    val opened = CompletableDeferred<Unit>()
  }

  private fun loader(width: Int, height: Int, gate: Gate?): Landscapist = Landscapist.builder()
    .noDiskCache()
    .fetcher(
      object : ImageFetcher {
        override fun canHandle(model: Any?): Boolean = true
        override suspend fun fetch(request: ImageRequest): FetchResult {
          gate?.opened?.await()
          return FetchResult.Success(byteArrayOf(1), mimeType = "image/png")
        }
      },
    )
    .decoder(
      object : ImageDecoder {
        override suspend fun decode(
          data: ByteArray,
          mimeType: String?,
          targetWidth: Int?,
          targetHeight: Int?,
          config: LandscapistConfig,
        ): DecodeResult = DecodeResult.Success(ImageBitmap(width, height), width, height)
      },
    )
    .build()

  /**
   * Every size the slot took, in order, with repeats collapsed.
   *
   * The column is 200 wide and scrolls, which is the demo's screen: the width is bounded and the
   * height is the slot's own to choose.
   */
  private fun sizesAcrossLoad(
    imageWidth: Int,
    imageHeight: Int,
    slotModifier: Modifier,
    component: @Composable () -> ImageComponent = { rememberImageComponent {} },
  ): List<IntSize> {
    val gate = Gate()
    val landscapist = loader(imageWidth, imageHeight, gate)
    val seen = mutableListOf<IntSize>()
    runComposeUiTest {
      setContent {
        Column(Modifier.width(200.dp).verticalScroll(rememberScrollState())) {
          LandscapistImage(
            imageModel = { url },
            landscapist = landscapist,
            modifier = slotModifier.onGloballyPositioned {
              if (seen.lastOrNull() != it.size) seen += it.size
            },
            component = component(),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
      waitForIdle()
      // Everything before this point is what the user looks at while the image is in flight.
      gate.opened.complete(Unit)
      waitForIdle()
      // A crossfade or a reveal keeps composing frames; the layout has to settle regardless.
      mainClock.advanceTimeBy(2_000)
      waitForIdle()
    }
    return seen
  }

  @Test
  fun `the gif slot grows from nothing once the image lands`() {
    // The demo's gif: fillMaxWidth in a scrolling column, no plugins, so the node path.
    val sizes = sizesAcrossLoad(400, 300, Modifier.fillMaxWidth())

    assertEquals(
      listOf(IntSize(200, 0), IntSize(200, 150)),
      sizes,
      "unexpected size sequence: $sizes",
    )
  }

  @Test
  fun `the gif slot grows from nothing once the image lands with a plugin`() {
    // The same slot with any plugin at all, which is the composed path and the auto aspect ratio.
    val sizes = sizesAcrossLoad(400, 300, Modifier.fillMaxWidth()) {
      rememberImageComponent { +CrossfadePlugin(duration = 100) }
    }

    assertEquals(
      listOf(IntSize(200, 0), IntSize(200, 150)),
      sizes,
      "unexpected size sequence: $sizes",
    )
  }

  @Test
  fun `the detail slot under an explicit aspect ratio never changes size`() {
    // The demo's SelectedPoster: an explicit ratio the caller set, so nothing is left to the image.
    val sizes = sizesAcrossLoad(400, 300, Modifier.aspectRatio(0.75f)) {
      rememberImageComponent { +CrossfadePlugin(duration = 100) }
    }

    assertEquals(1, sizes.size, "the detail slot resized after loading: $sizes")
    assertEquals(IntSize(200, 267), sizes.single(), "unexpected size: $sizes")
  }

  @Test
  fun `a slot the parent bounded on both axes never changes size`() {
    val sizes = sizesAcrossLoad(400, 300, Modifier.fillMaxWidth().aspectRatio(2f))

    assertTrue(sizes.size == 1, "a bounded slot resized after loading: $sizes")
  }
}
