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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.skydoves.landscapist.core.decoder.RawImageData
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/**
 * Creates and remembers a [Painter] from Skia Bitmap, RawImageData, or ImageBitmap.
 * Desktop decodes to its own image type, which [platformImageBitmapOrNull] handles.
 */
@Composable
public actual fun rememberLandscapistPainter(data: Any?): Painter {
  return remember(data) { landscapistPainterOrNull(data) ?: EmptyPainter }
}

/** Every decoded type this source set knows about is a still bitmap, so none of them need a
 * composed painter. */
internal actual fun landscapistPainterOrNull(data: Any?): Painter? = when (data) {
  is Bitmap -> BitmapPainter(data.asComposeImageBitmap())
  is RawImageData -> {
    try {
      BitmapPainter(Image.makeFromEncoded(data.data).toComposeImageBitmap())
    } catch (e: Exception) {
      EmptyPainter
    }
  }
  is ImageBitmap -> BitmapPainter(data)
  // Anything else is a type no decoder here produces, which draws nothing, exactly as it did
  // through rememberLandscapistPainter. It is not a reason to go back to a composed painter.
  else -> platformImageBitmapOrNull(data)?.let { BitmapPainter(it) } ?: EmptyPainter
}

internal actual val ComposedPainterEverNeeded: Boolean = false
