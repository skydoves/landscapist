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
package com.skydoves.landscapist.zoomable.internal

import androidx.compose.runtime.Composable
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState

/**
 * Platform-specific composable content for [ZoomablePlugin].
 *
 * On Android, this will use [SubSamplingImage] when sub-sampling is enabled and a decoder
 * is available via [LocalImageRegionDecoder].
 *
 * On other platforms (Skia), this will always use the standard graphicsLayer approach.
 *
 * @param zoomableState The [ZoomableState] managing zoom transformations.
 * @param config The [ZoomableConfig] for zoom behavior.
 * @param enabled Whether zoom gestures are enabled.
 * @param content The image content to display.
 */
@Composable
internal expect fun ZoomableContent(
  zoomableState: ZoomableState,
  config: ZoomableConfig,
  enabled: Boolean,
  content: @Composable () -> Unit,
)
