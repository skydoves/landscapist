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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import com.skydoves.landscapist.core.LandscapistConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Creates a platform-specific image decoder for Android.
 */
public actual fun createPlatformDecoder(): ImageDecoder = AndroidImageDecoder()

/**
 * Android implementation of [ImageDecoder] using BitmapFactory.
 *
 * Supports:
 * - Downsampling for memory efficiency
 * - RGB_565 for images without alpha
 * - Hardware bitmaps (API 26+) for faster rendering
 * - Animated images (GIF, APNG, animated WebP) on API 28+
 */
internal class AndroidImageDecoder : ImageDecoder {

  override suspend fun decode(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult = withContext(Dispatchers.Default) {
    try {
      // Check if this is an animated image and we can decode it
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAnimatedFormat(mimeType, data)) {
        val animatedResult = decodeAnimated(data, mimeType, targetWidth, targetHeight, config)
        if (animatedResult != null) {
          return@withContext animatedResult
        }
        // Fall through to static decoding if animated decoding failed
      }

      // Static image decoding
      decodeStatic(data, mimeType, targetWidth, targetHeight, config)
    } catch (e: Exception) {
      DecodeResult.Error(e)
    }
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun decodeAnimated(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult? {
    return try {
      val source = android.graphics.ImageDecoder.createSource(ByteBuffer.wrap(data))
      var isAnimated = false
      var width = 0
      var height = 0

      val drawable = android.graphics.ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
        width = info.size.width
        height = info.size.height

        // Apply target size if specified
        if (targetWidth != null && targetHeight != null && targetWidth > 0 && targetHeight > 0) {
          val scale = minOf(
            targetWidth.toFloat() / width,
            targetHeight.toFloat() / height,
          )
          if (scale < 1f) {
            decoder.setTargetSize((width * scale).toInt(), (height * scale).toInt())
          }
        }

        // Allow partial decoding for better performance
        decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE)
      }

      if (drawable is AnimatedImageDrawable) {
        isAnimated = true
        // Start the animation
        drawable.start()
      }

      if (isAnimated) {
        DecodeResult.Success(
          bitmap = drawable,
          width = drawable.intrinsicWidth,
          height = drawable.intrinsicHeight,
          isAnimated = true,
        )
      } else {
        // Not actually animated, let static decoder handle it
        null
      }
    } catch (_: Exception) {
      // Failed to decode as animated, return null to fall back to static
      null
    }
  }

  private fun decodeStatic(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult {
    // First pass: get dimensions
    val boundsOptions = BitmapFactory.Options().apply {
      inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)

    val originalWidth = boundsOptions.outWidth
    val originalHeight = boundsOptions.outHeight

    if (originalWidth <= 0 || originalHeight <= 0) {
      return DecodeResult.Error(
        IllegalArgumentException("Failed to decode image dimensions"),
      )
    }

    // Calculate sample size for downsampling
    val sampleSize = calculateSampleSize(
      originalWidth = originalWidth,
      originalHeight = originalHeight,
      targetWidth = targetWidth ?: originalWidth,
      targetHeight = targetHeight ?: originalHeight,
      maxSize = config.maxBitmapSize,
    )

    // Determine if we can use hardware bitmaps
    val useHardware = config.bitmapConfig.allowHardware &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      !hasAlpha(mimeType)

    // Second pass: decode with options
    val decodeOptions = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = when {
        useHardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
          Bitmap.Config.HARDWARE
        }
        config.allowRgb565 && !hasAlpha(mimeType) -> {
          Bitmap.Config.RGB_565
        }
        else -> {
          Bitmap.Config.ARGB_8888
        }
      }
    }

    var bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
      ?: return DecodeResult.Error(
        IllegalArgumentException("Failed to decode bitmap"),
      )

