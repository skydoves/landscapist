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
package com.skydoves.landscapist.svg

import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Loading an SVG from a URL end to end, which is what issue #945 asks for. */
class SvgLoadTest {

  private val url = "https://example.com/logo.svg"

  private val markup = """
    <?xml version="1.0" encoding="UTF-8"?>
    <svg xmlns="http://www.w3.org/2000/svg" width="48" height="24" viewBox="0 0 48 24">
      <rect width="48" height="24" fill="#6750A4"/>
    </svg>
  """.trimIndent()

  /** Serves the markup with a deliberately unhelpful content type, as many CDNs do. */
  private inner class SvgFetcher(private val mimeType: String?) : ImageFetcher {
    override suspend fun fetch(request: ImageRequest): FetchResult =
      FetchResult.Success(data = markup.encodeToByteArray(), mimeType = mimeType)

    override fun canHandle(model: Any?): Boolean = true
  }

  private fun newLoader(mimeType: String? = "text/plain"): Landscapist = Landscapist.builder()
    .fetcher(SvgFetcher(mimeType))
    .decoder(SvgImageDecoder())
    .build()

  private fun request(width: Int = 200, height: Int = 100) = ImageRequest.builder()
    .model(url)
    .diskCachePolicy(CachePolicy.DISABLED)
    .size(width, height)
    .build()

  @Test
  fun `an svg url loads into a bitmap of the requested size`() = runBlocking {
    val result = newLoader().load(request()).first { it is ImageResult.Success }

    val success = assertIs<ImageResult.Success>(result)
    val bitmap = assertIs<Bitmap>(success.data)
    assertEquals(200, bitmap.width)
    assertEquals(100, bitmap.height)
    assertEquals(0xFF6750A4.toInt(), bitmap.getColor(100, 50))
    assertEquals(200, success.originalWidth)
    assertEquals(100, success.originalHeight)
  }

  @Test
  fun `a second load of the same size comes straight from memory`() = runBlocking {
    val loader = newLoader()
    loader.load(request()).first { it is ImageResult.Success }

    val states = loader.load(request()).first()

    val success = assertIs<ImageResult.Success>(states)
    assertEquals(DataSource.MEMORY, success.dataSource)
  }

  @Test
  fun `a different size re-renders rather than upscaling`() = runBlocking {
    val loader = newLoader()
    loader.load(request(width = 100, height = 50)).first { it is ImageResult.Success }

    val larger = loader.load(request(width = 400, height = 200)).first { it is ImageResult.Success }

    val bitmap = assertIs<Bitmap>(assertIs<ImageResult.Success>(larger).data)
    assertEquals(400, bitmap.width)
  }

  @Test
  fun `an svg still loads when the server labels it correctly`() = runBlocking {
    val result = newLoader("image/svg+xml").load(request()).first { it is ImageResult.Success }

    val bitmap = assertIs<Bitmap>(assertIs<ImageResult.Success>(result).data)
    assertEquals(0xFF6750A4.toInt(), bitmap.getColor(100, 50))
  }
}
