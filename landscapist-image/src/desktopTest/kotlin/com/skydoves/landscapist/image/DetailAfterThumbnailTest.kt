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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A detail view opened over a strip of thumbnails of the same images.
 *
 * The strip caches each url at its own small size. Selecting one puts that url into the detail
 * view, which reads the cache during composition so an image already decoded is drawn at once.
 * What it must not be handed is the strip's thumbnail: fifty pixels stretched across the screen,
 * replaced a frame or two later by the real decode, which is a visible flash.
 */
@OptIn(ExperimentalTestApi::class)
class DetailAfterThumbnailTest {

  private val first = "https://example.com/a.jpg"
  private val second = "https://example.com/b.jpg"

  private fun loader(): Landscapist = Landscapist.builder()
    .noDiskCache()
    .fetcher(
      object : ImageFetcher {
        override fun canHandle(model: Any?): Boolean = true
        override suspend fun fetch(request: ImageRequest): FetchResult =
          FetchResult.Success(byteArrayOf(1), mimeType = "image/jpeg")
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
        ): DecodeResult {
          val side = targetWidth ?: 4000
          return DecodeResult.Success(ImageBitmap(side, side), side, side)
        }
      },
    )
    .build()

  /** What a strip of thumbnails leaves in the cache before anything is selected. */
  private fun Landscapist.cacheThumbnail(url: String) = runBlocking {
    load(
      ImageRequest.builder()
        .model(url)
        .diskCachePolicy(CachePolicy.DISABLED)
        .size(50, 50)
        .build(),
    ).first { it is ImageResult.Success }
  }

  @Test
  fun `selecting a poster does not draw the strip's thumbnail first`() {
    val landscapist = loader()
    landscapist.cacheThumbnail(first)
    landscapist.cacheThumbnail(second)

    val states = mutableListOf<LandscapistImageState>()
    runComposeUiTest {
      var model by mutableStateOf(first)
      setContent {
        LandscapistImage(
          imageModel = { model },
          landscapist = landscapist,
          modifier = Modifier.size(400.dp),
          component = rememberImageComponent { +CrossfadePlugin() },
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { states += it },
        )
      }
      waitForIdle()
      states.clear()
      // The tap.
      model = second
      waitForIdle()
    }

    val sizes = states.filterIsInstance<LandscapistImageState.Success>()
      .map { (it.data as? ImageBitmap)?.width }
    assertTrue(
      sizes.none { it == 50 },
      "the detail view drew the strip's 50 pixel thumbnail before the real image: $sizes",
    )
    assertEquals(
      listOf(400),
      sizes,
      "the detail view resolved to something other than one image at its own size: $sizes",
    )
  }

  /** Records a remembered identity, so a rebuilt subtree is visible as a second one. */
  private class RebuildCounter : com.skydoves.landscapist.plugins.ImagePlugin.SuccessStatePlugin {
    val ids = mutableListOf<Any>()

    @androidx.compose.runtime.Composable
    override fun compose(
      modifier: Modifier,
      imageModel: Any?,
      imageOptions: com.skydoves.landscapist.ImageOptions,
      imageBitmap: ImageBitmap?,
    ): com.skydoves.landscapist.plugins.ImagePlugin = apply {
      val id = androidx.compose.runtime.remember { Any() }
      if (ids.lastOrNull() !== id) ids += id
    }
  }

  @Test
  fun `a second decode of one image does not rebuild what is composed inside it`() {
    // A cached variant standing in until the right one arrives is one image twice, not two images.
    // Keying the content on the decoded bitmap threw away the plugin groups, a zoomable's decoder
    // and the reveal's animation each time, which is the image appearing to load again.
    val landscapist = loader()
    landscapist.cacheThumbnail(second)
    val counter = RebuildCounter()

    val states = mutableListOf<LandscapistImageState>()
    runComposeUiTest {
      setContent {
        LandscapistImage(
          imageModel = { second },
          landscapist = landscapist,
          modifier = Modifier.size(400.dp),
          component = rememberImageComponent { +counter },
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { states += it },
        )
      }
      waitForIdle()
    }

    val sizes = states.filterIsInstance<LandscapistImageState.Success>()
      .map { (it.data as? ImageBitmap)?.width }
    assertEquals(
      listOf(50, 400),
      sizes,
      "this test is only worth anything while the image still resolves twice here, it went $sizes",
    )
    assertEquals(
      1,
      counter.ids.size,
      "the second decode rebuilt everything composed inside the image, ${counter.ids.size} times",
    )
  }

  @Test
  fun `a composable that has never been measured still takes what the cache has`() {
    // The limit of a sized peek, pinned rather than hidden. A composable entering for the first
    // time has not been measured, so it has nothing to compare a variant against, and an already
    // decoded image beats an empty frame. It still resolves twice here. What that no longer costs
    // is the rebuild: the content is grouped by the kind of state, not by which decode, so the
    // plugins and their animations survive the second one.
    val landscapist = loader()
    landscapist.cacheThumbnail(second)

    val states = mutableListOf<LandscapistImageState>()
    runComposeUiTest {
      setContent {
        LandscapistImage(
          imageModel = { second },
          landscapist = landscapist,
          modifier = Modifier.size(400.dp),
          component = rememberImageComponent { +CrossfadePlugin() },
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { states += it },
        )
      }
      waitForIdle()
    }

    val sizes = states.filterIsInstance<LandscapistImageState.Success>()
      .map { (it.data as? ImageBitmap)?.width }
    assertEquals(
      listOf(50, 400),
      sizes,
      "the unmeasured peek changed, which is worth knowing either way: $sizes",
    )
  }

  @Test
  fun `an entry that fits is still drawn without waiting`() {
    // The other half of the rule: a cached image the slot can use is what the peek exists for.
    val landscapist = loader()
    runBlocking {
      landscapist.load(
        ImageRequest.builder()
          .model(second)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(400, 400)
          .build(),
      ).first { it is ImageResult.Success }
    }

    val states = mutableListOf<LandscapistImageState>()
    runComposeUiTest {
      var model by mutableStateOf(first)
      setContent {
        LandscapistImage(
          imageModel = { model },
          landscapist = landscapist,
          modifier = Modifier.size(400.dp),
          component = rememberImageComponent { +CrossfadePlugin() },
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { states += it },
        )
      }
      waitForIdle()
      states.clear()
      model = second
      waitForIdle()
    }

    assertTrue(
      states.firstOrNull() is LandscapistImageState.Success,
      "a cached image the slot can use was not drawn at once, it went $states",
    )
  }
}
