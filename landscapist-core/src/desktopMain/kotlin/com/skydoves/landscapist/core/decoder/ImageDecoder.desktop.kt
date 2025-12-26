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
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Creates a platform-specific image decoder for Desktop (JVM).
 */
public actual fun createPlatformDecoder(): ImageDecoder = DesktopImageDecoder()

/**
 * Desktop implementation of [ImageDecoder] using Java ImageIO.
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
      val inputStream = ByteArrayInputStream(data)
      val image = ImageIO.read(inputStream)
        ?: return@withContext DecodeResult.Error(
          IllegalArgumentException("Failed to decode image"),
        )

      val originalWidth = image.width
      val originalHeight = image.height

      // Calculate target dimensions
      val (finalWidth, finalHeight) = calculateTargetSize(
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        maxSize = config.maxBitmapSize,
      )

      // Scale if necessary
      val finalImage = if (finalWidth != originalWidth || finalHeight != originalHeight) {
        scaleImage(image, finalWidth, finalHeight)
      } else {
        image
      }

      DecodeResult.Success(
        bitmap = finalImage,
        width = finalImage.width,
        height = finalImage.height,
      )
    } catch (e: Exception) {
      DecodeResult.Error(e)
    }
  }

  private fun calculateTargetSize(
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

    return (originalWidth * ratio).toInt() to (originalHeight * ratio).toInt()
  }

  private fun scaleImage(
    image: BufferedImage,
    targetWidth: Int,
    targetHeight: Int,
  ): BufferedImage {
    val scaledImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaledImage.createGraphics()
    graphics.drawImage(
      image.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH),
      0,
      0,
      null,
    )
    graphics.dispose()
    return scaledImage
  }
}
