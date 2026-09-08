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
import java.io.ByteArrayOutputStream

/**
 * Real encoded images, made by the platform encoder the decoder under test will read back.
 *
 * A stub that hands over an already decoded bitmap skips the format, the header, the colour space
 * and the sampling, which between them are most of what a decoder does.
 */
object ImageFixtures {

  /** A solid [color] image, encoded as [format]. */
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

  /**
   * An image with enough detail that an encoder cannot collapse it, for decode timing.
   *
   * Four quadrants of flat colour plus noise, so a downscale of it is still recognisably it and a
   * test can tell one fixture from another by reading one pixel.
   */
  fun photo(
    width: Int,
    height: Int,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
  ): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val quadrants = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
    for (index in quadrants.indices) {
      paint.color = quadrants[index]
      val left = if (index % 2 == 0) 0f else width / 2f
      val top = if (index < 2) 0f else height / 2f
      canvas.drawRect(left, top, left + width / 2f, top + height / 2f, paint)
    }
    var seed = 0x9E3779B9.toInt()
    for (y in 0 until height step 3) {
      for (x in 0 until width step 3) {
        seed = seed * 1_664_525 + 1_013_904_223
        paint.color = seed or (0xFF shl 24)
        canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
      }
    }
    return encode(bitmap, format, quality = 90)
  }

  /** Bytes that begin like a JPEG and stop part way, which is a cut off download. */
  fun truncatedJpeg(width: Int = 64, height: Int = 64): ByteArray {
    val whole = photo(width, height)
    return whole.copyOf(whole.size / 3)
  }

  /** Bytes no decoder can read at all. */
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
}
