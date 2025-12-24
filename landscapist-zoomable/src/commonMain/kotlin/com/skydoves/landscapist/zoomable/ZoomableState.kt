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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * Creates and remembers a [ZoomableState] for managing zoom and pan transformations.
 *
 * @param config The configuration for zoom behavior.
 * @return A remembered [ZoomableState] instance.
 */
@Composable
public expect fun rememberZoomableState(
  config: ZoomableConfig = ZoomableConfig(),
): ZoomableState

/**
 * State holder for zoomable content that manages zoom and pan transformations.
 *
 * Use [rememberZoomableState] to create and remember an instance of this class.
 */
@Stable
public expect class ZoomableState {
  /**
   * The current transformation applied to the content.
   */
  public val transformation: ContentTransformation

  /**
   * The current zoom fraction between 0 and 1, where:
   * - 0 means minimum zoom (minZoom)
   * - 1 means maximum zoom (maxZoom)
   */
  public val zoomFraction: Float

  /**
   * Whether the content is currently zoomed beyond the minimum zoom level.
   */
  public val isZoomed: Boolean

  /**
   * Whether an animation is currently running.
   */
  public val isAnimating: Boolean

  /**
   * The configuration for this zoomable state.
   */
  public val config: ZoomableConfig

  /**
   * Zooms to a specific scale at the given centroid point.
   *
   * @param scale The target scale to zoom to.
   * @param centroid The focal point for the zoom. Defaults to the center.
   * @param animationSpec The animation specification. Defaults to spring animation.
   */
  public suspend fun zoomTo(
    scale: Float,
    centroid: Offset = Offset.Unspecified,
    animationSpec: AnimationSpec<Float> = spring(),
  )

  /**
   * Zooms by a relative factor at the given centroid point.
   *
   * @param zoomFactor The factor to multiply the current scale by.
   * @param centroid The focal point for the zoom. Defaults to the center.
   * @param animationSpec The animation specification. Defaults to spring animation.
   */
  public suspend fun zoomBy(
    zoomFactor: Float,
    centroid: Offset = Offset.Unspecified,
    animationSpec: AnimationSpec<Float> = spring(),
  )

  /**
   * Pans the content by the given offset.
   *
   * @param offset The offset to pan by.
   * @param animationSpec The animation specification. Defaults to spring animation.
   */
  public suspend fun panBy(
    offset: Offset,
    animationSpec: AnimationSpec<Float> = spring(),
  )

  /**
   * Resets the zoom and pan to the initial state.
   *
   * @param animationSpec The animation specification. Defaults to spring animation.
   */
  public suspend fun resetZoom(
    animationSpec: AnimationSpec<Float> = spring(),
  )

  /**
   * The current layout size of the zoomable container.
   */
  public val layoutSize: IntSize

  /**
   * Internal function to set the layout size of the container.
   * This is used to constrain pan movements within bounds.
   */
  internal fun setLayoutSize(size: IntSize)

  /**
   * Internal function to update scale directly from gesture input.
   */
  internal fun onGestureZoom(zoomChange: Float, centroid: Offset)

  /**
   * Internal function to update offset directly from gesture input.
   * The offset will be constrained to keep the image within bounds.
   */
  internal fun onGesturePan(pan: Offset)
}
