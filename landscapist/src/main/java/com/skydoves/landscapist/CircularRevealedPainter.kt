/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
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

package com.skydoves.landscapist

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.createAnimation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.util.Pools

/**
 * CircularRevealedPainter is a [Painter] which animates a clipping circle to reveal an image.
 * Reveal animations provide users visual continuity when we show an image.
 *
 * @param imageBitmap an image bitmap for loading for the content.
 * @param painter an image painter to draw an [ImageBitmap] into the provided canvas.
 * @param clock an interface allows AnimationClock to be subscribed and unsubscribed.
 * @param durationMs milli-second times from start to finish animation.
 */
internal class CircularRevealedPainter(
  private val imageBitmap: ImageBitmap,
  private val painter: Painter,
  clock: AnimationClockObservable,
  durationMs: Int
) : Painter() {

  private var radius by mutableStateOf(0f)

  private val circularAnimation =
    CircularRevealedAnimation.definition(durationMs).createAnimation(clock)

  var isFinished by mutableStateOf(false)
    private set

  init {
    circularAnimation.onUpdate = {
      radius = circularAnimation[CircularRevealedAnimation.Radius]
    }

    circularAnimation.onStateChangeFinished = { state ->
      if (state == CircularRevealedAnimation.State.Finished) {
        isFinished = true
      }
    }
  }

  override fun DrawScope.onDraw() {
    val paint = paintPool.acquire() ?: Paint()
    val shaderMatrix = Matrix()
    var scale: Float
    var dx = 0f
    var dy = 0f

    try {
      val shader = ImageShader(imageBitmap, TileMode.Clamp)
      val brush = ShaderBrush(shader)

      paint.asFrameworkPaint().apply {
        isAntiAlias = true
        isDither = true
        isFilterBitmap = true
      }

      drawIntoCanvas { canvas ->
        canvas.saveLayer(size.toRect(), paint)

        val mDrawableRect = RectF(0f, 0f, size.width, size.height)
        val bitmapWidth: Int = imageBitmap.asAndroidBitmap().width
        val bitmapHeight: Int = imageBitmap.asAndroidBitmap().height

        if (bitmapWidth * mDrawableRect.height() > mDrawableRect.width() * bitmapHeight) {
          scale = mDrawableRect.height() / bitmapHeight.toFloat()
          dx = (mDrawableRect.width() - bitmapWidth * scale) * 0.5f
        } else {
          scale = mDrawableRect.width() / bitmapWidth.toFloat()
          dy = (mDrawableRect.height() - bitmapHeight * scale) * 0.5f
        }

        // resize the matrix to scale by sx and sy.
        shaderMatrix.setScale(scale, scale)

        // post translate the matrix with the specified translation.
        shaderMatrix.postTranslate(
          (dx + 0.5f) + mDrawableRect.left,
          (dy + 0.5f) + mDrawableRect.top
        )

        shader.setLocalMatrix(shaderMatrix)

        drawCircle(brush, size.height * radius, Offset(size.width / 2, size.height / 2))

        canvas.restore()
      }
    } finally {
      // resets the paint and release to the pool.
      paint.asFrameworkPaint().reset()
      paintPool.release(paint)
    }
  }

  /**
   * if the animation is ongoing, returns a [CircularRevealedPainter],
   * if the animation is finished, returns a [painter].
   */
  fun getMainPainter(): Painter {
    return if (!isFinished) this
    else this.painter
  }

  /** return the dimension size of the [ImageBitmap]'s intrinsic width and height. */
  override val intrinsicSize: Size get() = painter.intrinsicSize

  /** starts the circular revealed animation by transitioning to the Loaded state. */
  fun start() {
    circularAnimation.toState(CircularRevealedAnimation.State.Finished)
  }
}

/** paint pool which caching and reusing [Paint] instances. */
private val paintPool = Pools.SimplePool<Paint>(2)
