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
package com.skydoves.landscapist.transformation.blur

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import com.skydoves.landscapist.transformation.RenderScriptToolkit
import com.skydoves.landscapist.transformation.TransformationPainter

/**
 * This is an extension of the [Painter] for giving blur transformation effect to the given [imageBitmap].
 *
 * @param imageBitmap an image bitmap for loading the content.
 * @property radius The radius of the pixels used to blur, a value from 1 to 25. Default is 10.
 */
@Composable
internal fun Painter.rememberBlurPainter(
  imageBitmap: ImageBitmap,
  radius: Int
): Painter {
  var androidBitmap = imageBitmap.asAndroidBitmap()

  if (!(
    androidBitmap.config == Bitmap.Config.ARGB_8888 ||
      androidBitmap.config == Bitmap.Config.ALPHA_8
    )
  ) {
    androidBitmap = androidBitmap.copy(Bitmap.Config.ARGB_8888, false)
  }

  val blurredBitmap = remember(imageBitmap, radius) {
    RenderScriptToolkit.blur(
      inputBitmap = androidBitmap,
      radius = radius
    )
  }

  return remember(this) {
    TransformationPainter(
      imageBitmap = blurredBitmap.asImageBitmap(),
      painter = this
    )
  }
}
