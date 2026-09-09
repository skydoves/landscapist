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
import com.skydoves.landscapist.core.cache.LruMemoryCache
import com.skydoves.landscapist.core.cache.TwoTierMemoryCache
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The decoder here fits inside the box it is given rather than matching it, as real ones do. */
class SizeToleranceTest {

  private val url = "https://example.com/photo.jpg"

  /** Scales the source down to fit whatever box it is handed. */
  private class FittingDecoder(
    private val sourceWidth: Int,
    private val sourceHeight: Int,
  ) : ImageDecoder {
    var decodes = 0
      private set

    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      decodes++
      val boxWidth = targetWidth?.takeIf { it in 1 until Int.MAX_VALUE } ?: sourceWidth
      val boxHeight = targetHeight?.takeIf { it in 1 until Int.MAX_VALUE } ?: sourceHeight
      val scale = minOf(
        boxWidth.toDouble() / sourceWidth,
        boxHeight.toDouble() / sourceHeight,
        1.0,
      )
      val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
      val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
      return DecodeResult.Success(
        bitmap = "decoded_${width}x$height",
        width = width,
        height = height,
      )
    }
  }

  private object StubFetcher : ImageFetcher {
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult =
      FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/jpeg")
  }

  private class Loader(sourceWidth: Int, sourceHeight: Int) {
    val decoder = FittingDecoder(sourceWidth, sourceHeight)
    val landscapist: Landscapist =
      Landscapist.builder().noDiskCache().fetcher(StubFetcher).decoder(decoder).build()

    fun load(width: Int?, height: Int?): String = runBlocking {
      val request = ImageRequest.builder()
        .model("https://example.com/photo.jpg")
        .diskCachePolicy(CachePolicy.DISABLED)
        .apply { if (width != null && height != null) size(width, height) }
        .build()
      val result = landscapist.load(request).first { it is ImageResult.Success }
      assertIs<ImageResult.Success>(result).data as String
    }
  }

  private fun photo() = Loader(4000, 3000)

  @Test
  fun `a photo reuses its entry across the sizes a grid actually measures`() {
    // 4000x3000 in a 360 box is 360x270, so it is the pixels that are compared, not the box.
    val loader = photo()
    assertEquals("decoded_360x270", loader.load(360, 360))

    assertEquals("decoded_360x270", loader.load(359, 359))
    assertEquals("decoded_360x270", loader.load(361, 361))
    assertEquals("decoded_360x270", loader.load(300, 300))
    assertEquals(1, loader.decoder.decodes, "the photo was decoded more than once")
  }

  @Test
  fun `a meaningfully larger box decodes again`() {
    val loader = photo()
    loader.load(360, 360)

    assertEquals("decoded_400x300", loader.load(400, 400))
    assertEquals(2, loader.decoder.decodes)
  }

  @Test
  fun `a somewhat larger entry serves a smaller request`() {
    val loader = photo()
    assertEquals("decoded_500x375", loader.load(500, 500))

    assertEquals("decoded_500x375", loader.load(360, 360))
    assertEquals(1, loader.decoder.decodes)
  }

  @Test
  fun `an entry far larger than the slot is decoded again`() {
    val loader = photo()
    loader.load(1080, 1080)

    assertEquals("decoded_360x270", loader.load(360, 360))
    assertEquals(2, loader.decoder.decodes)
  }

  @Test
  fun `an entry oversized on one axis alone is decoded again`() {
    // A panorama: 1080x135 in a 360 wide slot is oversized across but not down.
    val loader = Loader(sourceWidth = 4000, sourceHeight = 500)
    assertEquals("decoded_1080x135", loader.load(1080, 1080))

    assertEquals("decoded_360x45", loader.load(360, 360))
    assertEquals(2, loader.decoder.decodes, "the oversized panorama was reused")
  }

  @Test
  fun `a decoder that ignores the target size still gets its entry reused`() {
    // What the Apple and wasm decoders do: they hand back the source whatever is asked for. An
    // entry that is already the source cannot be made smaller by decoding again, so refusing it
    // would throw away the hit and produce the same pixels.
    var decodes = 0
    val loader = Landscapist.builder().noDiskCache().fetcher(StubFetcher).decoder(
      object : ImageDecoder {
        override suspend fun decode(
          data: ByteArray,
          mimeType: String?,
          targetWidth: Int?,
          targetHeight: Int?,
          config: LandscapistConfig,
        ): DecodeResult {
          decodes++
          return DecodeResult.Success("source_4000x3000", 4000, 3000)
        }
      },
    ).build()
    fun load(width: Int, height: Int) = runBlocking {
      val result = loader.load(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(width, height)
          .build(),
      ).first { it is ImageResult.Success }
      assertIs<ImageResult.Success>(result).data as String
    }

    assertEquals("source_4000x3000", load(1080, 1080))
    assertEquals("source_4000x3000", load(360, 360))
    assertEquals(1, decodes, "the entry was decoded again although it could not come back smaller")
  }

  @Test
  fun `an entry decoded for a box that fills it is not reused for a taller box`() {
    // An SVG renderer fills the box rather than fitting it, so boxes are compared, not pixels.
    val loader = Loader(sourceWidth = 100, sourceHeight = 100)
    assertEquals("decoded_100x100", loader.load(100, 100))

    assertEquals("decoded_100x100", loader.load(100, 300))
    assertEquals(2, loader.decoder.decodes, "the square entry was reused for a taller box")
  }

  @Test
  fun `a thumbnail does not satisfy a request with an unbounded height`() {
    // The bounded axis still has to be covered.
    val loader = photo()
    loader.load(15, 15)

    assertEquals("decoded_1080x810", loader.load(1080, Int.MAX_VALUE))
  }

  @Test
  fun `a thumbnail does not satisfy a request with an unbounded width`() {
    val loader = photo()
    loader.load(15, 15)

    assertEquals("decoded_2560x1920", loader.load(Int.MAX_VALUE, 1920))
  }

  @Test
  fun `a request with no size at all does not reuse a variant`() {
    val loader = photo()
    loader.load(15, 15)

    assertEquals("decoded_4000x3000", loader.load(null, null))
  }

  private fun entry(name: String, width: Int, height: Int) =
    CachedImage(name, DataSource.MEMORY, 100, width, height)

  @Test
  fun `a lookup keeps offering variants until one is accepted`() {
    for (cache in listOf(LruMemoryCache(10_000), TwoTierMemoryCache(10_000))) {
      cache[CacheKey.create(url, emptyList(), 1080, 1080)] = entry("full", 1080, 810)
      cache[CacheKey.create(url, emptyList(), 15, 15)] = entry("thumb", 15, 11)

      val wanted = CacheKey.create(url, emptyList(), 1000, 1000)
      val found = cache.getMatching(wanted) { _, image -> image.originalWidth >= 1000 }

      assertEquals("full", found?.data, "${cache::class.simpleName} stopped at the thumbnail")
    }
  }

  @Test
  fun `a transformed request is not served by a differently sized variant`() {
    // A transformation may resize what it is handed, so the recorded size means nothing here.
    val loader = photo()
    val transformation = object : com.skydoves.landscapist.core.transformation.Transformation {
      override val key: String = "shrink"
      override suspend fun transform(input: Any): Any = input
    }
    fun load(size: Int): String = runBlocking {
      val request = ImageRequest.builder()
        .model(url)
        .diskCachePolicy(CachePolicy.DISABLED)
        .size(size, size)
        .transformations(listOf(transformation))
        .build()
      assertIs<ImageResult.Success>(
        loader.landscapist.load(request).first { it is ImageResult.Success },
      ).data as String
    }
    load(360)
    val decodes = loader.decoder.decodes

    load(359)

    assertTrue(loader.decoder.decodes > decodes, "a transformed variant was reused by size")
  }

  @Test
  fun `a rejected variant is not marked as used`() {
    val cache = LruMemoryCache(10_000)
    val thumbnail = CacheKey.create(url, emptyList(), 15, 15)
    val unrelated = CacheKey.create("https://example.com/other.jpg", emptyList(), 10, 10)
    cache[thumbnail] = entry("thumb", 15, 11)
    cache[unrelated] = entry("other", 10, 10)

    assertNull(cache.getMatching(CacheKey.create(url, emptyList(), 1080, 1080)) { _, _ -> false })

    // The rejected thumbnail has to still be the oldest entry, so it is the first one evicted.
    cache.trimToSize(100)
    assertNull(cache[thumbnail], "the rejected variant was refreshed by the lookup")
    assertEquals("other", cache[unrelated]?.data)
  }
}
