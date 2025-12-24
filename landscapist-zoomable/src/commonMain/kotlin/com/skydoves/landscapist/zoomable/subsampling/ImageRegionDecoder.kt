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
package com.skydoves.landscapist.zoomable.subsampling

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * A decoder for loading regions of large images.
 *
 * This interface provides thread-safe region decoding with configurable options.
 * Platform-specific implementations handle the actual decoding.
 */
public expect class ImageRegionDecoder {
  /**
   * The size of the full image.
   */
  public val imageSize: IntSize

  /**
   * Decodes a region of the image.
   *
   * @param region The region to decode in image coordinates.
   * @param sampleSize The sample size for decoding (power of 2).
   * @return The decoded [ImageBitmap], or null if decoding failed.
   */
  public suspend fun decodeRegion(
    region: IntRect,
    sampleSize: Int,
  ): ImageBitmap?

  /**
   * Closes the decoder and releases resources.
   */
  public fun close()

  public companion object {
    /**
     * Creates an [ImageRegionDecoder] from a file path.
     *
     * @param path The path to the image file.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    public fun create(path: String): ImageRegionDecoder?

    /**
     * Creates an [ImageRegionDecoder] from byte array.
     *
     * @param data The image data as a byte array.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    public fun create(data: ByteArray): ImageRegionDecoder?
  }
}
