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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.skydoves.landscapist.core.decoder.RawImageData
import org.jetbrains.skia.Image

/**
 * Skia implementation that converts [RawImageData] to [ImageBitmap].
 * Used by Apple (iOS/macOS) and Wasm platforms.
 */
public actual fun convertToImageBitmap(data: Any): ImageBitmap? {
  return when (data) {
    is RawImageData -> {
      try {
        val skiaImage = Image.makeFromEncoded(data.data)
        skiaImage.toComposeImageBitmap()
      } catch (e: Exception) {
        null
      }
    }
    is ImageBitmap -> data
    else -> null
  }
}

/**
 * Skia implementation: checks if data is an ImageBitmap.
 * Note: Skia platforms typically use RawImageData, not pre-decoded bitmaps.
 */
public actual fun isBitmapType(data: Any?): Boolean {
  return data is ImageBitmap
}

/**
 * Skia implementation: gets width from ImageBitmap.
 */
public actual fun getBitmapWidth(data: Any?): Int {
  return when (data) {
    is ImageBitmap -> data.width
    else -> 0
  }
}

/**
 * Skia implementation: gets height from ImageBitmap.
 */
public actual fun getBitmapHeight(data: Any?): Int {
  return when (data) {
    is ImageBitmap -> data.height
    else -> 0
  }
}
