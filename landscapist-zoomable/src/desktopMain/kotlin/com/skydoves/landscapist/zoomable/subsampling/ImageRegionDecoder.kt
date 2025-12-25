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
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

/**
 * Desktop (JVM) implementation of [ImageRegionDecoder] using javax.imageio.
 *
 * This implementation uses [ImageReader] with [ImageReadParam.setSourceRegion] for efficient
 * region-based decoding. JPEG images support true partial decoding, while other formats
 * may require loading the full image.
 */
public actual class ImageRegionDecoder private constructor(
  private val imageInputStream: ImageInputStream,
  private val reader: ImageReader,
  private val _imageSize: IntSize,
) {

  // Allow up to 2 concurrent decode operations to balance performance and memory
  private val semaphore = Semaphore(2)
  private var isClosed = false

  /**
   * The size of the full image.
   */
  public actual val imageSize: IntSize
    get() = _imageSize

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
        val param = reader.defaultReadParam.apply {
          sourceRegion = Rectangle(
            region.left,
            region.top,
            region.right - region.left,
            region.bottom - region.top,
          )
          // Set subsampling (equivalent to Android's inSampleSize)
          setSourceSubsampling(sampleSize, sampleSize, 0, 0)
        }

        val bufferedImage: BufferedImage = synchronized(reader) {
          reader.read(0, param)
        }

        // Convert to Skia Image then to Compose ImageBitmap
        bufferedImage.toComposeImageBitmap()
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
    try {
      reader.dispose()
      imageInputStream.close()
    } catch (_: Exception) {
      // Ignore close errors
    }
  }

  public actual companion object {
    /**
     * Creates an [ImageRegionDecoder] from a file path.
     *
     * @param path The path to the image file.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    public actual fun create(path: String): ImageRegionDecoder? {
      return try {
        val file = File(path)
        if (!file.exists()) return null

        val imageInputStream = ImageIO.createImageInputStream(file) ?: return null
        createFromInputStream(imageInputStream)
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
    public actual fun create(data: ByteArray): ImageRegionDecoder? {
      return try {
        val byteArrayInputStream = ByteArrayInputStream(data)
        val imageInputStream = ImageIO.createImageInputStream(byteArrayInputStream) ?: return null
        createFromInputStream(imageInputStream)
      } catch (e: Exception) {
        null
      }
    }

    private fun createFromInputStream(imageInputStream: ImageInputStream): ImageRegionDecoder? {
      val readers = ImageIO.getImageReaders(imageInputStream)
      if (!readers.hasNext()) {
        imageInputStream.close()
        return null
      }

      val reader = readers.next()
      reader.input = imageInputStream

      val width = reader.getWidth(0)
      val height = reader.getHeight(0)

      return ImageRegionDecoder(
        imageInputStream = imageInputStream,
        reader = reader,
        _imageSize = IntSize(width, height),
      )
    }
  }
}
