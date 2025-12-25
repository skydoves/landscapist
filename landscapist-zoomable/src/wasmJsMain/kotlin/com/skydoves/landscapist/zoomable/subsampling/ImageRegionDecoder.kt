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
 * WASM/JS implementation of [ImageRegionDecoder].
 *
 * Web browsers do not support efficient region-based image decoding.
 * This implementation disables sub-sampling functionality on WASM targets.
 * The zoomable feature will still work, but without tile-based image loading.
 *
 * For web applications with large images, consider:
 * - Using pre-generated image pyramids (tile servers)
 * - Serving appropriately sized images based on viewport
 * - Using WebGL-based solutions for large image rendering
 */
public actual class ImageRegionDecoder private constructor(
  private val _imageSize: IntSize,
) {

  /**
   * The size of the full image.
   */
  public actual val imageSize: IntSize
    get() = _imageSize

  /**
   * Decodes a region of the image.
   *
   * Note: Region decoding is not supported on WASM. This always returns null.
   *
   * @param region The region to decode in image coordinates.
   * @param sampleSize The sample size for decoding (power of 2).
   * @return Always null on WASM platform.
   */
  public actual suspend fun decodeRegion(
    region: IntRect,
    sampleSize: Int,
  ): ImageBitmap? {
    // Region decoding is not supported on WASM
    return null
  }

  /**
   * Closes the decoder and releases resources.
   */
  public actual fun close() {
    // No resources to release on WASM
  }

  public actual companion object {
    /**
     * Creates an [ImageRegionDecoder] from a file path.
     *
     * Note: File path creation is not supported on WASM.
     *
     * @param path The path to the image file.
     * @return Always null on WASM platform.
     */
    public actual fun create(path: String): ImageRegionDecoder? {
      // Not supported on WASM
      return null
    }

    /**
     * Creates an [ImageRegionDecoder] from byte array.
     *
     * Note: Sub-sampling is not supported on WASM.
     * This returns null to indicate that the feature is unavailable.
     *
     * @param data The image data as a byte array.
     * @return Always null on WASM platform.
     */
    public actual fun create(data: ByteArray): ImageRegionDecoder? {
      // Sub-sampling is not supported on WASM
      // Return null to fall back to regular image loading
      return null
    }
  }
}