    // If hardware bitmap failed, try to copy to hardware
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      bitmap.config != Bitmap.Config.HARDWARE &&
      useHardware
    ) {
      try {
        val hardwareBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
        if (hardwareBitmap != null) {
          bitmap.recycle()
          bitmap = hardwareBitmap
        }
      } catch (_: Exception) {
        // Failed to copy to hardware, keep the original bitmap
      }
    }

    return DecodeResult.Success(
      bitmap = bitmap,
      width = originalWidth,
      height = originalHeight,
      isAnimated = false,
    )
  }

  private fun calculateSampleSize(
    originalWidth: Int,
    originalHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    maxSize: Int,
  ): Int {
    var sampleSize = 1
    val effectiveTargetWidth = min(targetWidth, maxSize)
    val effectiveTargetHeight = min(targetHeight, maxSize)

    while (
      originalWidth / (sampleSize * 2) >= effectiveTargetWidth &&
      originalHeight / (sampleSize * 2) >= effectiveTargetHeight
    ) {
      sampleSize *= 2
    }

    // Also ensure we don't exceed max bitmap size
    while (
      originalWidth / sampleSize > maxSize ||
      originalHeight / sampleSize > maxSize
    ) {
      sampleSize *= 2
    }

    return max(1, sampleSize)
  }

  private fun hasAlpha(mimeType: String?): Boolean {
    return mimeType == "image/png" ||
      mimeType == "image/webp" ||
      mimeType == "image/gif"
  }

  private fun isAnimatedFormat(mimeType: String?, data: ByteArray): Boolean {
    // Check mime type first
    if (mimeType == "image/gif") return true
    if (mimeType == "image/webp") {
      // Check if WebP is animated by looking for ANIM chunk
      return isAnimatedWebP(data)
    }
    if (mimeType == "image/apng" || mimeType == "image/png") {
      // Check for APNG
      return isAnimatedPng(data)
    }

    // Try to detect by file signature
    if (data.size >= 6) {
      // GIF signature
      if (data[0] == 'G'.code.toByte() &&
        data[1] == 'I'.code.toByte() &&
        data[2] == 'F'.code.toByte()
      ) {
        return true
      }
    }

    return false
  }

  private fun isAnimatedWebP(data: ByteArray): Boolean {
    // WebP animated files have "ANIM" chunk
    // RIFF....WEBP....ANIM
    if (data.size < 30) return false

    // Check RIFF header
    if (data[0] != 'R'.code.toByte() ||
      data[1] != 'I'.code.toByte() ||
      data[2] != 'F'.code.toByte() ||
      data[3] != 'F'.code.toByte()
    ) {
      return false
    }

    // Check WEBP signature
    if (data[8] != 'W'.code.toByte() ||
      data[9] != 'E'.code.toByte() ||
      data[10] != 'B'.code.toByte() ||
      data[11] != 'P'.code.toByte()
    ) {
      return false
    }

    // Look for VP8X chunk with animation flag or ANIM chunk
    var offset = 12
    while (offset < data.size - 8) {
      val chunkType = String(data.sliceArray(offset until offset + 4))
      if (chunkType == "VP8X" && offset + 8 < data.size) {
        // Check animation flag (bit 1)
        val flags = data[offset + 8].toInt() and 0xFF
        if (flags and 0x02 != 0) return true
      }
      if (chunkType == "ANIM") return true

      // Get chunk size (little-endian)
      if (offset + 7 >= data.size) break
      val chunkSize = (data[offset + 4].toInt() and 0xFF) or
        ((data[offset + 5].toInt() and 0xFF) shl 8) or
        ((data[offset + 6].toInt() and 0xFF) shl 16) or
        ((data[offset + 7].toInt() and 0xFF) shl 24)

      // Move to next chunk (chunk size + 8 for header, padded to even)
      offset += 8 + chunkSize + (chunkSize and 1)

      // Safety check
      if (chunkSize < 0 || offset < 0) break
    }

    return false
  }

  private fun isAnimatedPng(data: ByteArray): Boolean {
    // APNG has "acTL" chunk
    if (data.size < 8) return false

    // Check PNG signature
    if (data[0] != 0x89.toByte() ||
      data[1] != 'P'.code.toByte() ||
      data[2] != 'N'.code.toByte() ||
      data[3] != 'G'.code.toByte()
    ) {
      return false
    }

    // Search for acTL chunk
    var offset = 8
    while (offset < data.size - 12) {
      // Get chunk length (big-endian)
      val length = ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

      val chunkType = String(data.sliceArray(offset + 4 until offset + 8))
      if (chunkType == "acTL") return true
      if (chunkType == "IDAT") return false // acTL must come before IDAT

      // Move to next chunk (4 length + 4 type + data + 4 crc)
      offset += 12 + length

      // Safety check
      if (length < 0 || offset < 0) break
    }

    return false
  }
}
