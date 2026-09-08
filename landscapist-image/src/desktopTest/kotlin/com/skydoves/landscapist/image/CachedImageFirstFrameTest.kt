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
import androidx.compose.runtime.Composable
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
import com.skydoves.landscapist.core.model.DataSource
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
 * The blink these tests guard against: a composable that enters composition with its image already
 * in memory used to render nothing until the loading flow came back a frame or two later. Inside a
 * shared element transition that empty box is what animates, so the image appears to vanish and
 * come back.
 */
@OptIn(ExperimentalTestApi::class)
class CachedImageFirstFrameTest {

  private val url = "https://example.com/image.jpg"

  private object StubFetcher : ImageFetcher {
    override suspend fun fetch(request: ImageRequest): FetchResult =
      FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/png")

    override fun canHandle(model: Any?): Boolean = true
  }

  /** Returns an [ImageBitmap] so the skia painter and bitmap converter both accept the result. */
  private object StubDecoder : ImageDecoder {
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult = DecodeResult.Success(
      bitmap = ImageBitmap(width = 8, height = 16),
      width = 8,
      height = 16,
    )
  }

  private fun newLoader(): Landscapist =
    Landscapist.builder().noDiskCache().fetcher(StubFetcher).decoder(StubDecoder).build()

  private fun Landscapist.warmCache(width: Int = 200, height: Int = 200) = runBlocking {
    val request = ImageRequest.builder()
      .model(url)
      .diskCachePolicy(CachePolicy.DISABLED)
      .size(width, height)
      .build()
    load(request).first { it is ImageResult.Success }
  }

  /** Records every state the composable renders, in order. */
  private fun composedStates(
    landscapist: Landscapist,
    component: @Composable () -> com.skydoves.landscapist.components.ImageComponent,
  ): List<LandscapistImageState> {
    val states = mutableListOf<LandscapistImageState>()
    runComposeUiTest {
      mainClock.autoAdvance = false
      setContent {
        LandscapistImage(
          imageModel = { url },
          landscapist = landscapist,
          modifier = Modifier.size(100.dp),
          component = component(),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { states += it },
        )
      }
    }
    return states
  }

  @Test
  fun `a cached image is rendered in the first composed frame`() {
    val landscapist = newLoader()
    landscapist.warmCache()

    val states = composedStates(landscapist) { rememberImageComponent {} }

    val first = states.firstOrNull()
    assertTrue(
      first is LandscapistImageState.Success,
      "the first composed frame must already hold the cached image, but was $first",
    )
    assertEquals(DataSource.MEMORY, first.dataSource)
    assertEquals(8, first.originalWidth)
    assertEquals(16, first.originalHeight)
  }

  @Test
  fun `a cached image never drops back to a loading state`() {
    val landscapist = newLoader()
    landscapist.warmCache()

    val states = composedStates(landscapist) { rememberImageComponent {} }

    assertTrue(states.isNotEmpty(), "no state ever reached the callback")

    assertTrue(
      states.none { it is LandscapistImageState.Loading || it is LandscapistImageState.None },
      "a cached image must not pass through a loading state, but rendered $states",
    )
  }

  @Test
  fun `a cached image is not crossfaded in`() {
    val landscapist = newLoader()
    landscapist.warmCache()

    val states = composedStates(landscapist) {
      rememberImageComponent { +CrossfadePlugin(duration = 300) }
    }

    // With a crossfade plugin the states are the same; what changes is that the first one is the
    // crossfade's initial content, so it is drawn at full opacity instead of fading up from zero.
    val first = states.firstOrNull()
    assertTrue(
      first is LandscapistImageState.Success,
      "the crossfade must start from the cached image, but was $first",
    )
  }

  @Test
  fun `an uncached image still starts from a loading state`() {
    val states = composedStates(newLoader()) { rememberImageComponent {} }

    val first = states.firstOrNull()
    assertTrue(
      first is LandscapistImageState.None || first is LandscapistImageState.Loading,
      "an image that is not cached has nothing to show yet, but was $first",
    )
  }
}
