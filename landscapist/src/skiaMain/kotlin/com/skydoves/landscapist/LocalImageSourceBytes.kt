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
package com.skydoves.landscapist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal for providing the raw bytes of a loaded image.
 *
 * This is used on Skia platforms (iOS, macOS, Desktop) where file path access
 * may differ from Android. Image loaders can provide the raw image bytes
 * to enable features like sub-sampling in zoomable images.
 *
 * @see ProvideImageSourceBytes
 */
public val LocalImageSourceBytes: ProvidableCompositionLocal<ByteArray?> =
  staticCompositionLocalOf<ByteArray?> { null }

/**
 * Provides the raw image bytes to the composition.
 *
 * Image loaders should use this to provide the cached image bytes when:
 * - The image was loaded from disk cache
 * - The image was loaded from network and cached
 *
 * This enables consumers like the ZoomablePlugin to use features like sub-sampling
 * that require access to the original image data.
 *
 * @param bytes The raw bytes of the loaded image, or null if not available.
 * @param content The content that will have access to the bytes.
 */
@Composable
public fun ProvideImageSourceBytes(
  bytes: ByteArray?,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalImageSourceBytes provides bytes) {
    content()
  }
}
