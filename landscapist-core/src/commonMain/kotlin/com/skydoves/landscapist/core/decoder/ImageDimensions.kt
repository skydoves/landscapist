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
 * Reads the pixel dimensions from the header of an encoded image without fully decoding it.
 *
 * Supports PNG, JPEG, GIF, and WebP, which cover essentially all network images. Returns null when
 * the format is unrecognized or the header is incomplete. This is pure Kotlin with no platform
 * dependency, so platforms that defer the actual decode to the UI layer (Apple, Web) can still
 * report a real size for memory-cache accounting without pulling in a decoder.
 */
internal fun readImageDimensions(bytes: ByteArray): ImageSize? =
  readPng(bytes) ?: readGif(bytes) ?: readWebP(bytes) ?: readJpeg(bytes)

private fun readPng(b: ByteArray): ImageSize? {
  // 8-byte signature, then IHDR chunk: length(4) "IHDR"(4) width(4) height(4).
  if (b.size < 24) return null
  if (b.u(0) != 0x89 || b.u(1) != 0x50 || b.u(2) != 0x4E || b.u(3) != 0x47 ||
    b.u(4) != 0x0D || b.u(5) != 0x0A || b.u(6) != 0x1A || b.u(7) != 0x0A
  ) {
    return null
  }
  if (b.u(12) != 'I'.code || b.u(13) != 'H'.code || b.u(14) != 'D'.code || b.u(15) != 'R'.code) {
    return null
  }
  return sizeOrNull(b.i32be(16), b.i32be(20))
}

private fun readGif(b: ByteArray): ImageSize? {
  // "GIF87a" / "GIF89a", logical screen width/height are little-endian at offsets 6 and 8.
  if (b.size < 10) return null
  if (b.u(0) != 'G'.code || b.u(1) != 'I'.code || b.u(2) != 'F'.code || b.u(3) != '8'.code) {
    return null
  }
  if ((b.u(4) != '7'.code && b.u(4) != '9'.code) || b.u(5) != 'a'.code) return null
  return sizeOrNull(b.u16le(6), b.u16le(8))
}

private fun readWebP(b: ByteArray): ImageSize? {
  // RIFF container with a "WEBP" form type, then a VP8 / VP8L / VP8X chunk.
  if (b.size < 30) return null
  if (b.u(0) != 'R'.code || b.u(1) != 'I'.code || b.u(2) != 'F'.code || b.u(3) != 'F'.code) {
    return null
  }
  if (b.u(8) != 'W'.code || b.u(9) != 'E'.code || b.u(10) != 'B'.code || b.u(11) != 'P'.code) {
    return null
  }
  return when {
    // Lossy: start code 0x9d 0x01 0x2a at 23, then 14-bit width/height little-endian.
    b.u(12) == 'V'.code && b.u(13) == 'P'.code && b.u(14) == '8'.code && b.u(15) == ' '.code ->
      sizeOrNull(b.u16le(26) and 0x3FFF, b.u16le(28) and 0x3FFF)

    // Lossless: 0x2f signature at 20, then 14-bit (width-1) and 14-bit (height-1).
    b.u(12) == 'V'.code && b.u(15) == 'L'.code -> {
      val bits = b.u(21) or (b.u(22) shl 8) or (b.u(23) shl 16) or (b.u(24) shl 24)
      sizeOrNull((bits and 0x3FFF) + 1, ((bits shr 14) and 0x3FFF) + 1)
    }

    // Extended: canvas (width-1) and (height-1) as 24-bit little-endian at 24 and 27.
    b.u(12) == 'V'.code && b.u(15) == 'X'.code ->
      sizeOrNull(b.u24le(24) + 1, b.u24le(27) + 1)

    else -> null
  }
}

private fun readJpeg(b: ByteArray): ImageSize? {
  if (b.size < 4 || b.u(0) != 0xFF || b.u(1) != 0xD8) return null
  var offset = 2
  while (offset + 1 < b.size) {
    if (b.u(offset) != 0xFF) {
      offset++
      continue
    }
    val marker = b.u(offset + 1)
    when {
      // Padding fill byte.
      marker == 0xFF -> offset++
      // Standalone markers without a length payload (TEM, RSTn, SOI, EOI).
      marker == 0x01 || marker in 0xD0..0xD9 -> offset += 2
      // Start Of Frame markers carry: length(2) precision(1) height(2) width(2).
      marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC -> {
        if (offset + 9 > b.size) return null
        return sizeOrNull(b.u16be(offset + 7), b.u16be(offset + 5))
      }
      else -> {
        if (offset + 4 > b.size) return null
        val segmentLength = b.u16be(offset + 2)
        if (segmentLength < 2) return null
        offset += 2 + segmentLength
      }
    }
  }
  return null
}

private fun sizeOrNull(width: Int, height: Int): ImageSize? =
  if (width > 0 && height > 0) ImageSize(width, height) else null

private fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.u16be(index: Int): Int = (u(index) shl 8) or u(index + 1)

private fun ByteArray.u16le(index: Int): Int = u(index) or (u(index + 1) shl 8)

private fun ByteArray.u24le(index: Int): Int =
  u(index) or (u(index + 1) shl 8) or (u(index + 2) shl 16)

private fun ByteArray.i32be(index: Int): Int =
  (u(index) shl 24) or (u(index + 1) shl 16) or (u(index + 2) shl 8) or u(index + 3)
