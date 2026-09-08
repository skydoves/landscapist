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
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What happens to the node when a list rebinds or reuses the row it is in. */
@OptIn(ExperimentalTestApi::class)
class LandscapistImageNodeReloadTest {

  private val slow = "https://example.com/slow.png"
  private val quick = "https://example.com/quick.png"

  /** Holds the first model's fetch open until it is released. */
  private class GatedFetcher(private val gate: CompletableDeferred<Unit>) : ImageFetcher {
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult {
      if (request.model.toString().contains("slow")) gate.await()
      return FetchResult.Success(
        data = byteArrayOf(if (request.model.toString().contains("slow")) 1 else 2),
        mimeType = "image/png",
      )
    }
  }

  private object SizedDecoder : ImageDecoder {
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult = DecodeResult.Success("image-${data[0]}", 20, 20)
  }

  @Test
  fun `a load left behind by a rebind does not overwrite the image that replaced it`() {
    val gate = CompletableDeferred<Unit>()
    val loader = Landscapist.builder()
      .noDiskCache()
      .fetcher(GatedFetcher(gate))
      .decoder(SizedDecoder)
      .build()
    val seen = mutableListOf<String>()

    runComposeUiTest {
      var model by mutableStateOf(slow)
      setContent {
        LandscapistImage(
          imageModel = { model },
          landscapist = loader,
          modifier = Modifier.size(20.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { state ->
            if (state is LandscapistImageState.Success) seen += state.data.toString()
          },
        )
      }
      waitForIdle()
      // Rebound before the first image arrives, as a scrolling list does.
      model = quick
      // waitForIdle does not follow the loader's own scope, so wait for the image itself.
      waitUntil(timeoutMillis = 5_000) { seen.isNotEmpty() }
      // Let the abandoned load finish; nothing it publishes may reach the composable.
      gate.complete(Unit)
      repeat(5) {
        waitForIdle()
        Thread.sleep(40)
      }
    }

    assertTrue(seen.isNotEmpty(), "no image ever arrived")
    assertEquals(
      "image-2",
      seen.last(),
      "the load left behind by the rebind published over the image that replaced it: $seen",
    )
  }

  @Test
  fun `a reused node does not publish the image of the row it used to be in`() {
    // A lazy list resets and reattaches the node before the new composition hands it the model.
    val loader = Landscapist.builder()
      .noDiskCache()
      .fetcher(GatedFetcher(CompletableDeferred(Unit)))
      .decoder(SizedDecoder)
      .build()
    // Both already in memory, so the reused node can read either of them synchronously.
    runBlocking {
      for (model in listOf(slow, quick)) {
        loader.load(
          ImageRequest.builder().model(model).diskCachePolicy(CachePolicy.DISABLED).build(),
        ).first { it is ImageResult.Success }
      }
    }
    val seen = mutableListOf<String>()

    runComposeUiTest {
      var model by mutableStateOf(slow)
      setContent {
        // Reused rather than disposed and rebuilt, which a plain state change would not do.
        ReusableContent(model) {
          LandscapistImage(
            imageModel = { model },
            landscapist = loader,
            modifier = Modifier.size(20.dp),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
            onImageStateChanged = { state ->
              if (state is LandscapistImageState.Success) seen += state.data.toString()
            },
          )
        }
      }
      waitForIdle()
      seen.clear()
      model = quick
      waitForIdle()
      waitUntil(timeoutMillis = 5_000) { seen.isNotEmpty() }
    }

    assertTrue(seen.isNotEmpty(), "the reused node never loaded anything")
    assertEquals(
      listOf("image-2"),
      seen.distinct(),
      "the reused node published the previous row's image: $seen",
    )
  }
}
