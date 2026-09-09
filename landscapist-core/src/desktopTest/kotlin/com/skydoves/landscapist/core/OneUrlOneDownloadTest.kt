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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One trip to the network per url, whatever sizes are asking for it.
 *
 * A screen showing one image twice is ordinary: a strip of thumbnails over the picture they select,
 * a grid item that opens into a detail view. Those are two requests for one url at two sizes, and
 * they need two decodes but one download.
 */
class OneUrlOneDownloadTest {

  private val url = "https://example.com/poster.jpg"

  private class SlowFetcher : ImageFetcher {
    val fetches = atomic(0)
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult {
      fetches.incrementAndGet()
      // Long enough that a second request starts while this one is still out.
      delay(50)
      return FetchResult.Success(byteArrayOf(1, 2, 3), mimeType = "image/jpeg")
    }
  }

  private class SizingDecoder : ImageDecoder {
    val decodes = atomic(0)
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      decodes.incrementAndGet()
      val side = targetWidth ?: 4000
      return DecodeResult.Success("decoded_$side", side, side)
    }
  }

  private fun loaderWith(fetcher: SlowFetcher, decoder: SizingDecoder): Landscapist =
    Landscapist.builder().noDiskCache().fetcher(fetcher).decoder(decoder).build()

  private fun Landscapist.loadAt(side: Int) = load(
    ImageRequest.builder()
      .model(url)
      .diskCachePolicy(CachePolicy.DISABLED)
      .size(side, side)
      .build(),
  )

  @Test
  fun `one url asked for at two sizes at once is downloaded once`() {
    val fetcher = SlowFetcher()
    val decoder = SizingDecoder()
    val loader = loaderWith(fetcher, decoder)

    runBlocking {
      val thumbnail = async { loader.loadAt(50).first { it is ImageResult.Success } }
      val poster = async { loader.loadAt(1080).first { it is ImageResult.Success } }
      thumbnail.await()
      poster.await()
    }

    assertEquals(1, fetcher.fetches.value, "the same bytes were downloaded once per size")
    assertEquals(2, decoder.decodes.value, "each size still needs its own decode")
  }

  @Test
  fun `the same size twice at once is still one download and one decode`() {
    val fetcher = SlowFetcher()
    val decoder = SizingDecoder()
    val loader = loaderWith(fetcher, decoder)

    runBlocking {
      val a = async { loader.loadAt(240).first { it is ImageResult.Success } }
      val b = async { loader.loadAt(240).first { it is ImageResult.Success } }
      a.await()
      b.await()
    }

    assertEquals(1, fetcher.fetches.value)
    assertEquals(1, decoder.decodes.value, "one request was decoded twice")
  }

  @Test
  fun `each size gets the pixels it asked for`() {
    val fetcher = SlowFetcher()
    val loader = loaderWith(fetcher, SizingDecoder())

    val results = runBlocking {
      val thumbnail = async { loader.loadAt(50).first { it is ImageResult.Success } }
      val poster = async { loader.loadAt(1080).first { it is ImageResult.Success } }
      listOf(thumbnail.await(), poster.await()).map { (it as ImageResult.Success).data }
    }

    assertEquals(listOf("decoded_50", "decoded_1080"), results)
  }
}
