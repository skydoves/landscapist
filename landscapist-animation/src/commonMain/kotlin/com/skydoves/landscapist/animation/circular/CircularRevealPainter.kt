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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter

/**
 * CircularRevealPainter is a [Painter] which animates a clipping circle to reveal an image.
 * Reveal animations provide users visual continuity when we show an image.
 *
 * @param imageBitmap an image bitmap for loading for the content.
 * @param painter an image painter to draw an [ImageBitmap] into the provided canvas.
 */
internal expect class CircularRevealPainter(
  imageBitmap: ImageBitmap,
  painter: Painter,
) : Painter {
  internal val imageBitmap: ImageBitmap
  internal val painter: Painter
  internal var radius: Float
}
