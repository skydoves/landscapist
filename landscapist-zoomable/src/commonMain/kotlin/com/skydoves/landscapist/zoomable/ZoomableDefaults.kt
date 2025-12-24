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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default values for [ZoomableConfig].
 */
public object ZoomableDefaults {
  /**
   * Default minimum zoom scale (no zoom out beyond original size).
   */
  public const val MinZoom: Float = 1f

  /**
   * Default maximum zoom scale.
   */
  public const val MaxZoom: Float = 5f

  /**
   * Default zoom scale when double-tapping.
   */
  public const val DoubleTapZoom: Float = 2.5f

  /**
   * Default tile size for sub-sampling.
   * Each tile will be this size, balancing between memory usage and number of tiles.
   */
  public val TileSize: Dp = 256.dp

  /**
   * Default threshold for the image size to enable sub-sampling automatically.
   * Images larger than this (in either dimension) will benefit from sub-sampling.
   */
  public val SubSamplingThreshold: Dp = 1024.dp

  /**
   * Default animation duration in milliseconds for zoom animations.
   */
  public const val AnimationDurationMs: Int = 300

  /**
   * Default fling velocity threshold.
   */
  public const val FlingVelocityThreshold: Float = 1000f
}
