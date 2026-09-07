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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The desktop decoder produces a [BufferedImage], so the painter and the bitmap converter have to
 * understand one. Issue #982.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopBufferedImageTest {

  private val url = "https://example.com/photo.png"

  /** A 4x2 solid red PNG, encoded the way a server would send it. */
  private val png: ByteArray = run {
    val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB)
    for (x in 0 until 4) {
      for (y in 0 until 2) {
        image.setRGB(x, y, 0xFFFF0000.toInt())
      }
    }
    ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "png", out)
      out.toByteArray()
    }
  }

  private inner class PngFetcher : ImageFetcher {
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult =
      FetchResult.Success(data = png, mimeType = "image/png")
  }

  /** No decoder override, so this exercises the real DesktopImageDecoder. */
  private fun newLoader(): Landscapist = Landscapist.builder().fetcher(PngFetcher()).build()

  private fun request() = ImageRequest.builder()
    .model(url)
    .diskCachePolicy(CachePolicy.DISABLED)
    .size(4, 2)
    .build()

  private fun Landscapist.awaitDecoded(): Any {
    val result = runBlocking { load(request()).first { it is ImageResult.Success } }
    return assertNotNull(assertIs<ImageResult.Success>(result).data)
  }

  @Test
  fun `the desktop decoder really does hand back a BufferedImage`() {
    assertIs<BufferedImage>(newLoader().awaitDecoded())
  }

  @Test
  fun `a decoded desktop image converts to an ImageBitmap`() {
    val bitmap = assertNotNull(
      convertToImageBitmap(newLoader().awaitDecoded()),
      "convertToImageBitmap dropped the decoded image, so palette and painter plugins never run",
    )

    assertEquals(4, bitmap.width)
    assertEquals(2, bitmap.height)
  }

  @Test
  fun `a decoded desktop image is drawn rather than dropped`() {
    val landscapist = newLoader()
    landscapist.awaitDecoded()

    var painter: Painter? = null
    runComposeUiTest {
      setContent {
        LandscapistImage(
          imageModel = { url },
          landscapist = landscapist,
          modifier = Modifier.size(100.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          success = { _, loaded -> painter = loaded },
        )
      }
    }

    val loaded = assertNotNull(painter, "the image never reached the success slot")
    assertTrue(
      loaded !is EmptyPainter,
      "the desktop image fell through to EmptyPainter, so nothing is drawn",
    )
  }
}
