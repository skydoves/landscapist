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
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import com.skydoves.landscapist.core.bitmappool.BitmapFormat
import com.skydoves.landscapist.core.bitmappool.GlobalBitmapPool

/**
 * Creates an Android region decoder using [BitmapRegionDecoder].
 *
 * @param data The image data as a byte array.
 * @return An [AndroidRegionDecoder] if successful, null if the format is not supported.
 */
public actual fun createRegionDecoder(data: ByteArray): RegionDecoder? {
  return try {
    val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      BitmapRegionDecoder.newInstance(data, 0, data.size)
    } else {
      @Suppress("DEPRECATION")
      BitmapRegionDecoder.newInstance(data, 0, data.size, false)
    }

    if (decoder != null) {
      AndroidRegionDecoder(decoder)
    } else {
      null
    }
  } catch (e: Exception) {
    null
  }
}

/**
 * Android implementation of [RegionDecoder] using [BitmapRegionDecoder].
 *
 * This class provides efficient region-based decoding of large images,
 * leveraging Android's native BitmapRegionDecoder which:
 * - Only loads the requested region into memory
 * - Supports JPEG, PNG, and WebP formats
 * - Uses native code for optimal performance
 *
 * Thread-safety: The underlying BitmapRegionDecoder is synchronized,
 * so this class is thread-safe for concurrent region decodes.
 *
 * @property decoder The underlying Android BitmapRegionDecoder.
 */
internal class AndroidRegionDecoder(
  private val decoder: BitmapRegionDecoder,
) : RegionDecoder {

  override val imageWidth: Int get() = decoder.width

  override val imageHeight: Int get() = decoder.height

  override val isRecycled: Boolean get() = decoder.isRecycled

  override fun decodeRegion(region: ImageRegion, sampleSize: Int): RegionDecodeResult {
    if (isRecycled) {
      return RegionDecodeResult.Error(
        throwable = IllegalStateException("RegionDecoder has been recycled"),
        region = region,
      )
    }

    // Validate region bounds
    val clampedRegion = clampRegion(region)
    if (!clampedRegion.isValid) {
      return RegionDecodeResult.Error(
        throwable = IllegalArgumentException("Invalid region: $region"),
        region = region,
      )
    }

    val rect = Rect(
      clampedRegion.left,
      clampedRegion.top,
      clampedRegion.right,
      clampedRegion.bottom,
    )

    val options = BitmapFactory.Options().apply {
      inSampleSize = sampleSize.coerceAtLeast(1)
      inPreferredConfig = Bitmap.Config.ARGB_8888

      // Try to reuse a bitmap from the pool
      val reusable = GlobalBitmapPool.get().getReusable(
        width = clampedRegion.width / inSampleSize,
        height = clampedRegion.height / inSampleSize,
        format = BitmapFormat.ARGB_8888,
      )
      if (reusable is Bitmap && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        inMutable = true
        inBitmap = reusable
      }
    }

    return try {
      val bitmap = decoder.decodeRegion(rect, options)
      if (bitmap != null) {
        RegionDecodeResult.Success(
          bitmap = bitmap,
          region = clampedRegion,
          sampleSize = sampleSize,
        )
      } else {
        RegionDecodeResult.Error(
          throwable = IllegalStateException("Failed to decode region"),
          region = region,
        )
      }
    } catch (e: IllegalArgumentException) {
      // inBitmap was not suitable, try without reuse
      options.inBitmap = null
      try {
        val bitmap = decoder.decodeRegion(rect, options)
        if (bitmap != null) {
          RegionDecodeResult.Success(
            bitmap = bitmap,
            region = clampedRegion,
            sampleSize = sampleSize,
          )
        } else {
          RegionDecodeResult.Error(
            throwable = IllegalStateException("Failed to decode region"),
            region = region,
          )
        }
      } catch (e2: Exception) {
        RegionDecodeResult.Error(throwable = e2, region = region)
      }
    } catch (e: Exception) {
      RegionDecodeResult.Error(throwable = e, region = region)
    }
  }

  override fun recycle() {
    decoder.recycle()
  }

  /**
   * Clamps the region to valid image bounds.
   */
  private fun clampRegion(region: ImageRegion): ImageRegion {
    return ImageRegion(
      left = region.left.coerceIn(0, imageWidth),
      top = region.top.coerceIn(0, imageHeight),
      right = region.right.coerceIn(0, imageWidth),
      bottom = region.bottom.coerceIn(0, imageHeight),
    )
  }
}

/**
 * Extension function to decode a region with automatic sample size calculation.
 *
 * @param region The region to decode.
 * @param targetWidth Target width for the decoded bitmap.
 * @param targetHeight Target height for the decoded bitmap.
 * @return The decode result.
 */
public fun RegionDecoder.decodeRegionWithTargetSize(
  region: ImageRegion,
  targetWidth: Int,
  targetHeight: Int,
): RegionDecodeResult {
  val sampleSize = calculateSampleSizeForRegion(
    regionWidth = region.width,
    regionHeight = region.height,
    targetWidth = targetWidth,
    targetHeight = targetHeight,
  )
  return decodeRegion(region, sampleSize)
}

/**
 * Calculates the optimal sample size for a region decode.
 *
 * @param regionWidth Width of the region to decode.
 * @param regionHeight Height of the region to decode.
 * @param targetWidth Desired output width.
 * @param targetHeight Desired output height.
 * @return A power-of-2 sample size.
 */
public fun calculateSampleSizeForRegion(
  regionWidth: Int,
  regionHeight: Int,
  targetWidth: Int,
  targetHeight: Int,
): Int {
  var sampleSize = 1

  // Calculate sample size to fit within target dimensions
  while (regionWidth / (sampleSize * 2) >= targetWidth &&
    regionHeight / (sampleSize * 2) >= targetHeight
  ) {
    sampleSize *= 2
  }

  return sampleSize
}
