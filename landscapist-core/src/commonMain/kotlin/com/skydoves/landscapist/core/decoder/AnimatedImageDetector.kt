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

/**
 * Utility object for detecting animated image formats.
 */
internal object AnimatedImageDetector {

  /**
   * Checks if the given image data represents an animated format (GIF, animated WebP, APNG).
   *
   * @param data The raw image bytes.
   * @param mimeType Optional MIME type hint.
   * @return true if the image is likely animated.
   */
  fun isAnimated(data: ByteArray, mimeType: String?): Boolean {
    // Check mime type first
    if (mimeType == "image/gif") return true

    // Check for GIF signature
    if (isGif(data)) return true

    // Check for animated WebP
    if (mimeType == "image/webp" || isWebP(data)) {
      return isAnimatedWebP(data)
    }

    // Check for APNG
    if (mimeType == "image/apng" || mimeType == "image/png" || isPng(data)) {
      return isAnimatedPng(data)
    }

    return false
  }

  private fun isGif(data: ByteArray): Boolean {
    if (data.size < 6) return false
    return data[0] == 'G'.code.toByte() &&
      data[1] == 'I'.code.toByte() &&
      data[2] == 'F'.code.toByte()
  }

  private fun isWebP(data: ByteArray): Boolean {
    if (data.size < 12) return false
    return data[0] == 'R'.code.toByte() &&
      data[1] == 'I'.code.toByte() &&
      data[2] == 'F'.code.toByte() &&
      data[3] == 'F'.code.toByte() &&
      data[8] == 'W'.code.toByte() &&
      data[9] == 'E'.code.toByte() &&
      data[10] == 'B'.code.toByte() &&
      data[11] == 'P'.code.toByte()
  }

  private fun isPng(data: ByteArray): Boolean {
    if (data.size < 8) return false
    return data[0] == 0x89.toByte() &&
      data[1] == 'P'.code.toByte() &&
      data[2] == 'N'.code.toByte() &&
      data[3] == 'G'.code.toByte()
  }

  private fun isAnimatedWebP(data: ByteArray): Boolean {
    if (data.size < 30) return false

    // Look for VP8X chunk with animation flag or ANIM chunk
    var offset = 12
    while (offset < data.size - 8) {
      val chunkType = getChunkType(data, offset)
      if (chunkType == "VP8X" && offset + 8 < data.size) {
        // Check animation flag (bit 1)
        val flags = data[offset + 8].toInt() and 0xFF
        if (flags and 0x02 != 0) return true
      }
      if (chunkType == "ANIM") return true

      // Get chunk size (little-endian)
      if (offset + 7 >= data.size) break
      val chunkSize = (data[offset + 4].toInt() and 0xFF) or
        ((data[offset + 5].toInt() and 0xFF) shl 8) or
        ((data[offset + 6].toInt() and 0xFF) shl 16) or
        ((data[offset + 7].toInt() and 0xFF) shl 24)

      // Move to next chunk (chunk size + 8 for header, padded to even)
      offset += 8 + chunkSize + (chunkSize and 1)

      // Safety check
      if (chunkSize < 0 || offset < 0) break
    }

    return false
  }

  private fun isAnimatedPng(data: ByteArray): Boolean {
    if (data.size < 8) return false

    // Search for acTL chunk (animation control)
    var offset = 8
    while (offset < data.size - 12) {
      // Get chunk length (big-endian)
      val length = ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

      val chunkType = getChunkType(data, offset + 4)
      if (chunkType == "acTL") return true
      if (chunkType == "IDAT") return false // acTL must come before IDAT

      // Move to next chunk (4 length + 4 type + data + 4 crc)
      offset += 12 + length

      // Safety check
      if (length < 0 || offset < 0) break
    }

    return false
  }

  private fun getChunkType(data: ByteArray, offset: Int): String {
    if (offset + 4 > data.size) return ""
    return buildString {
      for (i in 0 until 4) {
        append(data[offset + i].toInt().toChar())
      }
    }
  }
}
