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
import android.os.Build
import com.skydoves.landscapist.core.LandscapistConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
      // First pass: get dimensions
      val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
      BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)

      val originalWidth = boundsOptions.outWidth
      val originalHeight = boundsOptions.outHeight

      if (originalWidth <= 0 || originalHeight <= 0) {
        return@withContext DecodeResult.Error(
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
        !hasAlpha(mimeType) // Hardware bitmaps work best with opaque images

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
        ?: return@withContext DecodeResult.Error(
          IllegalArgumentException("Failed to decode bitmap"),
        )

      // If hardware bitmap failed (can happen with certain image types), fall back
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        bitmap.config == Bitmap.Config.HARDWARE &&
        useHardware
      ) {
        // Hardware bitmap succeeded, no action needed
      } else if (useHardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Hardware bitmap was requested but failed, try to copy to hardware
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

      DecodeResult.Success(
        bitmap = bitmap,
        width = bitmap.width,
        height = bitmap.height,
      )
    } catch (e: Exception) {
      DecodeResult.Error(e)
    }
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
}
