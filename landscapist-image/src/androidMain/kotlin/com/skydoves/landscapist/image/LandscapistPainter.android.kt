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
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.skydoves.landscapist.rememberDrawablePainter

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
