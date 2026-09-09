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

import com.skydoves.landscapist.core.cache.CacheKey
import com.skydoves.landscapist.core.cache.CachedImage
import com.skydoves.landscapist.core.cache.MemoryCache
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
import kotlin.test.assertIs

/**
 * A cache written before [MemoryCache.getMatching] existed still has to serve the right pixels.
 *
 * Such a cache inherits the default, which cannot know the box a variant was decoded for. Handing
 * the request's own key to the acceptance test makes every size check compare a value with itself,
 * and a thumbnail is then accepted for a full sized request and drawn upscaled.
 */
class ThirdPartyCacheTest {

  private val url = "https://example.com/photo.jpg"

  /** All the hooks a cache had before `getMatching`, and none of the ones added since. */
  private class OlderCache : MemoryCache {
    private val entries = linkedMapOf<String, Pair<CacheKey, CachedImage>>()
    override val maxSize: Long = Long.MAX_VALUE
    override val size: Long get() = 0
    override fun get(key: CacheKey): CachedImage? = entries[key.memoryKey]?.second
    override fun getIgnoringSize(key: CacheKey): CachedImage? =
      entries.values.lastOrNull { it.first.baseKey == key.baseKey }?.second
    override fun set(key: CacheKey, image: CachedImage) {
      entries[key.memoryKey] = key to image
    }
    override fun remove(key: CacheKey): Boolean = entries.remove(key.memoryKey) != null
    override fun clear() = entries.clear()
    override fun trimToSize(size: Long) = Unit
  }

  @Test
  fun `a thumbnail is not served for a full sized request`() {
    val cache = OlderCache()
    var decodes = 0
    val loader = Landscapist.builder()
      .noDiskCache()
      .memoryCache(cache)
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
            decodes++
            val side = targetWidth ?: 1000
            return DecodeResult.Success("decoded_$side", side, side)
          }
        },
      )
      .build()

    fun load(side: Int) = runBlocking {
      val result = loader.load(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(side, side)
          .build(),
      ).first { it is ImageResult.Success }
      assertIs<ImageResult.Success>(result).data as String
    }

    assertEquals("decoded_100", load(100))
    assertEquals(
      "decoded_1000",
      load(1000),
      "the 100px thumbnail was served for a 1000px request and would be drawn upscaled",
    )
    assertEquals(2, decodes)
  }
}
