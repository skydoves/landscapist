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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter

/**
 * This is an extension of the [Painter] for giving crossfade animation to the given [imageBitmap].
 *
 * @param imageBitmap an image bitmap for loading the content.
 * @param durationMs milli-second times from start to finish animation.
 */
@Composable
internal fun Painter.rememberCrossfadePainter(
  imageBitmap: ImageBitmap,
  durationMs: Int
): Painter {
  val size = Size(imageBitmap.width.toFloat(), imageBitmap.height.toFloat())
  val colorMatrix = remember { ColorMatrix() }
  val fadeInTransition = updateFadeInTransition(key = size, durationMs = durationMs)
  val transitionColorFilter = if (!fadeInTransition.isFinished) {
    colorMatrix.apply {
      updateAlpha(fadeInTransition.alpha)
      updateBrightness(fadeInTransition.brightness)
      updateSaturation(fadeInTransition.saturation)
    }.let { ColorFilter.colorMatrix(it) }
  } else {
    // If the fade-in isn't running, reset the color matrix
    null
  }

  return remember(
    key1 = fadeInTransition.alpha,
    key2 = fadeInTransition.brightness,
    key3 = fadeInTransition.saturation
  ) {
    CrossfadePainter(
      imageBitmap = imageBitmap,
      painter = this
    ).also {
      it.transitionColorFilter = transitionColorFilter
    }
  }
}
