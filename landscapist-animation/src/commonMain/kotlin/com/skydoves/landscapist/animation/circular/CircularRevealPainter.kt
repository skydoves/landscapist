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
package com.skydoves.landscapist.animation.circular

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSaveLayer

/**
 * CircularRevealPainter is a [Painter] which animates a clipping circle to reveal an image.
 * Reveal animations provide users visual continuity when we show an image.
 *
 * The image itself is drawn by [painter], never rescaled here. A painter is handed the exact size
 * its caller wants it to fill, already worked out from the caller's `ContentScale`, so scaling the
 * image again would quietly override that and render everything as `ContentScale.Crop`. Delegating
 * also keeps a reveal composed on top of another painter plugin showing that plugin's work.
 *
 * @param painter an image painter to draw into the provided canvas.
 */
internal class CircularRevealPainter(
  private val painter: Painter,
) : Painter() {

  var radius: Float by mutableStateOf(0f, policy = neverEqualPolicy())

  // Held rather than allocated per draw: onDraw runs on every frame of the animation.
  private val maskPaint = Paint()
  private val contentPaint = Paint().apply { blendMode = BlendMode.SrcIn }

  override fun DrawScope.onDraw() {
    // The reveal is measured against the longest side, so it has covered the corners by the time it
    // finishes.
    val revealRadius = size.maxDimension * radius
    if (revealRadius <= 0f) return

    val bounds = size.toRect()
    drawIntoCanvas { canvas ->
      // The circle goes down first as a mask, then the image composites onto it with SrcIn, so the
      // image survives only inside the circle and the reveal edge keeps the circle's anti-aliasing.
      // Clipping to a circular path instead would leave that edge hard and jagged on Android's
      // hardware canvas, which is why the reveal has always been a shape rather than a clip.
      canvas.withSaveLayer(bounds, maskPaint) {
        drawCircle(
          color = Color.Black,
          radius = revealRadius,
          center = Offset(size.width / 2f, size.height / 2f),
        )
        canvas.withSaveLayer(bounds, contentPaint) {
          with(painter) { draw(size) }
        }
      }
    }
  }

  /** return the dimension size of the [painter]'s intrinsic width and height. */
  override val intrinsicSize: Size get() = painter.intrinsicSize
}
