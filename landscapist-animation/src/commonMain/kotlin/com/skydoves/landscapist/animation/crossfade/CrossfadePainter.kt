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
package com.skydoves.landscapist.animation.crossfade

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection

/**
 * CrossfadePainter is a [Painter] that applies crossfade filter effect on the given [imageBitmap].
 *
 * @param imageBitmap an image bitmap for loading for the content.
 * @param painter an image painter to draw an [ImageBitmap] into the provided canvas.
 */
internal expect class CrossfadePainter(
  imageBitmap: ImageBitmap,
  painter: Painter,
) : Painter {
  internal val imageBitmap: ImageBitmap
  internal val painter: Painter
  internal var transitionColorFilter: ColorFilter?
  override fun DrawScope.onDraw()

  override val intrinsicSize: Size
  override fun applyAlpha(alpha: Float): Boolean
  override fun applyColorFilter(colorFilter: ColorFilter?): Boolean
  override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean
}
