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
package com.skydoves.landscapist.image

import androidx.compose.runtime.Composable

/**
 * Provides the image source data to the composition for sub-sampling support.
 *
 * On Android, this provides the disk cache file path.
 * On Skia platforms (iOS, macOS, Desktop, Wasm), this provides the raw bytes.
 *
 * @param diskCachePath The disk cache file path (used on Android).
 * @param rawData The raw image bytes (used on Skia platforms).
 * @param content The content that will have access to the source data.
 */
@Composable
public expect fun ProvideImageSource(
  diskCachePath: String?,
  rawData: ByteArray?,
  content: @Composable () -> Unit,
)
