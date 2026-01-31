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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android implementation that converts [Bitmap] to [ImageBitmap].
 */
public actual fun convertToImageBitmap(data: Any): ImageBitmap? {
  return when (data) {
    is Bitmap -> data.asImageBitmap()
    is ImageBitmap -> data
    else -> null
  }
}

/**
 * Android implementation: checks if data is a Bitmap or ImageBitmap.
 */
public actual fun isBitmapType(data: Any?): Boolean {
  return data is Bitmap || data is ImageBitmap
}

/**
 * Android implementation: gets width from Bitmap or ImageBitmap.
 */
public actual fun getBitmapWidth(data: Any?): Int {
  return when (data) {
    is Bitmap -> data.width
    is ImageBitmap -> data.width
    else -> 0
  }
}

/**
 * Android implementation: gets height from Bitmap or ImageBitmap.
 */
public actual fun getBitmapHeight(data: Any?): Int {
  return when (data) {
    is Bitmap -> data.height
    is ImageBitmap -> data.height
    else -> 0
  }
}
