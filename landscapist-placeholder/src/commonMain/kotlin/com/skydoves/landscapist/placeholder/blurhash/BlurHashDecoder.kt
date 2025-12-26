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
package com.skydoves.landscapist.placeholder.blurhash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * BlurHash decoder that converts a BlurHash string into pixel data.
 *
 * BlurHash is a compact representation of a placeholder for an image.
 * It encodes the image into a short string (~20-30 characters) that can be
 * decoded into a blurred placeholder image instantly.
 *
 * @see <a href="https://blurha.sh">BlurHash</a>
 */
public object BlurHashDecoder {

  private val BASE83_CHARS = buildList {
    addAll('0'..'9')
    addAll('A'..'Z')
    addAll('a'..'z')
    add('#')
    add('$')
    add('%')
    add('*')
    add('+')
    add(',')
    add('-')
    add('.')
    add(':')
    add(';')
    add('=')
    add('?')
    add('@')
    add('[')
    add(']')
    add('^')
    add('_')
    add('{')
    add('|')
    add('}')
    add('~')
  }

  private val BASE83_LOOKUP = BASE83_CHARS.mapIndexed { index, c -> c to index }.toMap()

  /**
   * Decodes a BlurHash string into ARGB pixel data.
   *
   * @param blurHash The BlurHash string to decode.
   * @param width The width of the output image in pixels.
   * @param height The height of the output image in pixels.
   * @param punch The factor to increase the contrast of the decoded image. Default is 1.0.
   * @return An IntArray of ARGB pixel data, or null if decoding fails.
   */
  public fun decode(
    blurHash: String,
    width: Int,
    height: Int,
    punch: Float = 1.0f,
  ): IntArray? {
    if (blurHash.length < 6) return null

    val sizeFlag = decodeBase83(blurHash, 0, 1) ?: return null
    val numY = (sizeFlag / 9) + 1
    val numX = (sizeFlag % 9) + 1

    val quantisedMaximumValue = decodeBase83(blurHash, 1, 2) ?: return null
    val maximumValue = (quantisedMaximumValue + 1) / 166f

    val colors = Array(numX * numY) { i ->
      if (i == 0) {
        val value = decodeBase83(blurHash, 2, 6) ?: return null
        decodeDC(value)
      } else {
        val startIndex = 4 + i * 2
        if (startIndex + 2 > blurHash.length) return null
        val value = decodeBase83(blurHash, startIndex, startIndex + 2) ?: return null
        decodeAC(value, maximumValue * punch)
      }
    }

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
      for (x in 0 until width) {
        var r = 0f
        var g = 0f
        var b = 0f

        for (j in 0 until numY) {
          for (i in 0 until numX) {
            val basis = cos((PI * x * i) / width) * cos((PI * y * j) / height)
            val color = colors[i + j * numX]
            r += color[0] * basis.toFloat()
            g += color[1] * basis.toFloat()
            b += color[2] * basis.toFloat()
          }
        }

        val intR = linearToSrgb(r)
        val intG = linearToSrgb(g)
        val intB = linearToSrgb(b)

        pixels[x + y * width] = (0xFF shl 24) or (intR shl 16) or (intG shl 8) or intB
      }
    }

    return pixels
  }

  /**
   * Validates if a string is a valid BlurHash.
   *
   * @param blurHash The string to validate.
   * @return true if the string is a valid BlurHash, false otherwise.
   */
  public fun isValidBlurHash(blurHash: String): Boolean {
    if (blurHash.length < 6) return false

    val sizeFlag = decodeBase83(blurHash, 0, 1) ?: return false
    val numY = (sizeFlag / 9) + 1
    val numX = (sizeFlag % 9) + 1

    val expectedLength = 4 + 2 * numX * numY
    return blurHash.length == expectedLength
  }

  private fun decodeBase83(str: String, from: Int, to: Int): Int? {
    var value = 0
    for (i in from until to) {
      val c = str.getOrNull(i) ?: return null
      val digit = BASE83_LOOKUP[c] ?: return null
      value = value * 83 + digit
    }
    return value
  }

  private fun decodeDC(value: Int): FloatArray {
    val intR = value shr 16
    val intG = (value shr 8) and 255
    val intB = value and 255
    return floatArrayOf(srgbToLinear(intR), srgbToLinear(intG), srgbToLinear(intB))
  }

  private fun decodeAC(value: Int, maximumValue: Float): FloatArray {
    val quantR = value / (19 * 19)
    val quantG = (value / 19) % 19
    val quantB = value % 19
    return floatArrayOf(
      signPow((quantR - 9) / 9f, 2f) * maximumValue,
      signPow((quantG - 9) / 9f, 2f) * maximumValue,
      signPow((quantB - 9) / 9f, 2f) * maximumValue,
    )
  }

  private fun srgbToLinear(value: Int): Float {
    val v = value / 255f
    return if (v <= 0.04045f) {
      v / 12.92f
    } else {
      ((v + 0.055f) / 1.055f).pow(2.4f)
    }
  }

  private fun linearToSrgb(value: Float): Int {
    val v = value.coerceIn(0f, 1f)
    val srgb = if (v <= 0.0031308f) {
      v * 12.92f
    } else {
      1.055f * v.pow(1f / 2.4f) - 0.055f
    }
    return (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
  }

  private fun signPow(value: Float, exp: Float): Float {
    return value.pow(exp).withSign(value)
  }
}
