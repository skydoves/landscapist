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
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the paths that keep an image that is already in memory from blinking: [Landscapist.load]
 * must not announce a loading state for it, and [Landscapist.peekMemoryCache] must hand it back
 * without suspending so a composable can draw it in its first frame.
 */
class MemoryCacheFastPathTest {

  private val url = "https://example.com/image.jpg"

  private object StubFetcher : ImageFetcher {
    override suspend fun fetch(request: ImageRequest): FetchResult =
      FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/png")

    override fun canHandle(model: Any?): Boolean = true
  }

  private object StubDecoder : ImageDecoder {
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult = DecodeResult.Success(bitmap = "decoded", width = 8, height = 16)
  }

  private fun newLoader(): Landscapist =
    Landscapist.builder().fetcher(StubFetcher).decoder(StubDecoder).build()

  private fun request(width: Int? = null, height: Int? = null): ImageRequest =
    ImageRequest.builder()
      .model(url)
      .diskCachePolicy(CachePolicy.DISABLED)
      .apply { if (width != null && height != null) size(width, height) }
      .build()

  @Test
  fun `a memory hit resolves without passing through a loading state`() = runBlocking {
    val loader = newLoader()
    loader.load(request(400, 400)).first { it is ImageResult.Success }

    val states = loader.load(request(400, 400)).toList()

    assertEquals(1, states.size, "a cached image must not emit a loading state: $states")
    val success = states.single() as ImageResult.Success
    assertEquals(DataSource.MEMORY, success.dataSource)
  }

  @Test
  fun `a memory miss still announces a loading state`() = runBlocking {
    val states = newLoader().load(request(400, 400)).toList()

    assertTrue(states.first() is ImageResult.Loading, "expected a loading state first: $states")
    assertTrue(states.last() is ImageResult.Success)
  }

  @Test
  fun `peekMemoryCache finds a cached image without knowing the target size`() = runBlocking {
    val loader = newLoader()
    assertNull(loader.peekMemoryCache(request()), "nothing is cached yet")

    loader.load(request(400, 400)).first { it is ImageResult.Success }

    // This is the composable's first frame: the model is known, the measured size is not.
    val peeked = assertNotNull(loader.peekMemoryCache(request()))
    assertEquals("decoded", peeked.data)
    assertEquals(DataSource.MEMORY, peeked.dataSource)
    assertEquals(8, peeked.originalWidth)
    assertEquals(16, peeked.originalHeight)
  }

  @Test
  fun `peekMemoryCache honours a disabled memory cache policy`() = runBlocking {
    val loader = newLoader()
    loader.load(request(400, 400)).first { it is ImageResult.Success }

    val noRead = ImageRequest.builder()
      .model(url)
      .memoryCachePolicy(CachePolicy.DISABLED)
      .build()

    assertNull(loader.peekMemoryCache(noRead))
  }

  @Test
  fun `peekMemoryCache returns null for an unknown model`() {
    assertNull(newLoader().peekMemoryCache("https://example.com/never-loaded.jpg"))
  }
}
