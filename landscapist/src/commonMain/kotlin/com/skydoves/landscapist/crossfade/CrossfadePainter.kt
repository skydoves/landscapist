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
package com.skydoves.landscapist.crossfade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSaveLayer
import com.skydoves.landscapist.InternalLandscapistApi
import kotlinx.coroutines.launch

/**
 * Fades [painter] in, in the drawing rather than in the composition.
 *
 * The same effect as the crossfade modifiers: opacity over half the duration, brightness over three
 * quarters of it, saturation over all of it. Doing it here rather than by stacking composables is
 * what lets an image with a crossfade keep the cheap path, where the container draws the image
 * itself and nothing is composed inside it.
 *
 * There is nothing to fade out from: this is used only when what came before drew nothing, which is
 * an image with no loading content of its own. Anything that does have something on screen to leave
 * still goes through [CrossfadeWithEffect], which can stack the two.
 */
internal class CrossfadePainter(
  private val painter: Painter,
) : Painter() {

  var alpha: Float by mutableFloatStateOf(0f)
  var brightness: Float by mutableFloatStateOf(INITIAL_BRIGHTNESS)
  var saturation: Float by mutableFloatStateOf(0f)

  // Held rather than allocated per draw: onDraw runs on every frame of the animation.
  private val colorMatrix = ColorMatrix()
  private val paint = Paint()

  override fun DrawScope.onDraw() {
    val alphaValue = alpha
    val brightnessValue = brightness
    val saturationValue = saturation
    if (alphaValue >= 1f && brightnessValue >= 1f && saturationValue >= 1f) {
      with(painter) { draw(size) }
      return
    }
    colorMatrix.apply {
      updateBrightness(brightnessValue)
      updateSaturation(saturationValue)
    }
    paint.colorFilter = ColorFilter.colorMatrix(colorMatrix)
    paint.alpha = alphaValue
    drawIntoCanvas { canvas ->
      canvas.withSaveLayer(size.toRect(), paint) {
        with(painter) { draw(size) }
      }
    }
  }

  override val intrinsicSize: Size get() = painter.intrinsicSize

  private companion object {
    const val INITIAL_BRIGHTNESS = 0.8f
  }
}

/**
 * Fades this painter in over [durationMs], restarting whenever the painter itself changes.
 *
 * @param durationMs How long the fade takes from start to finish. Zero returns this painter as it
 * is.
 * @param skipFirst Whether the first painter seen here is already on screen and must not be faded.
 * That is an image read straight from the memory cache: fading it in from nothing is the blink a
 * crossfade exists to prevent. Every painter after the first replaces something the viewer can see,
 * so it fades whatever this is set to.
 */
@Composable
@InternalLandscapistApi
public fun Painter.rememberCrossfadePainter(durationMs: Int, skipFirst: Boolean = false): Painter {
  if (durationMs <= 0) return this
  val first = remember { this }
  if (skipFirst && first === this) return this
  val fading = remember(this) { CrossfadePainter(this) }
  LaunchedEffect(fading) {
    val alpha = Animatable(0f)
    val brightness = Animatable(0.8f)
    val saturation = Animatable(0f)
    launch {
      alpha.animateTo(1f, tween(durationMillis = durationMs / 2)) { fading.alpha = value }
    }
    launch {
      brightness.animateTo(1f, tween(durationMillis = durationMs * 3 / 4)) {
        fading.brightness = value
      }
    }
    launch {
      saturation.animateTo(1f, tween(durationMillis = durationMs)) { fading.saturation = value }
    }
  }
  return fading
}
