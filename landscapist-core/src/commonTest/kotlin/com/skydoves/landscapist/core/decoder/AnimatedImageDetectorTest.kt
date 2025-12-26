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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedImageDetectorTest {

  @Test
  fun `detects GIF by mime type`() {
    val data = ByteArray(10) // Empty data
    val result = AnimatedImageDetector.isAnimated(data, "image/gif")

    assertTrue(result)
  }

  @Test
  fun `detects GIF by file signature`() {
    // GIF89a signature
    val data = byteArrayOf(
      'G'.code.toByte(),
      'I'.code.toByte(),
      'F'.code.toByte(),
      '8'.code.toByte(),
      '9'.code.toByte(),
      'a'.code.toByte(),
    )
    val result = AnimatedImageDetector.isAnimated(data, null)

    assertTrue(result)
  }

  @Test
  fun `detects GIF87a by file signature`() {
    // GIF87a signature
    val data = byteArrayOf(
      'G'.code.toByte(),
      'I'.code.toByte(),
      'F'.code.toByte(),
      '8'.code.toByte(),
      '7'.code.toByte(),
      'a'.code.toByte(),
    )
    val result = AnimatedImageDetector.isAnimated(data, null)

    assertTrue(result)
  }

  @Test
  fun `returns false for JPEG`() {
    // JPEG signature (FFD8FF)
    val data = byteArrayOf(
      0xFF.toByte(),
      0xD8.toByte(),
      0xFF.toByte(),
      0xE0.toByte(),
    )
    val result = AnimatedImageDetector.isAnimated(data, "image/jpeg")

    assertFalse(result)
  }

  @Test
  fun `returns false for static PNG`() {
    // PNG signature without acTL chunk
    val data = buildPngWithoutAnimation()
    val result = AnimatedImageDetector.isAnimated(data, "image/png")

    assertFalse(result)
  }

  @Test
  fun `detects APNG by acTL chunk`() {
    val data = buildApngWithActl()
    val result = AnimatedImageDetector.isAnimated(data, "image/png")

    assertTrue(result)
  }

  @Test
  fun `detects APNG by mime type apng`() {
    val data = buildApngWithActl()
    val result = AnimatedImageDetector.isAnimated(data, "image/apng")

    assertTrue(result)
  }

  @Test
  fun `returns false for empty data`() {
    val data = ByteArray(0)
    val result = AnimatedImageDetector.isAnimated(data, null)

    assertFalse(result)
  }

  @Test
  fun `returns false for small data`() {
    val data = ByteArray(3) { 0 }
    val result = AnimatedImageDetector.isAnimated(data, null)

    assertFalse(result)
  }

  @Test
  fun `detects animated WebP with VP8X animation flag`() {
    val data = buildAnimatedWebPWithVP8X()
    val result = AnimatedImageDetector.isAnimated(data, "image/webp")

    assertTrue(result)
  }

  @Test
  fun `returns false for static WebP`() {
    val data = buildStaticWebP()
    val result = AnimatedImageDetector.isAnimated(data, "image/webp")

    assertFalse(result)
  }

  @Test
  fun `detects WebP by signature even without mime type`() {
    val data = buildAnimatedWebPWithVP8X()
    val result = AnimatedImageDetector.isAnimated(data, null)

    assertTrue(result)
  }

  // Helper functions to build test data

  private fun buildPngWithoutAnimation(): ByteArray {
    // PNG signature + IHDR chunk + IDAT chunk (simplified)
    return byteArrayOf(
      // PNG signature
      0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
      0x0D, 0x0A, 0x1A, 0x0A,
      // IHDR chunk (length = 13)
      0x00, 0x00, 0x00, 0x0D,
      'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
      // IHDR data (13 bytes)
      0x00, 0x00, 0x00, 0x01, // width
      0x00, 0x00, 0x00, 0x01, // height
      0x08, // bit depth
      0x02, // color type
      0x00, // compression
      0x00, // filter
      0x00, // interlace
      // CRC (4 bytes)
      0x00, 0x00, 0x00, 0x00,
      // IDAT chunk (length = 0)
      0x00, 0x00, 0x00, 0x00,
      'I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(),
      // CRC
      0x00, 0x00, 0x00, 0x00,
    )
  }

  private fun buildApngWithActl(): ByteArray {
    // PNG signature + IHDR chunk + acTL chunk + IDAT chunk
    return byteArrayOf(
      // PNG signature
      0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
      0x0D, 0x0A, 0x1A, 0x0A,
      // IHDR chunk (length = 13)
      0x00, 0x00, 0x00, 0x0D,
      'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
      // IHDR data (13 bytes)
      0x00, 0x00, 0x00, 0x01, // width
      0x00, 0x00, 0x00, 0x01, // height
      0x08, // bit depth
      0x02, // color type
      0x00, // compression
      0x00, // filter
      0x00, // interlace
      // CRC (4 bytes)
      0x00, 0x00, 0x00, 0x00,
      // acTL chunk (animation control, length = 8)
      0x00, 0x00, 0x00, 0x08,
      'a'.code.toByte(), 'c'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(),
      // acTL data (8 bytes)
      0x00, 0x00, 0x00, 0x02, // num_frames
      0x00, 0x00, 0x00, 0x00, // num_plays
      // CRC (4 bytes)
      0x00, 0x00, 0x00, 0x00,
      // IDAT chunk (length = 0)
      0x00, 0x00, 0x00, 0x00,
      'I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(),
      // CRC
      0x00, 0x00, 0x00, 0x00,
    )
  }

  private fun buildAnimatedWebPWithVP8X(): ByteArray {
    // RIFF header + WEBP + VP8X chunk with animation flag
    return byteArrayOf(
      // RIFF header
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      // File size (little-endian, placeholder)
      0x20, 0x00, 0x00, 0x00,
      // WEBP signature
      'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
      // VP8X chunk
      'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'X'.code.toByte(),
      // Chunk size (little-endian, 10 bytes)
      0x0A, 0x00, 0x00, 0x00,
      // Flags: animation bit (bit 1) set = 0x02
      0x02,
      // Reserved (3 bytes)
      0x00, 0x00, 0x00,
      // Canvas width - 1 (3 bytes, little-endian)
      0x00, 0x00, 0x00,
      // Canvas height - 1 (3 bytes, little-endian)
      0x00, 0x00, 0x00,
    )
  }

  private fun buildStaticWebP(): ByteArray {
    // RIFF header + WEBP + VP8 chunk (no VP8X, so static)
    return byteArrayOf(
      // RIFF header
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      // File size (little-endian)
      0x14, 0x00, 0x00, 0x00,
      // WEBP signature
      'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
      // VP8 chunk (static)
      'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), ' '.code.toByte(),
      // Chunk size (little-endian)
      0x04, 0x00, 0x00, 0x00,
      // Dummy VP8 data
      0x00, 0x00, 0x00, 0x00,
    )
  }
}
