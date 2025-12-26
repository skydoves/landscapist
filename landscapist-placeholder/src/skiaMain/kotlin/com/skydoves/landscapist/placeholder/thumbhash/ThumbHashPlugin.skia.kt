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
package com.skydoves.landscapist.placeholder.thumbhash

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal actual fun decodeThumbHashToImageBitmap(thumbHash: ByteArray): ImageBitmap? {
  val image = ThumbHashDecoder.decode(thumbHash) ?: return null

  val bitmap = Bitmap()
  val imageInfo = ImageInfo(
    width = image.width,
    height = image.height,
    colorType = ColorType.RGBA_8888,
    alphaType = ColorAlphaType.UNPREMUL,
  )
  bitmap.allocPixels(imageInfo)
  bitmap.installPixels(image.rgba)

  return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
}
