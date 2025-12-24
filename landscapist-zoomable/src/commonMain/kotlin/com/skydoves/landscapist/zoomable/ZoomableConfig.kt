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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * Configuration for [ZoomablePlugin] behavior.
 *
 * @property minZoom The minimum zoom scale. Defaults to [ZoomableDefaults.MinZoom].
 * @property maxZoom The maximum zoom scale. Defaults to [ZoomableDefaults.MaxZoom].
 * @property doubleTapZoom The zoom scale to apply when double-tapping. Defaults to [ZoomableDefaults.DoubleTapZoom].
 * @property enableDoubleTapZoom Whether double-tap to zoom gesture is enabled. Defaults to true.
 * @property enableFling Whether fling gesture after pan is enabled. Defaults to true.
 * @property enableSubSampling Whether sub-sampling (tiling) for large images is enabled. Defaults to false (opt-in).
 * @property subSamplingConfig Optional configuration for sub-sampling behavior.
 */
@Immutable
public data class ZoomableConfig(
  public val minZoom: Float = ZoomableDefaults.MinZoom,
  public val maxZoom: Float = ZoomableDefaults.MaxZoom,
  public val doubleTapZoom: Float = ZoomableDefaults.DoubleTapZoom,
  public val enableDoubleTapZoom: Boolean = true,
  public val enableFling: Boolean = true,
  public val enableSubSampling: Boolean = false,
  public val subSamplingConfig: SubSamplingConfig = SubSamplingConfig(),
)

/**
 * Configuration for sub-sampling behavior.
 *
 * Sub-sampling enables efficient rendering of very large images by loading only
 * the visible tiles at the appropriate resolution. This is useful for images
 * that are too large to fit in memory when fully decoded.
 *
 * **Note:** Sub-sampling requires the image source to support region decoding
 * (e.g., local files, content URIs). Network images may need to be cached first.
 *
 * @property tileSize The size of each tile. Larger tiles mean fewer tiles but more memory per tile.
 *   Defaults to [ZoomableDefaults.TileSize].
 * @property threshold The minimum image dimension (in either width or height) to enable sub-sampling.
 *   Images smaller than this will be rendered normally without tiling.
 *   Defaults to [ZoomableDefaults.SubSamplingThreshold].
 */
@Immutable
public data class SubSamplingConfig(
  public val tileSize: Dp = ZoomableDefaults.TileSize,
  public val threshold: Dp = ZoomableDefaults.SubSamplingThreshold,
)
