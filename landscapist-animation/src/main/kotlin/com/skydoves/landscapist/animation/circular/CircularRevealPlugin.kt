/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import com.skydoves.landscapist.plugins.ImagePlugin

/**
 * An image plugin that extends [ImagePlugin.PainterPlugin] to be executed over the given painter.ø
 *
 * @property duration milli-second times from start to finish animation.
 * @property onFinishListener A finish listener of the circular reveal animation.
 */
@Immutable
public data class CircularRevealPlugin(
  public val duration: Int = DefaultCircularRevealDuration,
  public val onFinishListener: CircularRevealFinishListener? = null
) : ImagePlugin.PainterPlugin {

  /**
   * Compose circular reveal painter with an [imageBitmap] to the given [painter].
   *
   * @param imageBitmap A target [ImageBitmap] to be drawn on the painter.
   * @param painter A given painter to be executed circular reveal animation.
   */
  @Composable
  override fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter {
    return painter.rememberCircularRevealPainter(
      imageBitmap = imageBitmap,
      durationMs = duration,
      onFinishListener = onFinishListener
    )
  }
}
