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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/**
 * Skia implementation of [ImageRegionDecoder] using Skia's Codec and Image APIs.
 *
 * This class provides thread-safe region decoding for desktop and iOS platforms.
 * Uses a semaphore to allow limited parallelism for better performance.
 */
public actual class ImageRegionDecoder private constructor(
  private val skiaImage: Image,
) {

  // Allow up to 2 concurrent decode operations to balance performance and memory
  private val semaphore = Semaphore(2)
  private var isClosed = false

  /**
   * The size of the full image.
   */
  public actual val imageSize: IntSize
    get() = IntSize(skiaImage.width, skiaImage.height)

  /**
   * Decodes a region of the image.
   *
   * @param region The region to decode in image coordinates.
   * @param sampleSize The sample size for decoding (power of 2).
   * @return The decoded [ImageBitmap], or null if decoding failed.
   */
  public actual suspend fun decodeRegion(
    region: IntRect,
    sampleSize: Int,
  ): ImageBitmap? = withContext(Dispatchers.Default) {
    semaphore.withPermit {
      if (isClosed) return@withPermit null

      try {
        // Calculate the output size based on sample size
        val regionWidth = region.right - region.left
        val regionHeight = region.bottom - region.top
        val outputWidth = regionWidth / sampleSize
        val outputHeight = regionHeight / sampleSize

        if (outputWidth <= 0 || outputHeight <= 0) return@withPermit null

        // Create a bitmap to draw the region into
        val bitmap = Bitmap()
        val imageInfo = ImageInfo.makeN32(outputWidth, outputHeight, ColorAlphaType.PREMUL)
        bitmap.allocPixels(imageInfo)

        // Draw the region of the source image onto the bitmap
        val canvas = Canvas(bitmap)
        val srcRect = Rect.makeLTRB(
          region.left.toFloat(),
          region.top.toFloat(),
          region.right.toFloat(),
          region.bottom.toFloat(),
        )
        val dstRect = Rect.makeWH(outputWidth.toFloat(), outputHeight.toFloat())

        canvas.drawImageRect(
          skiaImage,
          srcRect,
          dstRect,
          SamplingMode.DEFAULT,
          null,
          true,
        )

        // Convert to Compose ImageBitmap
        Image.makeFromBitmap(bitmap).toComposeImageBitmap()
      } catch (e: Exception) {
        null
      }
    }
  }

  /**
   * Closes the decoder and releases resources.
   */
  public actual fun close() {
    isClosed = true
    skiaImage.close()
  }

  public actual companion object {
    /**
     * Creates an [ImageRegionDecoder] from a file path.
     *
     * Note: File path-based creation is not fully supported on Skia platforms.
     * Use [create] with byte array for cross-platform compatibility, or provide
     * the decoder explicitly via [LocalImageRegionDecoder].
     *
     * @param path The path to the image file.
     * @return An [ImageRegionDecoder], or null if creation failed or not supported.
     */
    public actual fun create(path: String): ImageRegionDecoder? {
      // File path creation is not supported on all Skia platforms (iOS, WASM)
      // Users should use create(ByteArray) or provide decoder via LocalImageRegionDecoder
      return null
    }

    /**
     * Creates an [ImageRegionDecoder] from byte array.
     *
     * @param data The image data as a byte array.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    public actual fun create(data: ByteArray): ImageRegionDecoder? {
      return try {
        val skiaData = Data.makeFromBytes(data)
        val codec = Codec.makeFromData(skiaData) ?: return null
        val bitmap = Bitmap()
        bitmap.allocPixels(codec.imageInfo)
        codec.readPixels(bitmap, 0)
        val image = Image.makeFromBitmap(bitmap)
        ImageRegionDecoder(image)
      } catch (e: Exception) {
        null
      }
    }
  }
}
