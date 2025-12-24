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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Android implementation of [ImageRegionDecoder] using [BitmapRegionDecoder].
 *
 * This class provides thread-safe region decoding with configurable options.
 * Uses a semaphore to allow limited parallelism for better performance.
 */
public actual class ImageRegionDecoder private constructor(
  private val decoder: BitmapRegionDecoder,
) {

  // Allow up to 2 concurrent decode operations to balance performance and memory
  private val semaphore = Semaphore(2)
  private var isClosed = false

  /**
   * The size of the full image.
   */
  public actual val imageSize: IntSize
    get() = IntSize(decoder.width, decoder.height)

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
  ): ImageBitmap? = withContext(Dispatchers.IO) {
    semaphore.withPermit {
      if (isClosed) return@withPermit null

      try {
        val options = BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.RGB_565
        }

        val rect = Rect(
          region.left,
          region.top,
          region.right,
          region.bottom,
        )

        decoder.decodeRegion(rect, options)?.asImageBitmap()
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
    decoder.recycle()
  }

  public actual companion object {
    /**
     * Creates an [ImageRegionDecoder] from a file path.
     *
     * @param path The path to the image file.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    @Suppress("DEPRECATION")
    public actual fun create(path: String): ImageRegionDecoder? {
      return try {
        val decoder = BitmapRegionDecoder.newInstance(path, false)
        decoder?.let { ImageRegionDecoder(it) }
      } catch (e: Exception) {
        null
      }
    }

    /**
     * Creates an [ImageRegionDecoder] from byte array.
     *
     * @param data The image data as a byte array.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    @Suppress("DEPRECATION")
    public actual fun create(data: ByteArray): ImageRegionDecoder? {
      return try {
        val decoder = BitmapRegionDecoder.newInstance(data, 0, data.size, false)
        decoder?.let { ImageRegionDecoder(it) }
      } catch (e: Exception) {
        null
      }
    }

    /**
     * Creates an [ImageRegionDecoder] from an [InputStream].
     *
     * @param inputStream The input stream containing the image data.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    @Suppress("DEPRECATION")
    public fun create(inputStream: InputStream): ImageRegionDecoder? {
      return try {
        val decoder = BitmapRegionDecoder.newInstance(inputStream, false)
        decoder?.let { ImageRegionDecoder(it) }
      } catch (e: Exception) {
        null
      }
    }
  }
}
