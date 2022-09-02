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
@file:JvmName("ImageWithSource")
@file:JvmMultifileClass

package com.skydoves.landscapist

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale

/**
 * Draw image using a drawable source one of [ImageBitmap], [ImageVector], or [Painter].
 *
 * @param source Image source one of [ImageBitmap], [ImageVector], or [Painter].
 * @param alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @param contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @param contentDescription The content description used to provide accessibility to describe the image.
 * @param colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @param alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 */
@Composable
public fun ImageBySource(
  source: Any,
  modifier: Modifier,
  alignment: Alignment,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
  colorFilter: ColorFilter? = null,
  alpha: Float = DefaultAlpha
) {
  when (source) {
    is ImageBitmap -> {
      Image(
        bitmap = source,
        modifier = modifier,
        alignment = alignment,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha
      )
    }
    is ImageVector -> {
      Image(
        imageVector = source,
        modifier = modifier,
        alignment = alignment,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha
      )
    }
    is Painter -> {
      Image(
        painter = source,
        modifier = modifier,
        alignment = alignment,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha
      )
    }
    else -> {
      throw IllegalArgumentException(
        "Wrong source was used: $source, " +
          "The source should be one of ImageBitmap, ImageVector, or Painter."
      )
    }
  }
}
