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

/**
 * Interface for decoding image data into platform-specific bitmap objects.
 */
public interface ImageDecoder {
  /**
   * Decodes raw image data into a platform-specific bitmap.
   *
   * @param data The raw image data as a byte array.
   * @param mimeType The MIME type of the image, if known.
   * @param targetWidth Target width for the decoded image, or null for original size.
   * @param targetHeight Target height for the decoded image, or null for original size.
   * @param config The Landscapist configuration.
   * @return The decode result containing the bitmap or an error.
   */
  public suspend fun decode(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult
}

/**
 * Result of a decode operation.
 */
public sealed class DecodeResult {
  /**
   * Successful decode containing the bitmap.
   *
   * @property bitmap The decoded bitmap (platform-specific type).
   * @property width The width of the decoded image.
   * @property height The height of the decoded image.
   * @property isAnimated Whether the image is animated (GIF, APNG, animated WebP).
   */
  public data class Success(
    val bitmap: Any,
    val width: Int,
    val height: Int,
    val isAnimated: Boolean = false,
  ) : DecodeResult()

  /**
   * Failed decode with an error.
   *
   * @property throwable The exception that caused the failure.
   */
  public data class Error(
    val throwable: Throwable,
  ) : DecodeResult()
}

/**
 * Creates a platform-specific image decoder.
 */
public expect fun createPlatformDecoder(): ImageDecoder

/**
 * Wrapper for raw image data that will be decoded in the Compose layer.
 * Used by platforms that rely on Skia for image decoding (Apple, Wasm).
 */
public data class RawImageData(
  val data: ByteArray,
  val mimeType: String?,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as RawImageData

    if (!data.contentEquals(other.data)) return false
    if (mimeType != other.mimeType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = data.contentHashCode()
    result = 31 * result + (mimeType?.hashCode() ?: 0)
    return result
  }
}
