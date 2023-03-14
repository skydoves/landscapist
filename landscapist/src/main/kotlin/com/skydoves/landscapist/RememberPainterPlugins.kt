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
package com.skydoves.landscapist

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.graphics.drawable.toBitmap
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.plugins.composePainterPlugins

/**
 * Remembers [Drawable] wrapped up as a [Painter] with a given the list of [ImagePlugin].
 * This function attempts to un-wrap the drawable contents and use Compose primitives where possible.
 *
 * If the provided [drawable] is `null`, an empty no-op painter is returned.
 *
 * This function tries to dispatch lifecycle events to [drawable] as much as possible from
 * within Compose.
 *
 * @param drawable A [Drawable] to be drawn.
 * @param imagePlugins A list of [ImagePlugin] that will be applied to the drawable painter.
 */
@Composable
@InternalLandscapistApi
public fun rememberDrawablePainter(
  drawable: Drawable,
  imagePlugins: List<ImagePlugin>,
): Painter =
  remember(drawable, imagePlugins) {
    when (drawable) {
      is BitmapDrawable -> BitmapPainter(drawable.bitmap.asImageBitmap())
      is ColorDrawable -> ColorPainter(Color(drawable.color))
      // Since the DrawablePainter will be remembered and it implements RememberObserver, it
      // will receive the necessary events
      else -> DrawablePainter(drawable.mutate())
    }
  }.composePainterPlugins(
    imagePlugins = imagePlugins,
    imageBitmap = drawable.toBitmap().asImageBitmap(),
  )

/**
 * Remembers [ImageBitmap] wrapped up as a [Painter] with a given the list of [ImagePlugin].
 *
 * @param imageBitmap An [ImageBitmap] to be drawn.
 * @param imagePlugins A list of [ImagePlugin] that will be applied to the drawable painter.
 */
@Composable
@InternalLandscapistApi
public fun rememberBitmapPainter(
  imagePlugins: List<ImagePlugin>,
  imageBitmap: ImageBitmap,
): Painter = remember(imageBitmap, imagePlugins) {
  BitmapPainter(imageBitmap)
}.composePainterPlugins(imagePlugins = imagePlugins, imageBitmap = imageBitmap)
