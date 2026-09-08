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
package com.github.skydoves.landscapistdemo.harness

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import java.io.ByteArrayOutputStream

/** Real encoded images, so the format, header, colour space and sampling are all real. */
object ImageFixtures {

  /** The width and the height of [animatedGif], in pixels. */
  const val ANIMATED_GIF_SIDE: Int = 8

  fun solid(
    width: Int,
    height: Int,
    color: Int = Color.RED,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 95,
  ): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(color)
    return encode(bitmap, format, quality)
  }

  /** Four quadrants plus noise, so a downscale is still identifiable from one pixel. */
  fun photo(
    width: Int,
    height: Int,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
  ): ByteArray = encode(quadrants(width, height, transparent = false), format, quality = 90)

  /**
   * WebP. The explicit lossy and lossless formats arrived in API 30, above this module's minSdk,
   * so below that the deprecated WEBP stands in: it is lossless at quality 100, lossy under it.
   * Only a lossy file with alpha carries the VP8X chunk the animation check reads.
   */
  fun webp(
    width: Int,
    height: Int,
    lossless: Boolean = false,
    transparent: Boolean = false,
  ): ByteArray = encode(
    bitmap = quadrants(width, height, transparent),
    format = webpFormat(lossless),
    quality = if (lossless) 100 else 90,
  )

  /** An animated GIF, which [Bitmap.compress] cannot produce. See [ANIMATED_GIF]. */
  fun animatedGif(): ByteArray = ANIMATED_GIF.copyOf()

  fun truncatedJpeg(width: Int = 64, height: Int = 64): ByteArray {
    val whole = photo(width, height)
    return whole.copyOf(whole.size / 3)
  }

  fun garbage(size: Int = 512): ByteArray = ByteArray(size) { (it * 31).toByte() }

  private fun encode(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    quality: Int,
  ): ByteArray = ByteArrayOutputStream().use { out ->
    check(bitmap.compress(format, quality, out)) { "could not encode a $format fixture" }
    bitmap.recycle()
    out.toByteArray()
  }

  /** The first quadrant is left unpainted when [transparent], so the alpha there is really zero. */
  private fun quadrants(width: Int, height: Int, transparent: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val quadrants = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
    for (index in quadrants.indices) {
      if (transparent && index == 0) continue
      paint.color = quadrants[index]
      val left = if (index % 2 == 0) 0f else width / 2f
      val top = if (index < 2) 0f else height / 2f
      canvas.drawRect(left, top, left + width / 2f, top + height / 2f, paint)
    }
    var seed = 0x9E3779B9.toInt()
    for (y in 0 until height step 3) {
      for (x in 0 until width step 3) {
        seed = seed * 1_664_525 + 1_013_904_223
        if (transparent && x < width / 2 && y < height / 2) continue
        paint.color = seed or (0xFF shl 24)
        canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
      }
    }
    return bitmap
  }

  @Suppress("DEPRECATION")
  private fun webpFormat(lossless: Boolean): Bitmap.CompressFormat {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Bitmap.CompressFormat.WEBP
    return if (lossless) {
      Bitmap.CompressFormat.WEBP_LOSSLESS
    } else {
      Bitmap.CompressFormat.WEBP_LOSSY
    }
  }

  /**
   * A GIF89a of two 8x8 frames, opaque red then opaque blue, a tenth of a second apart, looping.
   *
   * Each frame's pixels are one 9 bit LZW literal each, after a clear code, so the dictionary never
   * reaches 0x200 and every code in the block stands for the pixel it names.
   */
  private val ANIMATED_GIF: ByteArray = hex(
    "474946383961", // GIF89a.
    "08000800f00000", // 8x8, a global colour table of two, background index 0.
    "ff00000000ff", // The table: opaque red, then opaque blue.
    "21ff0b4e45545343415045322e300301000000", // NETSCAPE2.0, loop forever.
    "21f904040a000000", // Frame 1: keep the canvas, hold for 100ms.
    "2c000000000800080000", // Frame 1: the whole canvas, no local colour table.
    "084b", // Frame 1: 8 bit codes, one 75 byte block.
    // Clear (0x100), 64 nine bit literals of colour 0, end (0x101).
    "0001000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
    "00000000000000000000000000000000000000000000000000000000000202",
    "00", // Block terminator.
    "21f904040a000000", // Frame 2: keep the canvas, hold for 100ms.
    "2c000000000800080000", // Frame 2: the whole canvas, no local colour table.
    "084b", // Frame 2: 8 bit codes, one 75 byte block.
    // Clear (0x100), 64 nine bit literals of colour 1, end (0x101).
    "0003040810204080000102040810204080000102040810204080000102040810204080000102040810204080",
    "00010204081020408000010204081020408000010204081020408000010202",
    "00", // Block terminator.
    "3b", // Trailer.
  )

  private fun hex(vararg parts: String): ByteArray {
    val digits = parts.joinToString(separator = "")
    return ByteArray(digits.length / 2) {
      digits.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
  }
}
