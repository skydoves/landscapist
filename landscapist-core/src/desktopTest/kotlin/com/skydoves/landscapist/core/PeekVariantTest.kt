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
package com.skydoves.landscapist.core

import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the cache hands back to a composable reading it before it has laid out.
 *
 * A composable peeks so an image that is already decoded is drawn in the frame it appears in. A
 * screen with a strip of thumbnails over a detail view has the thumbnail decoded and the detail
 * view not, and the two are the same url, so the peek has to decide whether 50 pixels will do for
 * a slot that is about to be 1080 wide.
 */
class PeekVariantTest {

  private val url = "https://example.com/poster.jpg"

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
          return DecodeResult.Success("decoded_$side", side, side)
        }
      },
    )
    .build()

  private fun request(side: Int) = ImageRequest.builder()
    .model(url)
    .diskCachePolicy(CachePolicy.DISABLED)
    .size(side, side)
    .build()

  @Test
  fun `a detail slot does not peek the thumbnail the strip above it left behind`() {
    val loader = loader()
    // The strip loads first, which is what a LazyRow of thumbnails over a detail view does.
    runBlocking { loader.load(request(50)).first { it is ImageResult.Success } }

    val peeked = loader.peekMemoryCache(request(1080))

    assertNull(
      peeked,
      "the detail view was handed a 50 pixel thumbnail to draw at 1080, which is the flash the " +
        "user sees before the real image replaces it: it peeked ${(peeked?.data)}",
    )
  }

  @Test
  fun `a slot peeks the entry decoded for it`() {
    val loader = loader()
    runBlocking { loader.load(request(1080)).first { it is ImageResult.Success } }

    assertEquals("decoded_1080", loader.peekMemoryCache(request(1080))?.data)
  }

  @Test
  fun `a slot peeks an entry large enough to serve it`() {
    // Not the same size, but no worse: drawing it costs nothing extra and the alternative is an
    // empty frame.
    val loader = loader()
    runBlocking { loader.load(request(1200)).first { it is ImageResult.Success } }

    assertEquals("decoded_1200", loader.peekMemoryCache(request(1080))?.data)
  }
}
