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
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection

/**
 * A [Painter] that wraps another painter and applies zoom transformations from [ZoomableState].
 *
 * @param painter The original painter to wrap.
 * @param state The [ZoomableState] providing transformation information.
 */
public class ZoomablePainter internal constructor(
  private val painter: Painter,
  private val state: ZoomableState,
) : Painter() {

  private var alpha: Float = 1f
  private var colorFilter: ColorFilter? = null

  override val intrinsicSize: Size
    get() = painter.intrinsicSize

  override fun DrawScope.onDraw() {
    val transformation = state.transformation

    withTransform({
      // Apply scale transformation
      scale(
        scaleX = transformation.scale.scaleX,
        scaleY = transformation.scale.scaleY,
        pivot = center,
      )
      // Apply translation
      translate(
        left = transformation.offset.x,
        top = transformation.offset.y,
      )
      // Apply rotation if any
      if (transformation.rotationZ != 0f) {
        rotate(transformation.rotationZ, pivot = center)
      }
    }) {
      with(painter) {
        draw(size = size, alpha = alpha, colorFilter = colorFilter)
      }
    }
  }

  override fun applyAlpha(alpha: Float): Boolean {
    this.alpha = alpha
    return true
  }

  override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
    this.colorFilter = colorFilter
    return true
  }

  override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
    return false
  }
}

/**
 * Remember a [ZoomablePainter] that wraps the given painter with zoom transformations.
 *
 * @param painter The painter to wrap.
 * @param state The [ZoomableState] providing transformation information.
 * @return A [ZoomablePainter] that applies zoom transformations.
 */
@Composable
public fun rememberZoomablePainter(
  painter: Painter,
  state: ZoomableState,
): ZoomablePainter {
  return remember(painter, state) {
    ZoomablePainter(painter, state)
  }
}
