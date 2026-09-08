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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The container draws the image itself, so it is the container that has to clip it. */
class LandscapistImageClipTest {

  private val url = "https://example.com/wide.png"
  private val imageWidth = 80
  private val imageHeight = 40
  private val nodeSize = 40
  private val sceneSize = 120

  /** A fully opaque image, so any pixel it touches is distinguishable from the empty scene. */
  private fun sourceImage(): ImageBitmap {
    val bitmap = ImageBitmap(imageWidth, imageHeight)
    val bounds = Rect(Offset.Zero, Size(imageWidth.toFloat(), imageHeight.toFloat()))
    Canvas(bitmap).drawRect(bounds, Paint().apply { color = Color.Red })
    return bitmap
  }

  private inner class StubFetcher : ImageFetcher {
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult =
      FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/png")
  }

  private inner class StubDecoder : ImageDecoder {
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult = DecodeResult.Success(
      bitmap = sourceImage(),
      width = imageWidth,
      height = imageHeight,
    )
  }

  private fun warmLoader(): Landscapist {
    val loader = Landscapist.builder().noDiskCache().fetcher(
      StubFetcher(),
    ).decoder(StubDecoder()).build()
    runBlocking {
      loader.load(
        ImageRequest.builder().model(url).diskCachePolicy(CachePolicy.DISABLED).build(),
      ).first { it is ImageResult.Success }
    }
    return loader
  }

  /** Renders one frame and returns its pixels as ARGB ints, row major. */
  private fun render(content: @Composable () -> Unit): IntArray {
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = content,
    )
    try {
      val image = scene.render(0L)
      try {
        val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
          bitmap.allocN32Pixels(image.width, image.height)
          check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
          bitmap.readPixels() ?: error("no pixels")
        }
        return IntArray(sceneSize * sceneSize) { index ->
          val offset = index * 4
          (bytes[offset + 3].toInt() and 0xFF shl 24) or
            (bytes[offset + 2].toInt() and 0xFF shl 16) or
            (bytes[offset + 1].toInt() and 0xFF shl 8) or
            (bytes[offset].toInt() and 0xFF)
        }
      } finally {
        image.close()
      }
    } finally {
      scene.close()
    }
  }

  private fun renderCroppedImage(): IntArray {
    val loader = warmLoader()
    return render {
      Box(Modifier.size(sceneSize.dp)) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.size(nodeSize.dp),
          imageOptions = ImageOptions(contentScale = ContentScale.Crop),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        )
      }
    }
  }

  private fun IntArray.alphaAt(x: Int, y: Int): Int = this[y * sceneSize + x] ushr 24

  @Test
  fun `a cropped image does not paint outside the node it was given`() {
    // Crop draws the 80x40 image 80 wide in a 40 wide node, so 20 columns fall past each edge.
    val pixels = renderCroppedImage()

    val spilled = buildList {
      for (y in 0 until sceneSize) {
        for (x in 0 until sceneSize) {
          if ((x >= nodeSize || y >= nodeSize) && pixels.alphaAt(x, y) != 0) add(x to y)
        }
      }
    }

    assertTrue(
      spilled.isEmpty(),
      "painted ${spilled.size} pixels outside its bounds, first at ${spilled.firstOrNull()}",
    )
  }

  @Test
  fun `a cropped image still fills the node it was given`() {
    // The other half of the same contract: clipping must not eat the drawing itself.
    val pixels = renderCroppedImage()

    var painted = 0
    for (y in 0 until nodeSize) {
      for (x in 0 until nodeSize) {
        if (pixels.alphaAt(x, y) != 0) painted++
      }
    }

    assertEquals(nodeSize * nodeSize, painted, "the node was not fully covered")
  }

  @Test
  fun `a colour filter and an alpha reach the drawing`() {
    // The container path has to carry these onto its paint modifier; the size cannot show that.
    val loader = warmLoader()

    val tinted = render {
      Box(Modifier.size(sceneSize.dp)) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.size(nodeSize.dp),
          imageOptions = ImageOptions(
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(Color.Blue),
          ),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        )
      }
    }
    val faded = render {
      Box(Modifier.size(sceneSize.dp)) {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.size(nodeSize.dp),
          imageOptions = ImageOptions(contentScale = ContentScale.FillBounds, alpha = 0.5f),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        )
      }
    }

    val centre = nodeSize / 2
    assertEquals(
      0xFF0000FF.toInt(),
      tinted[centre * sceneSize + centre],
      "the colour filter never reached the drawing",
    )
    val alpha = faded[centre * sceneSize + centre] ushr 24
    assertTrue(alpha in 1..0xFE, "alpha never reached the drawing, read $alpha")
  }
}
