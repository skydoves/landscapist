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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageDimensionsTest {

  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

  @Test
  fun readsPngDimensions() {
    val png = bytes(
      0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // signature
      0x00, 0x00, 0x00, 0x0D, // IHDR length
      'I'.code, 'H'.code, 'D'.code, 'R'.code,
      0x00, 0x00, 0x03, 0x20, // width = 800
      0x00, 0x00, 0x02, 0x58, // height = 600
    )
    assertEquals(ImageSize(800, 600), readImageDimensions(png))
  }

  @Test
  fun readsGifDimensions() {
    val gif = bytes(
      'G'.code, 'I'.code, 'F'.code, '8'.code, '9'.code, 'a'.code,
      0x80, 0x02, // width = 640 (little-endian)
      0xE0, 0x01, // height = 480 (little-endian)
    )
    assertEquals(ImageSize(640, 480), readImageDimensions(gif))
  }

  @Test
  fun readsJpegDimensionsFromSof0() {
    val jpeg = bytes(
      0xFF, 0xD8, // SOI
      0xFF, 0xC0, 0x00, 0x11, 0x08, // SOF0 marker, length, precision
      0x03, 0x00, // height = 768
      0x04, 0x00, // width = 1024
    )
    assertEquals(ImageSize(1024, 768), readImageDimensions(jpeg))
  }

  @Test
  fun skipsJpegApp0SegmentBeforeSof() {
    val jpeg = bytes(
      0xFF, 0xD8, // SOI
      0xFF, 0xE0, 0x00, 0x04, 0x00, 0x00, // APP0 segment (length 4)
      0xFF, 0xC0, 0x00, 0x11, 0x08, // SOF0
      0x01, 0x00, // height = 256
      0x02, 0x00, // width = 512
    )
    assertEquals(ImageSize(512, 256), readImageDimensions(jpeg))
  }

  @Test
  fun readsWebPLossyDimensions() {
    val webp = bytes(
      'R'.code, 'I'.code, 'F'.code, 'F'.code,
      0x00, 0x00, 0x00, 0x00, // file size (unused)
      'W'.code, 'E'.code, 'B'.code, 'P'.code,
      'V'.code, 'P'.code, '8'.code, 0x20, // "VP8 "
      0x00, 0x00, 0x00, 0x00, // chunk size
      0x00, 0x00, 0x00, // frame tag
      0x9D, 0x01, 0x2A, // start code
      0x2C, 0x01, // width = 300
      0xC8, 0x00, // height = 200
    )
    assertEquals(ImageSize(300, 200), readImageDimensions(webp))
  }

  @Test
  fun returnsNullForUnknownFormat() {
    assertNull(readImageDimensions(bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)))
  }

  @Test
  fun readsWebPLosslessDimensions() {
    // VP8L packs (width-1) in bits 0..13 and (height-1) in bits 14..27, little-endian from byte 21.
    // width = 16, height = 32 -> width-1 = 15 (0x0F), height-1 = 31; packed = 0x0007C00F.
    val webp = bytes(
      'R'.code, 'I'.code, 'F'.code, 'F'.code,
      0x00, 0x00, 0x00, 0x00,
      'W'.code, 'E'.code, 'B'.code, 'P'.code,
      'V'.code, 'P'.code, '8'.code, 'L'.code,
      0x00, 0x00, 0x00, 0x00, // chunk size
      0x2F, // signature
      0x0F, 0xC0, 0x07, 0x00, // packed width/height
      0x00, 0x00, 0x00, 0x00, 0x00, // padding to satisfy the 30-byte guard
    )
    assertEquals(ImageSize(16, 32), readImageDimensions(webp))
  }

  @Test
  fun readsWebPExtendedDimensions() {
    // VP8X canvas: (width-1) and (height-1) as 24-bit little-endian at offsets 24 and 27.
    // width = 1000 -> 999 = 0x0003E7, height = 2000 -> 1999 = 0x0007CF.
    val webp = bytes(
      'R'.code, 'I'.code, 'F'.code, 'F'.code,
      0x00, 0x00, 0x00, 0x00,
      'W'.code, 'E'.code, 'B'.code, 'P'.code,
      'V'.code, 'P'.code, '8'.code, 'X'.code,
      0x00, 0x00, 0x00, 0x00, // chunk size
      0x00, // flags
      0x00, 0x00, 0x00, // reserved
      0xE7, 0x03, 0x00, // width - 1
      0xCF, 0x07, 0x00, // height - 1
    )
    assertEquals(ImageSize(1000, 2000), readImageDimensions(webp))
  }

  @Test
  fun readsProgressiveJpegSof2Dimensions() {
    val jpeg = bytes(
      0xFF, 0xD8,
      0xFF, 0xC2, 0x00, 0x11, 0x08, // SOF2 (progressive)
      0x02, 0x00, // height = 512
      0x03, 0x00, // width = 768
    )
    assertEquals(ImageSize(768, 512), readImageDimensions(jpeg))
  }

  @Test
  fun readsLargePngWithoutOverflow() {
    val png = bytes(
      0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
      0x00, 0x00, 0x00, 0x0D,
      'I'.code, 'H'.code, 'D'.code, 'R'.code,
      0x00, 0x00, 0x4E, 0x20, // width = 20000
      0x00, 0x00, 0x4E, 0x20, // height = 20000
    )
    assertEquals(ImageSize(20000, 20000), readImageDimensions(png))
  }

  @Test
  fun returnsNullForJpegTruncatedBeforeDimensions() {
    // SOF0 marker present but the segment ends before width/height; must not throw.
    assertNull(readImageDimensions(bytes(0xFF, 0xD8, 0xFF, 0xC0, 0x00, 0x11, 0x08, 0x01)))
  }

  @Test
  fun returnsNullForJpegWithZeroLengthSegment() {
    // A segment length below 2 would loop forever if unguarded; must terminate and return null.
    assertNull(readImageDimensions(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x00, 0x00, 0x00)))
  }

  @Test
  fun returnsNullForGifWithWrongVersion() {
    assertNull(
      readImageDimensions(
        bytes('G'.code, 'I'.code, 'F'.code, '8'.code, '0'.code, 'a'.code, 0x80, 0x02, 0xE0, 0x01),
      ),
    )
  }

  @Test
  fun returnsNullForEmptyInput() {
    assertNull(readImageDimensions(ByteArray(0)))
  }

  @Test
  fun returnsNullForTruncatedHeader() {
    assertNull(readImageDimensions(bytes(0x89, 0x50)))
  }
}
