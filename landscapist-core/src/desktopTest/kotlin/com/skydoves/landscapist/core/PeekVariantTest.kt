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
import kotlin.test.assertNotNull
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

  /** Names itself for the cache key and hands the image back unchanged. */
  private class Marker : com.skydoves.landscapist.core.transformation.Transformation {
    override val key: String = "marker"
    override suspend fun transform(input: Any): Any = input
  }

  private fun transformedRequest(side: Int) = ImageRequest.builder()
    .model(url)
    .diskCachePolicy(CachePolicy.DISABLED)
    .size(side, side)
    .transformations(listOf(Marker()))
    .build()

  @Test
  fun `a transformed slot peeks a variant decoded for a box that covers it`() {
    // A grid whose columns do not divide evenly asks for 359, 360 and 361. A transformation makes
    // the recorded size say nothing about the box, so the load path refuses every variant and
    // decodes again, but the peek can still compare the boxes and draw something meanwhile.
    val loader = loader()
    runBlocking { loader.load(transformedRequest(361)).first { it is ImageResult.Success } }

    assertNotNull(
      loader.peekMemoryCache(transformedRequest(360)),
      "a transformed image blinks on re-entry where it used to draw at once",
    )
  }

  @Test
  fun `a transformed slot is judged on the axis it is short of`() {
    // Every other case here is square, so a peek that compared width against height would answer
    // the same. A wide entry does not cover a tall box.
    val loader = loader()
    runBlocking {
      loader.load(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(1080, 200)
          .transformations(listOf(Marker()))
          .build(),
      ).first { it is ImageResult.Success }
    }

    assertNull(
      loader.peekMemoryCache(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(200, 1080)
          .transformations(listOf(Marker()))
          .build(),
      ),
      "an entry 1080 wide and 200 tall was accepted for a box 200 wide and 1080 tall",
    )
  }

  @Test
  fun `a slot is judged on the axis it is short of`() {
    val loader = loader()
    runBlocking { loader.load(request(1080)).first { it is ImageResult.Success } }

    assertNull(
      loader.peekMemoryCache(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(200, 4000)
          .build(),
      ),
      "a 1080 square entry was accepted for a box 4000 tall",
    )
  }

  @Test
  fun `a transformed slot does not peek a thumbnail`() {
    val loader = loader()
    runBlocking { loader.load(transformedRequest(50)).first { it is ImageResult.Success } }

    assertNull(
      loader.peekMemoryCache(transformedRequest(1080)),
      "a transformed thumbnail was handed to a slot twenty times its size",
    )
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
