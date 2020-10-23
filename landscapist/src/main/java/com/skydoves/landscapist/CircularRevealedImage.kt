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

@file:JvmName("CircularRevealedImage")
@file:JvmMultifileClass

package com.skydoves.landscapist

import androidx.compose.animation.asDisposableClock
import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.graphics.painter.ImagePainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AnimationClockAmbient

/** CircularRevealedImage is an image composable for animating a clipping circle to reveal. */
@Composable
fun CircularRevealedImage(
  asset: ImageAsset,
  imagePainter: Painter = ImagePainter(asset),
  modifier: Modifier = Modifier,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Crop,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  circularRevealedEnabled: Boolean = false,
  circularRevealedDuration: Int = DefaultCircularRevealedDuration,
  clock: AnimationClockObservable = AnimationClockAmbient.current.asDisposableClock(),
) {
  val circularRevealedPainter = remember(imagePainter) {
    CircularRevealedPainter(
      asset,
      imagePainter,
      clock,
      circularRevealedDuration
    ).also { it.start() }
  }
  Image(
    painter = if (circularRevealedEnabled) {
      circularRevealedPainter.getMainPainter()
    } else {
      imagePainter
    },
    modifier = modifier,
    alignment = alignment,
    contentScale = contentScale,
    colorFilter = colorFilter,
    alpha = alpha
  )
}

/** a definition of the default circular revealed animations duration. */
const val DefaultCircularRevealedDuration = 350
