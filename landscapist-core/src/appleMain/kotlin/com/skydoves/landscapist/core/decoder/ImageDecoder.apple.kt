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

/**
 * Creates a platform-specific image decoder for Apple platforms.
 */
public actual fun createPlatformDecoder(): ImageDecoder = AppleImageDecoder()

/**
 * Apple implementation of [ImageDecoder].
 *
 * Note: This returns raw bytes that will be decoded by Compose/Skia in the UI layer.
 * CoreGraphics decoding could be implemented here for more control, but Skia
 * provides consistent cross-platform behavior.
 */
internal class AppleImageDecoder : ImageDecoder {

  override suspend fun decode(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult {
    // Return raw bytes for Compose/Skia layer to decode
    // This ensures consistent behavior across all Skia platforms
    return DecodeResult.Success(
      bitmap = RawImageData(data, mimeType),
      width = 0, // Unknown until decoded in Compose layer
      height = 0,
    )
  }
}
