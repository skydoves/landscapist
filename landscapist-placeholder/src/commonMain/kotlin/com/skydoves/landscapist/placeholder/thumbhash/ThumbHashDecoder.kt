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
package com.skydoves.landscapist.placeholder.thumbhash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * ThumbHash decoder that converts a ThumbHash byte array into pixel data.
 *
 * ThumbHash is a more modern alternative to BlurHash with several advantages:
 * - Preserves the aspect ratio of the original image
 * - Supports alpha channel (transparency)
 * - Better color accuracy
 * - Smaller encoded size (~25 bytes on average)
 *
 * @see <a href="https://evanw.github.io/thumbhash/">ThumbHash</a>
 */
public object ThumbHashDecoder {

  /**
   * Result of decoding a ThumbHash, containing both pixel data and dimensions.
   *
   * @property width The width of the decoded image.
   * @property height The height of the decoded image.
   * @property rgba The RGBA pixel data as a ByteArray.
   */
  public data class ThumbHashImage(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
  ) {
    /**
     * Converts RGBA byte array to ARGB IntArray for easier rendering.
     */
    public fun toArgbIntArray(): IntArray {
      val pixels = IntArray(width * height)
      for (i in pixels.indices) {
        val r = rgba[i * 4].toInt() and 0xFF
        val g = rgba[i * 4 + 1].toInt() and 0xFF
        val b = rgba[i * 4 + 2].toInt() and 0xFF
        val a = rgba[i * 4 + 3].toInt() and 0xFF
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
      }
      return pixels
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false

      other as ThumbHashImage

      if (width != other.width) return false
      if (height != other.height) return false
      if (!rgba.contentEquals(other.rgba)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = width
      result = 31 * result + height
      result = 31 * result + rgba.contentHashCode()
      return result
    }
  }

  /**
   * Decodes a ThumbHash byte array to image data.
   *
   * @param hash The ThumbHash as a byte array.
   * @return ThumbHashImage containing width, height, and RGBA pixel data, or null if invalid.
   */
  public fun decode(hash: ByteArray): ThumbHashImage? {
    if (hash.size < 5) return null

    // Read the header
    val header24 = (hash[0].toInt() and 0xFF) or
      ((hash[1].toInt() and 0xFF) shl 8) or
      ((hash[2].toInt() and 0xFF) shl 16)
    val header16 = (hash[3].toInt() and 0xFF) or ((hash[4].toInt() and 0xFF) shl 8)

    val lDc = header24 and 63
    val pDc = (header24 shr 6) and 63
    val qDc = (header24 shr 12) and 63
    val lScale = (header24 shr 18) and 31
    val hasAlpha = (header24 shr 23) and 1 != 0

    val pScale = (header16 shr 0) and 63
    val qScale = (header16 shr 6) and 63
    val isLandscape = (header16 shr 12) and 1 != 0

    val lx = max(3, if (isLandscape) if (hasAlpha) 5 else 7 else if (hasAlpha) 3 else 5)
    val ly = max(3, if (isLandscape) if (hasAlpha) 3 else 5 else if (hasAlpha) 5 else 7)

    // Calculate output dimensions
    val ratio = thumbHashToApproximateAspectRatio(hash) ?: return null
    val w: Int
    val h: Int
    if (ratio > 1f) {
      w = 32
      h = round(32f / ratio).toInt()
    } else {
      w = round(32f * ratio).toInt()
      h = 32
    }

    // Decode AC coefficients
    val ac = mutableListOf<Float>()
    var index = 5
    var bitIndex = 0

    fun readBits(count: Int): Int {
      var value = 0
      var remaining = count
      while (remaining > 0) {
        if (index >= hash.size) return value
        val bitsAvailable = 8 - bitIndex
        val bitsToRead = min(remaining, bitsAvailable)
        val mask = (1 shl bitsToRead) - 1
        val byteValue = (hash[index].toInt() and 0xFF) shr bitIndex
        value = value or ((byteValue and mask) shl (count - remaining))
        bitIndex += bitsToRead
        remaining -= bitsToRead
        if (bitIndex >= 8) {
          bitIndex = 0
          index++
        }
      }
      return value
    }

    // Decode L channel
    val lAc = mutableListOf<Float>()
    for (i in 0 until lx * ly - 1) {
      lAc.add((readBits(4) / 7.5f - 1f) * lScale / 31f)
    }

    // Decode P channel
    val pAc = mutableListOf<Float>()
    for (i in 0 until 3 * 3 - 1) {
      pAc.add((readBits(4) / 7.5f - 1f) * pScale / 63f)
    }

    // Decode Q channel
    val qAc = mutableListOf<Float>()
    for (i in 0 until 3 * 3 - 1) {
      qAc.add((readBits(4) / 7.5f - 1f) * qScale / 63f)
    }

    // Decode A channel if present
    var aDc = 1f
    var aScale = 0
    val aAc = mutableListOf<Float>()
    if (hasAlpha) {
      aDc = readBits(4) / 15f
      aScale = readBits(4)
      val ax = if (isLandscape) 5 else 3
      val ay = if (isLandscape) 3 else 5
      for (i in 0 until ax * ay - 1) {
        aAc.add((readBits(4) / 7.5f - 1f) * aScale / 15f)
      }
    }

    // Decode to pixels
    val rgba = ByteArray(w * h * 4)

    for (y in 0 until h) {
      for (x in 0 until w) {
        val fx = (x.toFloat() + 0.5f) / w
        val fy = (y.toFloat() + 0.5f) / h

        // Reconstruct L
        var l = lDc / 63f
        var k = 0
        for (cy in 0 until ly) {
          for (cx in 0 until lx) {
            if (cx == 0 && cy == 0) continue
            val basis = cos(PI * fx * cx) * cos(PI * fy * cy)
            if (k < lAc.size) {
              l += lAc[k] * basis.toFloat()
            }
            k++
          }
        }

        // Reconstruct P
        var p = pDc / 63f - 0.5f
        k = 0
        for (cy in 0 until 3) {
          for (cx in 0 until 3) {
            if (cx == 0 && cy == 0) continue
            val basis = cos(PI * fx * cx) * cos(PI * fy * cy)
            if (k < pAc.size) {
              p += pAc[k] * basis.toFloat()
            }
            k++
          }
        }

        // Reconstruct Q
        var q = qDc / 63f - 0.5f
        k = 0
        for (cy in 0 until 3) {
          for (cx in 0 until 3) {
            if (cx == 0 && cy == 0) continue
            val basis = cos(PI * fx * cx) * cos(PI * fy * cy)
            if (k < qAc.size) {
              q += qAc[k] * basis.toFloat()
            }
            k++
          }
        }

        // Reconstruct A
        var a = aDc
        if (hasAlpha) {
          val ax = if (isLandscape) 5 else 3
          val ay = if (isLandscape) 3 else 5
          k = 0
          for (cy in 0 until ay) {
            for (cx in 0 until ax) {
              if (cx == 0 && cy == 0) continue
              val basis = cos(PI * fx * cx) * cos(PI * fy * cy)
              if (k < aAc.size) {
                a += aAc[k] * basis.toFloat()
              }
              k++
            }
          }
        }

        // Convert from LPQ to RGB
        val b2 = l - 2f / 3f * p
        val r = (3f * l - b2 + q) / 2f
        val g = r - q
        val b = b2 - q

        val pixelIndex = (x + y * w) * 4
        rgba[pixelIndex] = (clamp(r) * 255f).toInt().toByte()
        rgba[pixelIndex + 1] = (clamp(g) * 255f).toInt().toByte()
        rgba[pixelIndex + 2] = (clamp(b) * 255f).toInt().toByte()
        rgba[pixelIndex + 3] = (clamp(a) * 255f).toInt().toByte()
      }
    }

    return ThumbHashImage(w, h, rgba)
  }

  /**
   * Decodes a Base64-encoded ThumbHash string.
   *
   * @param base64Hash The ThumbHash as a Base64 string.
   * @return ThumbHashImage, or null if invalid.
   */
  public fun decodeBase64(base64Hash: String): ThumbHashImage? {
    val bytes = base64Decode(base64Hash) ?: return null
    return decode(bytes)
  }

  /**
   * Extracts the approximate aspect ratio from a ThumbHash.
   *
   * @param hash The ThumbHash byte array.
   * @return The approximate aspect ratio (width / height), or null if invalid.
   */
  public fun thumbHashToApproximateAspectRatio(hash: ByteArray): Float? {
    if (hash.size < 5) return null

    val header = hash[3].toInt() and 0xFF
    val hasAlpha = ((hash[2].toInt() and 0xFF) shr 7) != 0
    val isLandscape = (header shr 4) and 1 != 0

    val lx = max(3, if (isLandscape) if (hasAlpha) 5 else 7 else if (hasAlpha) 3 else 5)
    val ly = max(3, if (isLandscape) if (hasAlpha) 3 else 5 else if (hasAlpha) 5 else 7)

    return lx.toFloat() / ly.toFloat()
  }

  private fun clamp(value: Float): Float = max(0f, min(1f, value))

  private fun base64Decode(input: String): ByteArray? {
    val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val cleanInput = input.replace("=", "")

    if (cleanInput.isEmpty()) return null

    val outputLength = cleanInput.length * 3 / 4
    val output = ByteArray(outputLength)

    var outputIndex = 0
    var buffer = 0
    var bitsCollected = 0

    for (c in cleanInput) {
      val value = base64Chars.indexOf(c)
      if (value < 0) return null

      buffer = (buffer shl 6) or value
      bitsCollected += 6

      if (bitsCollected >= 8) {
        bitsCollected -= 8
        output[outputIndex++] = (buffer shr bitsCollected).toByte()
        buffer = buffer and ((1 shl bitsCollected) - 1)
      }
    }

    return output
  }
}
