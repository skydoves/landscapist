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

import androidx.compose.runtime.ProvidedValue

/**
 * The image source a platform can offer for sub-sampling, ready to be provided.
 *
 * The value [ProvideImageSource] provides, without a composable to do it, so the caller can hand
 * it to one `CompositionLocalProvider` instead of nesting two more groups per image.
 *
 * On Android the disk cache file, on the Skia targets the raw bytes.
 */
internal expect fun imageSourceProvidedValue(
  diskCachePath: String?,
  rawData: ByteArray?,
): ProvidedValue<*>
