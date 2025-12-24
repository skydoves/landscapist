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
package com.skydoves.landscapist.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.skydoves.landscapist.zoomable.subsampling.ImageRegionDecoder

/**
 * CompositionLocal for providing [ImageRegionDecoder] to enable sub-sampling in [ZoomablePlugin].
 *
 * When sub-sampling is enabled in [ZoomableConfig], the [ZoomablePlugin] will use the decoder
 * provided via this CompositionLocal to efficiently load and display large images by loading
 * only the visible tiles at the appropriate resolution.
 *
 * Image loaders (Glide, Coil, Fresco) should provide the decoder by wrapping their success
 * content with [ProvideImageRegionDecoder].
 *
 * @see ZoomableConfig.enableSubSampling
 * @see ZoomablePlugin
 */
public val LocalImageRegionDecoder: ProvidableCompositionLocal<ImageRegionDecoder?> =
  staticCompositionLocalOf<ImageRegionDecoder?> { null }

/**
 * Provides an [ImageRegionDecoder] to the composition for sub-sampling support.
 *
 * This should be used by image loaders to provide the decoder when the image is loaded
 * from a source that supports region decoding (e.g., disk cache, local files).
 *
 * @param decoder The [ImageRegionDecoder] to provide, or null if not available.
 * @param content The content that will have access to the decoder.
 */
@Composable
public fun ProvideImageRegionDecoder(
  decoder: ImageRegionDecoder?,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalImageRegionDecoder provides decoder) {
    content()
  }
}
