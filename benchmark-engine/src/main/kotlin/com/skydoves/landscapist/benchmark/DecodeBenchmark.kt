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
package com.skydoves.landscapist.benchmark

import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Each library's real decoder against the same bytes.
 *
 * This is the one comparison where the two are genuinely doing different work: Coil decodes through
 * Skia, landscapist-core through ImageIO. It is reported separately from the engine numbers for
 * exactly that reason, and it says as much about the two imaging stacks as about the loaders.
 */
internal fun decodeComparison() {
  val photo = syntheticPhoto(4000, 3000)
  println("decode a ${4000}x${3000} JPEG (${photo.size.toLong().formatBytes()}) down to 400x300")

  val landscapistDecoder = createPlatformDecoder()
  val config = LandscapistConfig()

  val landscapist = measure("landscapist", warmups = 5, iterations = 25) {
    runBlocking {
      val result = landscapistDecoder.decode(photo, "image/jpeg", 400, 300, config)
      check(result is DecodeResult.Success) { "decode failed: $result" }
    }
  }
  settle()

  val coil = measure("coil (skia)", warmups = 5, iterations = 25) {
    // What SkiaImageDecoder does: full decode, then a raster to scale into.
    val image = org.jetbrains.skia.Image.makeFromEncoded(photo)
    val bitmap = org.jetbrains.skia.Bitmap()
    bitmap.allocN32Pixels(400, 300)
    org.jetbrains.skia.Canvas(bitmap).drawImageRect(
      image,
      org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
      org.jetbrains.skia.Rect.makeWH(400f, 300f),
    )
    bitmap.setImmutable()
    image.close()
  }

  report("decode 4000x3000 -> 400x300", landscapist, coil)

  val peakLandscapist = allocatedBytes {
    runBlocking { landscapistDecoder.decode(photo, "image/jpeg", 400, 300, config) }
  }
  println("  allocation for one decode")
  println("    landscapist    ${peakLandscapist.formatBytes()}")
  println()
}

/** A JPEG with enough detail that the encoder cannot collapse it to nothing. */
private fun syntheticPhoto(width: Int, height: Int): ByteArray {
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
  var seed = 0x9E3779B9.toInt()
  for (y in 0 until height step 2) {
    for (x in 0 until width step 2) {
      seed = seed * 1_664_525 + 1_013_904_223
      val rgb = seed and 0xFFFFFF
      image.setRGB(x, y, rgb)
      if (x + 1 < width) image.setRGB(x + 1, y, rgb)
      if (y + 1 < height) image.setRGB(x, y + 1, rgb)
      if (x + 1 < width && y + 1 < height) image.setRGB(x + 1, y + 1, rgb)
    }
  }
  return ByteArrayOutputStream().use { out ->
    ImageIO.write(image, "jpg", out)
    out.toByteArray()
  }
}
