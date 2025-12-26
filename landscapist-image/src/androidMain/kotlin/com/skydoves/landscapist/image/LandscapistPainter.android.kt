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
package com.skydoves.landscapist.image

import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection

/**
 * Creates and remembers a [Painter] from Android Bitmap or Drawable data.
 * Supports animated drawables (GIF, APNG, animated WebP) on API 28+.
 */
@Composable
public actual fun rememberLandscapistPainter(data: Any?): Painter {
  return when (data) {
    is Bitmap -> remember(data) { BitmapPainter(data.asImageBitmap()) }
    is Drawable -> rememberDrawablePainter(data)
    else -> EmptyPainter
  }
}

/**
 * Creates a painter that can handle both static and animated drawables.
 */
@Composable
private fun rememberDrawablePainter(drawable: Drawable): Painter {
  // For animated drawables, we need to invalidate on each frame
  var invalidateCount by remember { mutableIntStateOf(0) }

  val painter = remember(drawable, invalidateCount) {
    DrawablePainter(drawable)
  }

  DisposableEffect(drawable) {
    if (drawable is Animatable) {
      val animatable = drawable
      val handler = Handler(Looper.getMainLooper())

      // Set up callback to invalidate on each frame
      val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
          invalidateCount++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
          handler.postAtTime(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
          handler.removeCallbacks(what)
        }
      }

      drawable.callback = callback

      // Start animation if not already running
      if (!animatable.isRunning) {
        animatable.start()
      }

      onDispose {
        animatable.stop()
        drawable.callback = null
      }
    } else {
      onDispose { }
    }
  }

  return painter
}

/**
 * A Painter that wraps an Android Drawable.
 */
private class DrawablePainter(
  private val drawable: Drawable,
) : Painter() {

  override val intrinsicSize: Size
    get() = Size(
      drawable.intrinsicWidth.takeIf { it > 0 }?.toFloat() ?: Float.NaN,
      drawable.intrinsicHeight.takeIf { it > 0 }?.toFloat() ?: Float.NaN,
    )

  override fun applyAlpha(alpha: Float): Boolean {
    drawable.alpha = (alpha * 255).toInt().coerceIn(0, 255)
    return true
  }

  override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return drawable.setLayoutDirection(
        when (layoutDirection) {
          LayoutDirection.Ltr -> android.view.View.LAYOUT_DIRECTION_LTR
          LayoutDirection.Rtl -> android.view.View.LAYOUT_DIRECTION_RTL
        },
      )
    }
    return false
  }

  override fun DrawScope.onDraw() {
    drawIntoCanvas { canvas ->
      val width = size.width.toInt()
      val height = size.height.toInt()

      // Update drawable bounds
      drawable.setBounds(0, 0, width, height)

      // Draw the drawable
      drawable.draw(canvas.nativeCanvas)
    }
  }
}
