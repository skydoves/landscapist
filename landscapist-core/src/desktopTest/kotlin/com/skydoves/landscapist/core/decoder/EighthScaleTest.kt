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
package com.skydoves.landscapist.core.decoder

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The eighths fast path has to actually engage, on a photograph whose size is not a round number.
 *
 * libjpeg rounds its scaled output up and SkJpegCodec accepts a request only when it matches
 * exactly, so asking for the rounded down size is refused for every dimension that is not a
 * multiple of eight. That is most photographs, and the decode then falls back to reading the whole
 * raster: slower than the subsampling it replaced, and holding pixels that subsampling never
 * materialised. Nothing catches that with a 1600x1200 or a 4000x3000 fixture.
 */
class EighthScaleTest {

  private fun photo(width: Int, height: Int): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    var seed = 0x9E3779B9.toInt()
    for (y in 0 until height) {
      for (x in 0 until width) {
        seed = seed * 1_664_525 + 1_013_904_223
        image.setRGB(x, y, seed and 0xFFFFFF)
      }
    }
    return ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "jpg", out)
      out.toByteArray()
    }
  }

  /** Whether Skia will decode these bytes straight into [eighths] of their size. */
  private fun sampledReadSucceeds(bytes: ByteArray, eighths: Int): Boolean {
    val data = Data.makeFromBytes(bytes)
    try {
      val codec = Codec.makeFromData(data)
      try {
        val width = (codec.width * eighths + 7) / 8
        val height = (codec.height * eighths + 7) / 8
        val bitmap = Bitmap()
        try {
          bitmap.allocPixels(
            ImageInfo(width, height, ColorType.N32, ColorAlphaType.PREMUL, ColorSpace.sRGB),
          )
          return runCatching { codec.readPixels(bitmap) }.isSuccess
        } finally {
          bitmap.close()
        }
      } finally {
        codec.close()
      }
    } finally {
      data.close()
    }
  }

  @Test
  fun `a photograph whose size is not a multiple of eight still decodes at an eighth`() {
    // 1601x1201 is refused at every eighth if the requested size is rounded down.
    val bytes = photo(1601, 1201)

    val accepted = (1..7).filter { sampledReadSucceeds(bytes, it) }

    assertTrue(
      accepted.isNotEmpty(),
      "Skia refused every eighth of a 1601x1201 JPEG, so the fast path never runs for it",
    )
  }

  @Test
  fun `the decoder returns the size asked for either way`() {
    val decoder = createPlatformDecoder()
    for (side in listOf(1601 to 1201, 1600 to 1200)) {
      val bytes = photo(side.first, side.second)
      val result = kotlinx.coroutines.runBlocking {
        decoder.decode(
          bytes,
          "image/jpeg",
          200,
          150,
          com.skydoves.landscapist.core.LandscapistConfig(),
        )
      }
      val success = result as DecodeResult.Success
      assertTrue(
        success.width <= 200 && success.height <= 150 && success.width > 0,
        "decoding ${side.first}x${side.second} gave ${success.width}x${success.height}",
      )
    }
  }
}
