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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntSize

/**
 * Creates and remembers a [ZoomableState] for managing zoom and pan transformations.
 *
 * @param config The configuration for zoom behavior.
 * @return A remembered [ZoomableState] instance.
 */
@Composable
public actual fun rememberZoomableState(
  config: ZoomableConfig,
): ZoomableState {
  return remember(config) { ZoomableState(config) }
}

/**
 * Skiko (Desktop/WASM) implementation of [ZoomableState].
 * Provides basic zoom and pan functionality without sub-sampling support.
 */
@Stable
public actual class ZoomableState internal constructor(
  public actual val config: ZoomableConfig,
) {
  private var _scale by mutableStateOf(config.minZoom)
  private var _offset by mutableStateOf(Offset.Zero)
  private var _isAnimating by mutableStateOf(false)
  private var _layoutSize by mutableStateOf(IntSize.Zero)

  private val scaleAnimatable = Animatable(_scale)
  private val offsetAnimatable = Animatable(_offset, Offset.VectorConverter)

  public actual val layoutSize: IntSize
    get() = _layoutSize

  public actual val transformation: ContentTransformation by derivedStateOf {
    ContentTransformation(
      scale = ScaleFactor(_scale, _scale),
      offset = _offset,
    )
  }

  public actual val zoomFraction: Float by derivedStateOf {
    if (config.maxZoom <= config.minZoom) {
      0f
    } else {
      ((_scale - config.minZoom) / (config.maxZoom - config.minZoom)).coerceIn(0f, 1f)
    }
  }

  public actual val isZoomed: Boolean by derivedStateOf {
    _scale > config.minZoom
  }

  public actual val isAnimating: Boolean
    get() = _isAnimating

  public actual suspend fun zoomTo(
    scale: Float,
    centroid: Offset,
    animationSpec: AnimationSpec<Float>,
  ) {
    val targetScale = scale.coerceIn(config.minZoom, config.maxZoom)
    if (_scale == targetScale) return

    _isAnimating = true
    try {
      // Calculate offset to keep centroid point visually fixed during zoom
      val targetOffset = calculateZoomOffset(
        currentOffset = _offset,
        currentScale = _scale,
        targetScale = targetScale,
        centroid = centroid,
      )

      // Animate both scale and offset together
      val startScale = _scale
      val startOffset = _offset
      scaleAnimatable.snapTo(0f)
      scaleAnimatable.animateTo(1f, animationSpec) {
        val progress = value
        _scale = startScale + (targetScale - startScale) * progress
        _offset = Offset(
          x = startOffset.x + (targetOffset.x - startOffset.x) * progress,
          y = startOffset.y + (targetOffset.y - startOffset.y) * progress,
        )
      }
      _scale = targetScale
      _offset = targetOffset
      constrainOffset()
    } finally {
      _isAnimating = false
    }
  }

  public actual suspend fun zoomBy(
    zoomFactor: Float,
    centroid: Offset,
    animationSpec: AnimationSpec<Float>,
  ) {
    zoomTo(_scale * zoomFactor, centroid, animationSpec)
  }

  public actual suspend fun panBy(
    offset: Offset,
    animationSpec: AnimationSpec<Float>,
  ) {
    val targetOffset = constrainOffset(_offset + offset)
    _isAnimating = true
    try {
      offsetAnimatable.animateTo(targetOffset, spring()) {
        _offset = value
      }
    } finally {
      _isAnimating = false
    }
  }

  public actual suspend fun resetZoom(
    animationSpec: AnimationSpec<Float>,
  ) {
    _isAnimating = true
    try {
      // Animate both scale and offset back to initial state
      val startScale = _scale
      val startOffset = _offset
      scaleAnimatable.snapTo(0f)
      scaleAnimatable.animateTo(1f, animationSpec) {
        val progress = value
        _scale = startScale + (config.minZoom - startScale) * progress
        _offset = Offset(
          x = startOffset.x * (1f - progress),
          y = startOffset.y * (1f - progress),
        )
      }
      _scale = config.minZoom
      _offset = Offset.Zero
    } finally {
      _isAnimating = false
    }
  }

  /**
   * Internal function to set the layout size of the container.
   */
  internal actual fun setLayoutSize(size: IntSize) {
    _layoutSize = size
    constrainOffset()
  }

  /**
   * Internal function to update scale directly from gesture input.
   * The centroid is in local coordinates (0,0 at top-left of container).
   */
  internal actual fun onGestureZoom(zoomChange: Float, centroid: Offset) {
    val newScale = (_scale * zoomChange).coerceIn(config.minZoom, config.maxZoom)
    if (_scale == newScale) return

    // Calculate new offset to keep centroid visually fixed
    _offset = calculateZoomOffset(
      currentOffset = _offset,
      currentScale = _scale,
      targetScale = newScale,
      centroid = centroid,
    )
    _scale = newScale
    constrainOffset()
  }

  /**
   * Internal function to update offset directly from gesture input.
   * The offset will be constrained to keep the image within bounds.
   */
  internal actual fun onGesturePan(pan: Offset) {
    _offset = constrainOffset(_offset + pan)
  }

  /**
   * Calculates the new offset needed to keep a centroid point visually fixed when zooming.
   *
   * The centroid is in local coordinates (0,0 at top-left).
   * GraphicsLayer scales from center, so we convert centroid to center-relative coordinates.
   *
   * Formula: offset_new = (offset_old - centroidFromCenter) * scaleRatio + centroidFromCenter
   */
  private fun calculateZoomOffset(
    currentOffset: Offset,
    currentScale: Float,
    targetScale: Float,
    centroid: Offset,
  ): Offset {
    if (centroid == Offset.Unspecified || _layoutSize == IntSize.Zero) {
      return currentOffset
    }

    // Convert centroid from top-left coordinates to center-relative coordinates
    val centerX = _layoutSize.width / 2f
    val centerY = _layoutSize.height / 2f
    val centroidFromCenter = Offset(centroid.x - centerX, centroid.y - centerY)

    val scaleRatio = targetScale / currentScale
    return (currentOffset - centroidFromCenter) * scaleRatio + centroidFromCenter
  }

  /**
   * Constrains the given offset to keep content within bounds.
   */
  private fun constrainOffset(offset: Offset): Offset {
    if (_layoutSize == IntSize.Zero) return offset

    // Calculate maximum pan distance based on how much the scaled content exceeds the container
    // When scale=1: no overflow, maxOffset=0
    // When scale=2: content is 2x size, can pan by containerSize/2 in each direction
    val maxOffsetX = (_layoutSize.width * (_scale - 1f) / 2f).coerceAtLeast(0f)
    val maxOffsetY = (_layoutSize.height * (_scale - 1f) / 2f).coerceAtLeast(0f)

    return Offset(
      x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
      y = offset.y.coerceIn(-maxOffsetY, maxOffsetY),
    )
  }

  /**
   * Constrains the current offset in place.
   */
  private fun constrainOffset() {
    _offset = constrainOffset(_offset)
  }
}
