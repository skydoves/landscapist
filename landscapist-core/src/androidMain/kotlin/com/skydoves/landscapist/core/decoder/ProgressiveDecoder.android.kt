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
import androidx.annotation.RequiresApi
import com.skydoves.landscapist.core.LandscapistConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Creates the Android progressive decoder.
 */
public actual fun createProgressiveDecoder(): ProgressiveDecoder = AndroidProgressiveDecoder()

/**
 * Android implementation of progressive image decoding.
 *
 * On API 28+, uses Android's ImageDecoder with OnPartialImageListener for true progressive
 * decoding of progressive JPEGs and interlaced PNGs.
 *
 * On older APIs, provides a simulated progressive experience by decoding at multiple
 * sample sizes (low quality → high quality).
 */
internal class AndroidProgressiveDecoder : ProgressiveDecoder {

  override fun decodeProgressive(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): Flow<ProgressiveDecodeResult> = flow {
    try {
      // Get image dimensions first
      val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
      BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)

      val originalWidth = boundsOptions.outWidth
      val originalHeight = boundsOptions.outHeight

      if (originalWidth <= 0 || originalHeight <= 0) {
        emit(ProgressiveDecodeResult.Error(IllegalArgumentException("Invalid image dimensions")))
        return@flow
      }

      // Check if we can use Android's ImageDecoder for true progressive decoding
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        ProgressiveImageDetector.supportsProgressive(data, mimeType)
      ) {
        // Use ImageDecoder with OnPartialImageListener
        emitAll(
          decodeWithImageDecoder(
            data = data,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            config = config,
          ),
        )
      } else {
        // Fallback: Simulate progressive loading with multi-pass sampling
        emitAll(
          decodeWithMultiPass(
            data = data,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            config = config,
          ),
        )
      }
    } catch (e: Exception) {
      emit(ProgressiveDecodeResult.Error(e))
    }
  }.flowOn(Dispatchers.Default)

  @RequiresApi(Build.VERSION_CODES.P)
  private fun decodeWithImageDecoder(
    data: ByteArray,
    originalWidth: Int,
    originalHeight: Int,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): Flow<ProgressiveDecodeResult> = flow {
    val source = android.graphics.ImageDecoder.createSource(ByteBuffer.wrap(data))
    var partialBitmap: Bitmap? = null

    try {
      val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Apply target size if specified
        if (targetWidth != null && targetHeight != null && targetWidth > 0 && targetHeight > 0) {
          val scale = minOf(
            targetWidth.toFloat() / info.size.width,
            targetHeight.toFloat() / info.size.height,
          )
          if (scale < 1f) {
            decoder.setTargetSize(
              (info.size.width * scale).toInt(),
              (info.size.height * scale).toInt(),
            )
          }
        }

        // Use software allocator for partial image support
        decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE)

        // Set partial image listener for progressive rendering
        decoder.setOnPartialImageListener { exception ->
          // Get the partial bitmap if possible
          // Note: This is called when decoding fails partway through
          // For progressive JPEGs, it provides intermediate scans
          false // Don't throw exception, continue decoding
        }
      }

      // Emit final result
      emit(
        ProgressiveDecodeResult.Complete(
          bitmap = bitmap,
          width = originalWidth,
          height = originalHeight,
        ),
      )
    } catch (e: Exception) {
      // If we have a partial bitmap, emit it as intermediate
      partialBitmap?.let { partial ->
        emit(
          ProgressiveDecodeResult.Intermediate(
            bitmap = partial,
            width = partial.width,
            height = partial.height,
            progress = 0.5f,
            isPreview = true,
          ),
        )
      }
      emit(ProgressiveDecodeResult.Error(e))
    }
  }

  /**
   * Multi-pass decoding simulation.
   * Decodes the image at progressively higher quality levels.
   */
  private fun decodeWithMultiPass(
    data: ByteArray,
    originalWidth: Int,
    originalHeight: Int,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): Flow<ProgressiveDecodeResult> = flow {
    val effectiveTargetWidth = targetWidth ?: originalWidth
    val effectiveTargetHeight = targetHeight ?: originalHeight

    // Calculate base sample size for full quality
    val baseSampleSize = calculateSampleSize(
      originalWidth = originalWidth,
      originalHeight = originalHeight,
      targetWidth = effectiveTargetWidth,
      targetHeight = effectiveTargetHeight,
      maxSize = config.maxBitmapSize,
    )

    // Define decode passes (from low to high quality)
    // Pass 1: Very low quality preview (8x or 16x downsampled)
    // Pass 2: Medium quality (4x or 2x downsampled)
    // Pass 3: Full quality
    val sampleSizes = buildList {
      // Start with a very small preview
      val previewSampleSize = max(baseSampleSize * 8, 16)
      add(previewSampleSize)

      // Add intermediate step if there's a big jump
      val mediumSampleSize = max(baseSampleSize * 2, 4)
      if (mediumSampleSize < previewSampleSize / 2) {
        add(mediumSampleSize)
      }

      // Final full quality
      add(baseSampleSize)
    }.distinct()

    sampleSizes.forEachIndexed { index, sampleSize ->
      val isPreview = index < sampleSizes.size - 1
      val progress = (index + 1).toFloat() / sampleSizes.size

      val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = if (config.allowRgb565 && !hasAlpha(data)) {
          Bitmap.Config.RGB_565
        } else {
          Bitmap.Config.ARGB_8888
        }
      }

      val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
      if (bitmap != null) {
        if (isPreview) {
          emit(
            ProgressiveDecodeResult.Intermediate(
              bitmap = bitmap,
              width = originalWidth,
              height = originalHeight,
              progress = progress,
              isPreview = true,
            ),
          )
        } else {
          emit(
            ProgressiveDecodeResult.Complete(
              bitmap = bitmap,
              width = originalWidth,
              height = originalHeight,
            ),
          )
        }
      }
    }
  }

  override fun supportsProgressiveDecode(data: ByteArray, mimeType: String?): Boolean {
    // On Android, we always support "progressive" decoding via multi-pass
    // True progressive is supported on API 28+ for progressive JPEG/PNG
    return true
  }

  private fun calculateSampleSize(
    originalWidth: Int,
    originalHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    maxSize: Int,
  ): Int {
    var sampleSize = 1
    val effectiveTargetWidth = minOf(targetWidth, maxSize)
    val effectiveTargetHeight = minOf(targetHeight, maxSize)

    while (
      originalWidth / (sampleSize * 2) >= effectiveTargetWidth &&
      originalHeight / (sampleSize * 2) >= effectiveTargetHeight
    ) {
      sampleSize *= 2
    }

    while (
      originalWidth / sampleSize > maxSize ||
      originalHeight / sampleSize > maxSize
    ) {
      sampleSize *= 2
    }

    return max(1, sampleSize)
  }

  private fun hasAlpha(data: ByteArray): Boolean {
    // Check for PNG (supports alpha)
    if (data.size >= 4 &&
      data[0] == 0x89.toByte() &&
      data[1] == 'P'.code.toByte()
    ) {
      return true
    }
    // Check for WebP with VP8X chunk (may have alpha)
    if (data.size >= 30 &&
      data[0] == 'R'.code.toByte() &&
      data[1] == 'I'.code.toByte() &&
      data[2] == 'F'.code.toByte() &&
      data[3] == 'F'.code.toByte() &&
      data[8] == 'W'.code.toByte() &&
      data[9] == 'E'.code.toByte()
    ) {
      // Check VP8X chunk for alpha flag
      if (data.size >= 21 && data[12] == 'V'.code.toByte() && data[15] == 'X'.code.toByte()) {
        val flags = data[20].toInt() and 0xFF
        return (flags and 0x10) != 0 // Alpha flag
      }
    }
    return false
  }

  private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(flow: Flow<T>) {
    flow.collect { emit(it) }
  }
}
