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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream

/**
 * A wrapper around [BitmapRegionDecoder] for decoding regions of large images.
 *
 * This class provides thread-safe region decoding with configurable options.
 * Uses a semaphore to allow limited parallelism for better performance.
 *
 * @property decoder The underlying [BitmapRegionDecoder].
 * @property dispatcher The dispatcher to use for decoding operations.
 */
public class ImageRegionDecoder private constructor(
  private val decoder: BitmapRegionDecoder,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {

  // Allow up to 2 concurrent decode operations to balance performance and memory
  // BitmapRegionDecoder is thread-safe for decodeRegion calls
  private val semaphore = Semaphore(2)
  private var isClosed = false

  /**
   * The size of the full image.
   */
  public val imageSize: IntSize
    get() = IntSize(decoder.width, decoder.height)

  /**
   * Decodes a region of the image.
   *
   * @param region The region to decode in image coordinates.
   * @param sampleSize The sample size for decoding (power of 2).
   * @param config The bitmap config to use (RGB_565 for better performance).
   * @return The decoded [ImageBitmap], or null if decoding failed.
   */
  public suspend fun decodeRegion(
    region: IntRect,
    sampleSize: Int = 1,
    config: Bitmap.Config = Bitmap.Config.RGB_565,
  ): ImageBitmap? = withContext(dispatcher) {
    semaphore.withPermit {
      if (isClosed) return@withPermit null

      try {
        val options = BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = config
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
  override fun close() {
    isClosed = true
    decoder.recycle()
  }

  public companion object {
    /**
     * Creates an [ImageRegionDecoder] from an [InputStream].
     *
     * @param inputStream The input stream containing the image data.
     * @param dispatcher The dispatcher to use for decoding operations.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    @Suppress("DEPRECATION")
    public fun create(
      inputStream: InputStream,
      dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): ImageRegionDecoder? {
      return try {
        val decoder = BitmapRegionDecoder.newInstance(inputStream, false)
        decoder?.let { ImageRegionDecoder(it, dispatcher) }
      } catch (e: Exception) {
        null
      }
    }

    /**
     * Creates an [ImageRegionDecoder] from a file path.
     *
     * @param path The path to the image file.
     * @param dispatcher The dispatcher to use for decoding operations.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    @Suppress("DEPRECATION")
    public fun create(
      path: String,
      dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): ImageRegionDecoder? {
      return try {
        val decoder = BitmapRegionDecoder.newInstance(path, false)
        decoder?.let { ImageRegionDecoder(it, dispatcher) }
      } catch (e: Exception) {
        null
      }
    }
  }
}
