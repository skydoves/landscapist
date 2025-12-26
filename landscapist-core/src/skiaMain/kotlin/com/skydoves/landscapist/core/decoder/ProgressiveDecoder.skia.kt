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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Creates the Skia-based progressive decoder for non-Android platforms.
 */
public actual fun createProgressiveDecoder(): ProgressiveDecoder = SkiaProgressiveDecoder()

/**
 * Skia implementation of progressive image decoding.
 *
 * For non-Android platforms, progressive decoding passes through the raw image data
 * to be decoded by the Compose layer. The actual progressive effect is simulated
 * by showing a blurred preview while the full image loads.
 */
internal class SkiaProgressiveDecoder : ProgressiveDecoder {

  override fun decodeProgressive(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): Flow<ProgressiveDecodeResult> = flow {
    try {
      // For Skia platforms, we wrap the raw data and let the Compose layer handle decoding
      // The progressive effect is achieved through blur animation in the UI layer
      val rawImageData = RawImageData(data = data, mimeType = mimeType)

      // Emit the raw data as "complete" - the UI layer will handle progressive rendering
      emit(
        ProgressiveDecodeResult.Complete(
          bitmap = rawImageData,
          width = 0, // Will be determined during Compose decoding
          height = 0,
        ),
      )
    } catch (e: Exception) {
      emit(ProgressiveDecodeResult.Error(e))
    }
  }.flowOn(Dispatchers.Default)

  override fun supportsProgressiveDecode(data: ByteArray, mimeType: String?): Boolean {
    // Progressive effect is simulated in UI layer for Skia platforms
    return true
  }
}
