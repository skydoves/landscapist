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
package com.skydoves.landscapist.placeholder.blurhash

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal actual fun decodeBlurHashToImageBitmap(
  blurHash: String,
  width: Int,
  height: Int,
  punch: Float,
): ImageBitmap? {
  val pixels = BlurHashDecoder.decode(blurHash, width, height, punch) ?: return null

  // Convert ARGB IntArray to RGBA ByteArray for Skia
  val rgbaBytes = ByteArray(width * height * 4)
  for (i in pixels.indices) {
    val pixel = pixels[i]
    val a = (pixel shr 24) and 0xFF
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF

    rgbaBytes[i * 4] = r.toByte()
    rgbaBytes[i * 4 + 1] = g.toByte()
    rgbaBytes[i * 4 + 2] = b.toByte()
    rgbaBytes[i * 4 + 3] = a.toByte()
  }

  val bitmap = Bitmap()
  val imageInfo = ImageInfo(
    width = width,
    height = height,
    colorType = ColorType.RGBA_8888,
    alphaType = ColorAlphaType.UNPREMUL,
  )
  bitmap.allocPixels(imageInfo)
  bitmap.installPixels(rgbaBytes)

  return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
}
