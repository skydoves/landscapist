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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The painter a caller draws in their own `Image`, which is the one thing [LandscapistImage] cannot
 * offer: no container, no slot, one layout node. What has to hold for it to be worth having is that
 * it still draws a cached image on the first frame, still loads one that is not cached, still asks
 * for it at the size it is drawn at, and keeps its identity while all of that happens.
 */
class LandscapistImagePainterTest {

  private val url = "https://example.com/painter.png"
  private val sceneSize = 40

  private fun sourceImage(): ImageBitmap {
    val bitmap = ImageBitmap(sceneSize, sceneSize)
    val bounds = Rect(Offset.Zero, Size(sceneSize.toFloat(), sceneSize.toFloat()))
    Canvas(bitmap).drawRect(bounds, Paint().apply { color = Color.Red })
    return bitmap
  }

  private inner class StubFetcher : ImageFetcher {
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult {
      requested += request.targetWidth to request.targetHeight
      return FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/png")
    }
  }

  private inner class StubDecoder : ImageDecoder {
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult = DecodeResult.Success(sourceImage(), sceneSize, sceneSize)
  }

  /** Every target size the fetcher was asked for, in order. */
  private val requested = mutableListOf<Pair<Int?, Int?>>()

  private fun newLoader(): Landscapist =
    Landscapist.builder().fetcher(StubFetcher()).decoder(StubDecoder()).build()

  /** A loader whose image is already in memory, so the first frame can draw it. */
  private fun warmLoader(): Landscapist = newLoader().also { loader ->
    runBlocking {
      loader.load(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(sceneSize, sceneSize)
          .build(),
      ).first { it is ImageResult.Success }
    }
    requested.clear()
  }

  /**
   * Renders frames of one scene until [done] holds, and returns the pixels of the last.
   *
   * A load that is not already in memory resolves on the loader's own dispatcher, so frames have to
   * keep coming for the result to ever be composed. [frames] alone is the count for a scene that is
   * expected to be finished on the first one.
   */
  private fun render(
    frames: Int = 1,
    timeoutMs: Long = 10_000,
    done: (IntArray) -> Boolean = { true },
    content: @Composable () -> Unit,
  ): IntArray {
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = content,
    )
    try {
      var pixels = IntArray(sceneSize * sceneSize)
      val deadline = System.nanoTime() + timeoutMs * 1_000_000
      var frame = 0
      while (true) {
        val image = scene.render(frame.toLong() * 16_000_000)
        try {
          val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(image.width, image.height)
            check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
            bitmap.readPixels() ?: error("no pixels")
          }
          pixels = IntArray(sceneSize * sceneSize) { index ->
            val offset = index * 4
            (bytes[offset + 3].toInt() and 0xFF shl 24) or
              (bytes[offset + 2].toInt() and 0xFF shl 16) or
              (bytes[offset + 1].toInt() and 0xFF shl 8) or
              (bytes[offset].toInt() and 0xFF)
          }
        } finally {
          image.close()
        }
        frame++
        if (frame >= frames && done(pixels)) return pixels
        if (System.nanoTime() > deadline) return pixels
        Thread.sleep(4)
      }
    } finally {
      scene.close()
    }
  }

  private fun IntArray.coverage(): Double = count { it ushr 24 != 0 }.toDouble() / size

  @Test
  fun `a cached image is drawn on the very first frame`() {
    val loader = warmLoader()

    val pixels = render {
      Image(
        painter = rememberLandscapistImagePainter(
          model = url,
          landscapist = loader,
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        ),
        contentDescription = null,
        modifier = Modifier.size(sceneSize.dp),
      )
    }

    assertTrue(pixels.coverage() > 0.9, "the cached image was not drawn on the first frame")
  }

  @Test
  fun `an image that is not cached is loaded and drawn`() {
    val loader = newLoader()

    // The first frame is what tells the painter how big the image has to be, so the load starts
    // from it and the frame after is the first that can have the image in it.
    val pixels = render(frames = 2, done = { it.coverage() > 0.9 }) {
      Image(
        painter = rememberLandscapistImagePainter(
          model = url,
          landscapist = loader,
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        ),
        contentDescription = null,
        modifier = Modifier.size(sceneSize.dp),
      )
    }

    assertTrue(pixels.coverage() > 0.9, "the loaded image was never drawn")
  }

  @Test
  fun `the image is asked for at the size it is drawn at`() {
    val loader = newLoader()

    render(frames = 2, done = { requested.isNotEmpty() }) {
      Image(
        painter = rememberLandscapistImagePainter(
          model = url,
          landscapist = loader,
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        ),
        contentDescription = null,
        modifier = Modifier.size((sceneSize / 2).dp),
      )
    }

    val size = assertNotNull(requested.firstOrNull(), "the image was never fetched")
    assertEquals(sceneSize / 2 to sceneSize / 2, size)
  }

  @Test
  fun `the painter keeps its identity while the image resolves`() {
    val loader = newLoader()
    val seen = mutableListOf<Painter>()
    val states = mutableListOf<LandscapistImageState>()

    render(frames = 2, done = { states.any { state -> state is LandscapistImageState.Success } }) {
      val painter = rememberLandscapistImagePainter(
        model = url,
        landscapist = loader,
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        onImageStateChanged = { states += it },
      )
      seen += painter
      Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.size(sceneSize.dp),
      )
    }

    assertTrue(seen.size > 1, "the painter was only ever composed once, so nothing was proven")
    for (painter in seen) {
      assertSame(seen.first(), painter, "the painter was replaced when the image resolved")
    }
    assertTrue(
      states.any { it is LandscapistImageState.Success },
      "the state callback never reported a success",
    )
  }
}
