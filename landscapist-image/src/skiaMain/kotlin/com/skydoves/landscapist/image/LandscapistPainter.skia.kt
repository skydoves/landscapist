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
 * Used by Apple (iOS/macOS) and Wasm platforms.
 */
@Composable
public actual fun rememberLandscapistPainter(data: Any?): Painter {
  return remember(data) {
    when (data) {
      is Bitmap -> BitmapPainter(data.asComposeImageBitmap())
      is RawImageData -> {
        try {
          val skiaImage = Image.makeFromEncoded(data.data)
          BitmapPainter(skiaImage.toComposeImageBitmap())
        } catch (e: Exception) {
          EmptyPainter
        }
      }
      is ImageBitmap -> BitmapPainter(data)
      else -> EmptyPainter
    }
  }
}
