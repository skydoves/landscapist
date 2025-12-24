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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates the sample size (power of 2) needed for a given zoom level.
 * Used for sub-sampling large images.
 *
 * @param zoom The current zoom level.
 * @return The sample size as a power of 2.
 */
internal fun calculateSampleSize(zoom: Float): Int {
  var sampleSize = 1
  while (sampleSize * 2 <= (1 / zoom)) {
    sampleSize *= 2
  }
  return max(1, sampleSize)
}

/**
 * Coerces the offset to keep content within the visible bounds.
 *
 * @param offset The current offset.
 * @param scale The current scale factor.
 * @param contentSize The size of the content.
 * @param containerSize The size of the container.
 * @return The coerced offset that keeps content visible.
 */
internal fun coerceOffset(
  offset: Offset,
  scale: ScaleFactor,
  contentSize: Size,
  containerSize: Size,
): Offset {
  val scaledWidth = contentSize.width * scale.scaleX
  val scaledHeight = contentSize.height * scale.scaleY

  // Calculate the maximum allowed offset in each direction
  val maxOffsetX = max(0f, (scaledWidth - containerSize.width) / 2f)
  val maxOffsetY = max(0f, (scaledHeight - containerSize.height) / 2f)

  return Offset(
    x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
    y = offset.y.coerceIn(-maxOffsetY, maxOffsetY),
  )
}

/**
 * Calculates the focal point offset adjustment when zooming.
 *
 * @param currentOffset The current offset.
 * @param currentScale The current scale.
 * @param targetScale The target scale.
 * @param centroid The zoom focal point.
 * @return The new offset that keeps the centroid in place.
 */
internal fun calculateZoomOffset(
  currentOffset: Offset,
  currentScale: Float,
  targetScale: Float,
  centroid: Offset,
): Offset {
  if (centroid == Offset.Unspecified) {
    return currentOffset
  }

  val scaleDiff = targetScale / currentScale
  return currentOffset + (centroid - centroid * scaleDiff)
}

/**
 * Checks if the given size exceeds the sub-sampling threshold.
 *
 * @param size The image size to check.
 * @param threshold The threshold in pixels.
 * @return True if the image should use sub-sampling.
 */
internal fun shouldUseSubSampling(size: Size, threshold: Int): Boolean {
  return size.width > threshold || size.height > threshold
}

/**
 * Calculates the base zoom level to fit content within container.
 *
 * @param contentSize The size of the content.
 * @param containerSize The size of the container.
 * @return The scale factor to fit content in container.
 */
internal fun calculateFitScale(contentSize: Size, containerSize: Size): Float {
  if (contentSize.width <= 0 || contentSize.height <= 0) return 1f
  if (containerSize.width <= 0 || containerSize.height <= 0) return 1f

  val scaleX = containerSize.width / contentSize.width
  val scaleY = containerSize.height / contentSize.height

  return min(scaleX, scaleY)
}
