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
package com.skydoves.landscapist.glide

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.load.resource.gif.GifDrawable

/** Glide image request type, which decides the result of image data. */
public enum class GlideRequestType {
  DRAWABLE,
  BITMAP,
  GIF
}

internal fun Any?.toImageBitmap(glideRequestType: GlideRequestType): ImageBitmap {
  return when (glideRequestType) {
    GlideRequestType.DRAWABLE -> (this as Drawable).toBitmap().asImageBitmap()
    GlideRequestType.BITMAP -> (this as Bitmap).asImageBitmap()
    GlideRequestType.GIF -> (this as GifDrawable).apply {
      start()
    }.toBitmap().asImageBitmap()
  }
}
