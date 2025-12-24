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
import java.io.File

/**
 * CompositionLocal for providing the source file of a loaded image.
 *
 * Image loaders can provide the cached file path when the image is loaded from disk cache.
 * This enables features like sub-sampling in zoomable images that require access to the
 * original image file for efficient region decoding.
 *
 * @see ProvideImageSourceFile
 */
public val LocalImageSourceFile: ProvidableCompositionLocal<File?> =
  staticCompositionLocalOf<File?> { null }

/**
 * Provides an image source [File] to the composition.
 *
 * Image loaders should use this to provide the cached file when:
 * - The image was loaded from disk cache
 * - The image was loaded from a local file
 *
 * This enables consumers like the ZoomablePlugin to use features like sub-sampling
 * that require access to the original image file.
 *
 * @param file The source file of the loaded image, or null if not available.
 * @param content The content that will have access to the file.
 */
@Composable
public fun ProvideImageSourceFile(
  file: File?,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalImageSourceFile provides file) {
    content()
  }
}
