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

import com.skydoves.landscapist.core.LandscapistConfig
import kotlinx.coroutines.flow.Flow

/**
 * Result of a progressive decode operation.
 * Contains intermediate and final image results.
 */
public sealed class ProgressiveDecodeResult {
  /**
   * An intermediate image during progressive decode.
   *
   * @property bitmap The partially decoded bitmap.
   * @property width The width of the image.
   * @property height The height of the image.
   * @property progress The decode progress from 0.0 to 1.0.
   * @property isPreview Whether this is a low-quality preview (e.g., from JPEG baseline scan).
   */
  public data class Intermediate(
    val bitmap: Any,
    val width: Int,
    val height: Int,
    val progress: Float,
    val isPreview: Boolean = false,
  ) : ProgressiveDecodeResult()

  /**
   * The final decoded image.
   *
   * @property bitmap The fully decoded bitmap.
   * @property width The width of the image.
   * @property height The height of the image.
   */
  public data class Complete(
    val bitmap: Any,
    val width: Int,
    val height: Int,
  ) : ProgressiveDecodeResult()

  /**
   * Decode failed with an error.
   *
   * @property throwable The exception that caused the failure.
   */
  public data class Error(
    val throwable: Throwable,
  ) : ProgressiveDecodeResult()
}

/**
 * Interface for progressive image decoding.
 *
 * Progressive decoding shows intermediate image results as the image is being decoded,
 * providing a better perceived loading experience. This is particularly useful for:
 * - Progressive JPEG images (shows blurry → sharp)
 * - Large images that take time to decode
 * - Slow network connections where streaming partial data is beneficial
 */
public interface ProgressiveDecoder {
  /**
   * Decodes image data progressively, emitting intermediate results.
   *
   * @param data The raw image data as a byte array.
   * @param mimeType The MIME type of the image, if known.
   * @param targetWidth Target width for the decoded image, or null for original size.
   * @param targetHeight Target height for the decoded image, or null for original size.
   * @param config The Landscapist configuration.
   * @return A Flow emitting [ProgressiveDecodeResult] for intermediate and final images.
   */
  public fun decodeProgressive(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): Flow<ProgressiveDecodeResult>

  /**
   * Checks if the given image data supports progressive decoding.
   *
   * @param data The raw image data.
   * @param mimeType The MIME type, if known.
   * @return true if the image can be decoded progressively.
   */
  public fun supportsProgressiveDecode(data: ByteArray, mimeType: String?): Boolean
}

/**
 * Creates a platform-specific progressive image decoder.
 */
public expect fun createProgressiveDecoder(): ProgressiveDecoder

/**
 * Utility to detect if a JPEG is progressive/interlaced.
 */
public object ProgressiveImageDetector {
  /**
   * Checks if a JPEG image is encoded as progressive.
   * Progressive JPEGs have SOF2 (0xFFC2) marker instead of SOF0 (0xFFC0).
   */
  public fun isProgressiveJpeg(data: ByteArray): Boolean {
    if (data.size < 3) return false

    // Check JPEG signature
    if (data[0] != 0xFF.toByte() || data[1] != 0xD8.toByte()) return false

    var i = 2
    while (i < data.size - 1) {
      if (data[i] != 0xFF.toByte()) {
        i++
        continue
      }

      val marker = data[i + 1].toInt() and 0xFF

      // SOF2 = Progressive DCT
      if (marker == 0xC2) return true

      // SOF0 = Baseline DCT (not progressive)
      if (marker == 0xC0) return false

      // Skip to next marker
      if (marker in 0xD0..0xD9 || marker == 0x01) {
        // Standalone markers
        i += 2
      } else if (i + 3 < data.size) {
        // Read segment length
        val length = ((data[i + 2].toInt() and 0xFF) shl 8) or
          (data[i + 3].toInt() and 0xFF)
        i += 2 + length
      } else {
        break
      }
    }

    return false
  }

  /**
   * Checks if a PNG is interlaced (Adam7 interlacing).
   * Interlaced PNGs can be rendered progressively.
   */
  public fun isInterlacedPng(data: ByteArray): Boolean {
    if (data.size < 29) return false

    // Check PNG signature
    if (data[0] != 0x89.toByte() ||
      data[1] != 'P'.code.toByte() ||
      data[2] != 'N'.code.toByte() ||
      data[3] != 'G'.code.toByte()
    ) {
      return false
    }

    // IHDR chunk starts at offset 8, interlace method is at offset 28
    // IHDR: length(4) + "IHDR"(4) + width(4) + height(4) + bitDepth(1) +
    //       colorType(1) + compression(1) + filter(1) + interlace(1)
    val interlaceMethod = data[28].toInt() and 0xFF
    return interlaceMethod == 1 // Adam7 interlacing
  }

  /**
   * Checks if an image supports progressive/interlaced rendering.
   */
  public fun supportsProgressive(data: ByteArray, mimeType: String?): Boolean {
    return when {
      mimeType == "image/jpeg" || isJpeg(data) -> isProgressiveJpeg(data)
      mimeType == "image/png" || isPng(data) -> isInterlacedPng(data)
      else -> false
    }
  }

  private fun isJpeg(data: ByteArray): Boolean {
    return data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()
  }

  private fun isPng(data: ByteArray): Boolean {
    return data.size >= 4 &&
      data[0] == 0x89.toByte() &&
      data[1] == 'P'.code.toByte() &&
      data[2] == 'N'.code.toByte() &&
      data[3] == 'G'.code.toByte()
  }
}
