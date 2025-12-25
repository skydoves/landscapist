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
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceSubsampleFactor
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.posix.memcpy
import platform.posix.uint8_tVar

/**
 * Apple (iOS/macOS) implementation of [ImageRegionDecoder] using CGImageSource.
 *
 * This implementation uses Apple's ImageIO framework for efficient image decoding with subsampling.
 * The approach:
 * 1. Use kCGImageSourceSubsampleFactor for native subsampled decoding
 * 2. Use CGImageCreateWithImageInRect to extract the specific region
 * 3. Convert only the extracted region to ImageBitmap
 *
 * This provides memory-efficient tile-based loading without caching full images.
 */
@OptIn(ExperimentalForeignApi::class)
public actual class ImageRegionDecoder private constructor(
  private val imageData: ByteArray,
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
   * Decodes a region of the image with subsampling support.
   *
   * Uses CGImageSource with kCGImageSourceSubsampleFactor for native subsampled decode,
   * then CGImageCreateWithImageInRect for region extraction.
   *
   * @param region The region to decode in image coordinates.
   * @param sampleSize The sample size for decoding (power of 2: 1, 2, 4, 8).
   * @return The decoded [ImageBitmap], or null if decoding failed.
   */
  public actual suspend fun decodeRegion(
    region: IntRect,
    sampleSize: Int,
  ): ImageBitmap? = withContext(Dispatchers.IO) {
    semaphore.withPermit {
      if (isClosed) return@withPermit null

      try {
        decodeRegionInternal(region, sampleSize)
      } catch (e: Exception) {
        null
      }
    }
  }

  @OptIn(BetaInteropApi::class)
  private fun decodeRegionInternal(region: IntRect, sampleSize: Int): ImageBitmap? {
    // Normalize sample size to power of 2 (CGImageSource supports 2, 4, 8)
    val normalizedSampleSize = when {
      sampleSize <= 1 -> 1
      sampleSize <= 2 -> 2
      sampleSize <= 4 -> 4
      else -> 8
    }

    // Create CFData from image bytes
    val cfData = imageData.usePinned { pinned ->
      CFDataCreate(
        kCFAllocatorDefault,
        pinned.addressOf(0).reinterpret(),
        imageData.size.toLong(),
      )
    } ?: return null

    try {
      val imageSource = CGImageSourceCreateWithData(cfData, null) ?: return null

      try {
        // Step 1: Get subsampled image using CGImageSource
        val subsampledImage =
          createSubsampledImage(imageSource, normalizedSampleSize) ?: return null

        try {
          val subsampledWidth = CGImageGetWidth(subsampledImage).toInt()
          val subsampledHeight = CGImageGetHeight(subsampledImage).toInt()

          // Calculate scale factors
          val scaleX = subsampledWidth.toFloat() / _imageSize.width
          val scaleY = subsampledHeight.toFloat() / _imageSize.height

          // Map region to subsampled image coordinates
          val scaledLeft = (region.left * scaleX).toInt().coerceIn(0, subsampledWidth)
          val scaledTop = (region.top * scaleY).toInt().coerceIn(0, subsampledHeight)
          val scaledRight = (region.right * scaleX).toInt().coerceIn(0, subsampledWidth)
          val scaledBottom = (region.bottom * scaleY).toInt().coerceIn(0, subsampledHeight)

          val regionWidth = (scaledRight - scaledLeft).coerceAtLeast(1)
          val regionHeight = (scaledBottom - scaledTop).coerceAtLeast(1)

          // Step 2: Extract region using CGImageCreateWithImageInRect
          val regionRect = CGRectMake(
            scaledLeft.toDouble(),
            scaledTop.toDouble(),
            regionWidth.toDouble(),
            regionHeight.toDouble(),
          )

          val regionImage = CGImageCreateWithImageInRect(subsampledImage, regionRect) ?: return null

          try {
            // Step 3: Convert only the region to ImageBitmap
            return cgImageToImageBitmap(regionImage)
          } finally {
            CGImageRelease(regionImage)
          }
        } finally {
          CGImageRelease(subsampledImage)
        }
      } finally {
        CFRelease(imageSource)
      }
    } finally {
      CFRelease(cfData)
    }
  }

  @OptIn(BetaInteropApi::class)
  private fun createSubsampledImage(
    imageSource: platform.ImageIO.CGImageSourceRef,
    sampleSize: Int,
  ): platform.CoreGraphics.CGImageRef? {
    if (sampleSize <= 1) {
      // No subsampling needed, get full image
      return CGImageSourceCreateImageAtIndex(imageSource, 0u, null)
    }

    // Create options for subsampled thumbnail
    val options = CFDictionaryCreateMutable(
      kCFAllocatorDefault,
      4,
      null,
      null,
    ) ?: return null

    try {
      val trueValue = CFBridgingRetain(NSNumber(bool = true))
      val subsampleValue = CFBridgingRetain(NSNumber(int = sampleSize))

      // Calculate max pixel size for the subsampled image
      val maxSize = maxOf(_imageSize.width, _imageSize.height) / sampleSize
      val maxSizeValue = CFBridgingRetain(NSNumber(int = maxSize))

      CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, trueValue)
      CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, trueValue)
      CFDictionarySetValue(options, kCGImageSourceSubsampleFactor, subsampleValue)
      CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxSizeValue)

      if (trueValue != null) CFRelease(trueValue)
      if (subsampleValue != null) CFRelease(subsampleValue)
      if (maxSizeValue != null) CFRelease(maxSizeValue)

      return CGImageSourceCreateThumbnailAtIndex(imageSource, 0u, options)
    } finally {
      CFRelease(options)
    }
  }

  @OptIn(BetaInteropApi::class)
  private fun cgImageToImageBitmap(cgImage: platform.CoreGraphics.CGImageRef): ImageBitmap? {
    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()

    if (width <= 0 || height <= 0) return null

    // Create bitmap context with BGRA format (matches Skia's expectation)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo =
      kCGBitmapByteOrder32Little or CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value
    val bytesPerRow = width * 4

    val context = CGBitmapContextCreate(
      data = null,
      width = width.toULong(),
      height = height.toULong(),
      bitsPerComponent = 8u,
      bytesPerRow = bytesPerRow.toULong(),
      space = colorSpace,
      bitmapInfo = bitmapInfo,
    )

    CGColorSpaceRelease(colorSpace)

    if (context == null) return null

    try {
      // Draw CGImage into context
      val drawRect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
      CGContextDrawImage(context, drawRect, cgImage)

      // Get pixel data pointer
      val pixelData = CGBitmapContextGetData(context) ?: return null

      // Create Skia bitmap
      val bitmap = Bitmap()
      val imageInfo = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.BGRA_8888,
        alphaType = ColorAlphaType.PREMUL,
      )

      if (!bitmap.allocPixels(imageInfo)) return null

      // Copy pixel data to byte array
      val pixelBytes = pixelData.reinterpret<uint8_tVar>()
      val totalBytes = height * bytesPerRow
      val destArray = ByteArray(totalBytes)

      for (i in 0 until totalBytes) {
        destArray[i] = pixelBytes[i].toByte()
      }

      // Install pixels into bitmap
      val success = bitmap.installPixels(imageInfo, destArray, bytesPerRow)
      if (!success) return null

      return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    } finally {
      CGContextRelease(context)
    }
  }

  /**
   * Closes the decoder and releases resources.
   */
  public actual fun close() {
    isClosed = true
  }

  public actual companion object {
    /**
     * Creates an [ImageRegionDecoder] from a file path.
     *
     * @param path The path to the image file.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    @OptIn(BetaInteropApi::class)
    public actual fun create(path: String): ImageRegionDecoder? {
      return try {
        val nsData = NSData.create(contentsOfFile = path) ?: return null
        val bytes = ByteArray(nsData.length.toInt())
        bytes.usePinned { pinned ->
          memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
        }
        create(bytes)
      } catch (e: Exception) {
        null
      }
    }

    /**
     * Creates an [ImageRegionDecoder] from byte array.
     *
     * Uses CGImageSource to efficiently read image dimensions without full decode.
     *
     * @param data The image data as a byte array.
     * @return An [ImageRegionDecoder], or null if creation failed.
     */
    @OptIn(BetaInteropApi::class)
    public actual fun create(data: ByteArray): ImageRegionDecoder? {
      return try {
        // Use CGImageSource to get dimensions efficiently
        val size = getImageSizeWithCGImageSource(data)
          ?: getImageSizeWithSkia(data)
          ?: return null

        ImageRegionDecoder(
          imageData = data,
          _imageSize = size,
        )
      } catch (e: Exception) {
        null
      }
    }

    @OptIn(BetaInteropApi::class)
    private fun getImageSizeWithCGImageSource(data: ByteArray): IntSize? {
      val cfData = data.usePinned { pinned ->
        CFDataCreate(
          kCFAllocatorDefault,
          pinned.addressOf(0).reinterpret(),
          data.size.toLong(),
        )
      } ?: return null

      try {
        val imageSource = CGImageSourceCreateWithData(cfData, null)
        if (imageSource == null) {
          CFRelease(cfData)
          return null
        }

        try {
          val properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0u, null)
            ?: return null

          try {
            @Suppress("UNCHECKED_CAST")
            val propsDict = CFBridgingRelease(properties) as? Map<Any?, Any?>
            val width = (propsDict?.get(kCGImagePropertyPixelWidth) as? Number)?.toInt()
            val height = (propsDict?.get(kCGImagePropertyPixelHeight) as? Number)?.toInt()

            if (width != null && height != null && width > 0 && height > 0) {
              return IntSize(width, height)
            }
            return null
          } catch (e: Exception) {
            return null
          }
        } finally {
          CFRelease(imageSource)
        }
      } finally {
        CFRelease(cfData)
      }
    }

    private fun getImageSizeWithSkia(data: ByteArray): IntSize? {
      return try {
        val skiaData = Data.makeFromBytes(data)
        val codec = org.jetbrains.skia.Codec.makeFromData(skiaData)
        IntSize(codec.width, codec.height)
      } catch (e: Exception) {
        null
      }
    }
  }
}
