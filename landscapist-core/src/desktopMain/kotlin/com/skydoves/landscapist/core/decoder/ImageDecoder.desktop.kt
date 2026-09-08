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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Creates a platform-specific image decoder for Desktop (JVM).
 */
public actual fun createPlatformDecoder(): ImageDecoder = DesktopImageDecoder()

/**
 * Desktop implementation of [ImageDecoder].
 *
 * Skia does the reading whenever skiko is on the classpath, which it is for every Compose
 * Multiplatform application, because ImageIO's JPEG reader cannot scale while it decodes. ImageIO
 * remains the fallback, and produces the same [BufferedImage] the rest of the desktop code expects.
 */
internal class DesktopImageDecoder : ImageDecoder {

  override suspend fun decode(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult = withContext(Dispatchers.IO) {
    try {
      val throughSkia = if (skiaAvailable) {
        SkiaJvmDecoder.decode(data, targetWidth, targetHeight, config.maxBitmapSize)
      } else {
        null
      }
      throughSkia ?: decodeSubsampled(data, targetWidth, targetHeight, config)
    } catch (e: Exception) {
      DecodeResult.Error(e)
    }
  }

  /**
   * Reads the header, decodes at the nearest power of two above the requested size, then scales the
   * remainder.
   *
   * Decoding the whole image and shrinking it afterwards means a 4000x3000 photo materialises 48 MB
   * of pixels to produce a thumbnail. ImageIO can skip pixels while it reads, so the full size
   * raster never exists. It cannot skip the work of decoding them, which is why Skia is preferred
   * when it is there.
   */
  private fun decodeSubsampled(
    data: ByteArray,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult {
    ImageIO.createImageInputStream(ByteArrayInputStream(data)).use { stream ->
      val readers = ImageIO.getImageReaders(stream)
      if (!readers.hasNext()) {
        return DecodeResult.Error(IllegalArgumentException("Failed to decode image"))
      }
      val reader = readers.next()
      try {
        reader.setInput(stream, true, true)
        val originalWidth = reader.getWidth(0)
        val originalHeight = reader.getHeight(0)

        val (finalWidth, finalHeight) = fitInside(
          originalWidth = originalWidth,
          originalHeight = originalHeight,
          targetWidth = targetWidth,
          targetHeight = targetHeight,
          maxSize = config.maxBitmapSize,
        )

        val param = reader.defaultReadParam
        val sampleSize = sampleSizeFor(originalWidth, originalHeight, finalWidth, finalHeight)
        if (sampleSize > 1) {
          param.setSourceSubsampling(sampleSize, sampleSize, 0, 0)
        }

        val decoded = reader.read(0, param)
        val image = if (decoded.width != finalWidth || decoded.height != finalHeight) {
          scaleImage(decoded, finalWidth, finalHeight)
        } else {
          decoded
        }

        return DecodeResult.Success(
          bitmap = image,
          width = image.width,
          height = image.height,
        )
      } finally {
        reader.dispose()
      }
    }
  }

  /**
   * The largest power of two the reader can skip by while still producing at least [finalWidth] by
   * [finalHeight] pixels, so the remaining scale is never an upscale.
   */
  private fun sampleSizeFor(
    originalWidth: Int,
    originalHeight: Int,
    finalWidth: Int,
    finalHeight: Int,
  ): Int {
    if (finalWidth <= 0 || finalHeight <= 0) return 1
    var sampleSize = 1
    while (
      originalWidth / (sampleSize * 2) >= finalWidth &&
      originalHeight / (sampleSize * 2) >= finalHeight
    ) {
      sampleSize *= 2
    }
    return sampleSize
  }

  /**
   * Bilinear rather than [java.awt.Image.SCALE_SMOOTH], which runs an area averaging pipeline that
   * is roughly an order of magnitude slower. Subsampling has already brought the image to within a
   * factor of two, so a single bilinear pass loses nothing visible.
   */
  private fun scaleImage(
    image: BufferedImage,
    targetWidth: Int,
    targetHeight: Int,
  ): BufferedImage {
    val scaledImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaledImage.createGraphics()
    graphics.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION,
      RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    )
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null)
    graphics.dispose()
    return scaledImage
  }
}

/**
 * Whether the Skia reader can be used at all, decided once for the process.
 *
 * The probe has to live out here rather than inside [SkiaJvmDecoder]. Without skiko on the
 * classpath, merely naming that object throws `NoClassDefFoundError` while it is being loaded,
 * before any check inside it could run, and that is an `Error` rather than an `Exception`, so it
 * would sail straight past the decoder's own catch and fail every desktop decode. `runCatching`
 * takes any `Throwable`, and the first mention of the object is inside it.
 *
 * Internal rather than private so a test can load this file's class with skiko kept off the
 * classpath and check that the answer is `false` instead of a thrown `Error`.
 */
internal val skiaAvailable: Boolean by lazy {
  runCatching { SkiaJvmDecoder.isUsable() }.getOrDefault(false)
}

/**
 * The size an image of [originalWidth] by [originalHeight] takes when it is fitted inside the
 * requested box, keeping its shape and never growing.
 *
 * Shared by both desktop decode paths so that the Skia reader and the ImageIO fallback agree on the
 * size for the same request, whichever one runs.
 */
internal fun fitInside(
  originalWidth: Int,
  originalHeight: Int,
  targetWidth: Int?,
  targetHeight: Int?,
  maxSize: Int,
): Pair<Int, Int> {
  val maxW = minOf(targetWidth ?: originalWidth, maxSize)
  val maxH = minOf(targetHeight ?: originalHeight, maxSize)

  if (originalWidth <= maxW && originalHeight <= maxH) {
    return originalWidth to originalHeight
  }

  val widthRatio = maxW.toFloat() / originalWidth
  val heightRatio = maxH.toFloat() / originalHeight
  val ratio = minOf(widthRatio, heightRatio)

  return (originalWidth * ratio).toInt().coerceAtLeast(1) to
    (originalHeight * ratio).toInt().coerceAtLeast(1)
}
