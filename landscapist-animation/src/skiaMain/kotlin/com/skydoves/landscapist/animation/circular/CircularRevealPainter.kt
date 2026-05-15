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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter

/**
 * CircularRevealPainter is a [Painter] which animates a clipping circle to reveal an image.
 * Reveal animations provide users visual continuity when we show an image.
 *
 * @param imageBitmap an image bitmap for loading for the content.
 * @param painter an image painter to draw an [ImageBitmap] into the provided canvas.
 */
internal actual class CircularRevealPainter actual constructor(
  actual val imageBitmap: ImageBitmap,
  actual val painter: Painter,
) : Painter() {

  actual var radius by mutableStateOf(0f, policy = neverEqualPolicy())

  actual override fun DrawScope.onDraw() {
    var dx = 0f
    var dy = 0f
    var scale: Float
    val paint = Paint()

    paint.asFrameworkPaint().apply {
      isAntiAlias = true
      isDither = true
    }

    drawIntoCanvas { canvas ->
      // cache the paint in the internal stack.
      canvas.saveLayer(size.toRect(), paint)

      val mDrawableRect = Rect(offset = Offset.Zero, size = Size(size.width, size.height))
      val bitmapWidth: Int = imageBitmap.width
      val bitmapHeight: Int = imageBitmap.height

      if (bitmapWidth * mDrawableRect.size.height > mDrawableRect.size.width * bitmapHeight) {
        scale = mDrawableRect.size.height / bitmapHeight.toFloat()
        dx = (mDrawableRect.size.width - bitmapWidth * scale) * 0.5f
      } else {
        scale = mDrawableRect.size.width / bitmapWidth.toFloat()
        dy = (mDrawableRect.size.height - bitmapHeight * scale) * 0.5f
      }
      // calculate radius and clip the canvas to that circle to reveal the image.
      val calculatedRadius = size.width.coerceAtLeast(size.height) * radius
      val center = Offset(size.width / 2, size.height / 2)
      val clipPath = Path().apply {
        addOval(
          Rect(
            left = center.x - calculatedRadius,
            top = center.y - calculatedRadius,
            right = center.x + calculatedRadius,
            bottom = center.y + calculatedRadius,
          ),
        )
      }
      canvas.save()
      canvas.clipPath(clipPath)
      // translate and scale the canvas so the bitmap is drawn centered.
      canvas.translate(
        (dx + 0.5f) + mDrawableRect.left,
        (dy + 0.5f) + mDrawableRect.top,
      )
      canvas.scale(scale, scale)
      canvas.drawImage(imageBitmap, Offset.Zero, paint)
      canvas.restore()
      // restore canvas.
      canvas.restore()
      // resets the paint and release to the pool.
      paint.asFrameworkPaint().reset()
    }
  }

  /** return the dimension size of the [painter]'s intrinsic width and height. */
  actual override val intrinsicSize: Size get() = painter.intrinsicSize
}
