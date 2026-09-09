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
import com.skydoves.landscapist.core.decoder.RawImageData
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Variant reuse on the platforms that do not decode in the loader.
 *
 * The Apple and wasm decoders hand the encoded bytes to the Compose layer and let Skia decode them
 * at draw size, so what the memory cache holds is the whole source image. An entry like that serves
 * any request: there is no smaller decode waiting to be fetched, and refusing one only costs a
 * second trip through the pipeline to be handed back the same bytes.
 */
class RawImageDataReuseTest {

  private val url = "https://example.com/panorama.jpg"
  private val sourceWidth = 4000
  private val sourceHeight = 800

  private class CountingDecoder : ImageDecoder {
    var decodes = 0
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      decodes++
      // What AppleImageDecoder does: the bytes, sized from the header rather than from the box.
      return DecodeResult.Success(RawImageData(data, mimeType), 4000, 800)
    }
  }

  private fun loadTwice(
    first: Pair<Int, Int>,
    second: Pair<Int, Int>,
  ): Int {
    val decoder = CountingDecoder()
    val loader = Landscapist.builder()
      .noDiskCache()
      .fetcher(
        object : ImageFetcher {
          override fun canHandle(model: Any?): Boolean = true
          override suspend fun fetch(request: ImageRequest): FetchResult =
            FetchResult.Success(byteArrayOf(1, 2, 3), mimeType = "image/jpeg")
        },
      )
      .decoder(decoder)
      .build()

    runBlocking {
      for ((width, height) in listOf(first, second)) {
        loader.load(
          ImageRequest.builder()
            .model(url)
            .diskCachePolicy(CachePolicy.DISABLED)
            .size(width, height)
            .build(),
        ).first { it is ImageResult.Success }
      }
    }
    return decoder.decodes
  }

  @Test
  fun `an entry decoded for a box serves a request that leaves an axis open`() {
    // A grid cached it at 250 square, then a feed row asks for it 250 wide and as tall as it likes.
    assertEquals(
      1,
      loadTwice(250 to 250, 250 to Int.MAX_VALUE),
      "the same bytes were run through the pipeline twice",
    )
  }

  @Test
  fun `an entry serves a request larger than the box it was cached for`() {
    assertEquals(
      1,
      loadTwice(250 to 250, 2000 to 2000),
      "the same bytes were run through the pipeline twice",
    )
  }

  @Test
  fun `an entry serves a request that asks for no size at all`() {
    assertEquals(
      1,
      loadTwice(250 to 250, Int.MAX_VALUE to Int.MAX_VALUE),
      "the same bytes were run through the pipeline twice",
    )
  }
}
