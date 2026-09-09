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

/**
 * What an entry decoded without a target size may be reused for.
 *
 * A node measured to nothing on its first pass, which is a collapsed row or a lazy item laid out
 * before its container has room, sends its request with no size and gets the image back at its own
 * size. That entry is the source. Every later request for the same url covers it, so without a
 * second look a 96 pixel slot is served a 4000 pixel bitmap for as long as the entry lives, and
 * every plugin that walks the pixels pays for all of them.
 */
class UnsizedEntryReuseTest {

  private val url = "https://example.com/photo.jpg"
  private val sourceSide = 4000

  private class SizingDecoder : ImageDecoder {
    val targets = mutableListOf<Pair<Int?, Int?>>()
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      targets += targetWidth to targetHeight
      val side = minOf(targetWidth ?: 4000, targetHeight ?: 4000, 4000)
      return DecodeResult.Success("decoded_$side", side, side)
    }
  }

  private fun loadSizes(vararg sizes: Pair<Int, Int>?): SizingDecoder {
    val decoder = SizingDecoder()
    val loader = Landscapist.builder()
      .noDiskCache()
      .fetcher(
        object : ImageFetcher {
          override fun canHandle(model: Any?): Boolean = true
          override suspend fun fetch(request: ImageRequest): FetchResult =
            FetchResult.Success(byteArrayOf(1), mimeType = "image/jpeg")
        },
      )
      .decoder(decoder)
      .build()
    runBlocking {
      for (size in sizes) {
        loader.load(
          ImageRequest.builder()
            .model(url)
            .diskCachePolicy(CachePolicy.DISABLED)
            .apply { if (size != null) size(size.first, size.second) }
            .build(),
        ).first { it is ImageResult.Success }
      }
    }
    return decoder
  }

  @Test
  fun `a small slot is not served the whole source`() {
    val decoder = loadSizes(null, 200 to 150)
    assertEquals(
      listOf<Pair<Int?, Int?>>(null to null, 200 to 150),
      decoder.targets,
      "the ${sourceSide}px entry from the unsized load was handed to a 200px request",
    )
  }

  @Test
  fun `a slot the source is not wastefully larger than still reuses it`() {
    // No decode is worth doing when what is cached is within a factor of two of the request.
    val decoder = loadSizes(null, 3000 to 3000)
    assertEquals(1, decoder.targets.size, "the entry was decoded again for no gain")
  }

  @Test
  fun `an entry the decoder refused to shrink is not decoded again`() {
    // A box was asked for and ignored, so asking once more returns the same pixels. This is the
    // case the previous behaviour was written for, and it still has to hold.
    val decoder = SizingDecoder()
    val loader = Landscapist.builder().noDiskCache()
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
            decoder.targets += targetWidth to targetHeight
            return DecodeResult.Success("source", 4000, 4000)
          }
        },
      )
      .build()
    runBlocking {
      for (side in listOf(1000, 200)) {
        loader.load(
          ImageRequest.builder().model(url)
            .diskCachePolicy(CachePolicy.DISABLED).size(side, side).build(),
        ).first { it is ImageResult.Success }
      }
    }
    assertEquals(1, decoder.targets.size, "the same pixels were decoded twice")
  }
}
