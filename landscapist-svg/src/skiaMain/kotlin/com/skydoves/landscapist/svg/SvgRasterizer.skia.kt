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
package com.skydoves.landscapist.svg

import com.skydoves.landscapist.core.decoder.DecodeResult
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength
import org.jetbrains.skia.svg.SVGLengthUnit
import org.jetbrains.skia.svg.SVGPreserveAspectRatio
import org.jetbrains.skia.svg.SVGPreserveAspectRatioAlign
import org.jetbrains.skia.svg.SVGPreserveAspectRatioScale

/**
 * Renders with Skia's own SVG support, which every skia target already has through skiko.
 *
 * The root is resized to the requested box. A document with a view box scales into it; one without
 * gets its own size adopted as the view box first, so it scales rather than being cropped.
 */
internal actual fun rasterizeSvg(data: ByteArray, width: Int, height: Int): DecodeResult = try {
  val dom = SVGDOM(Data.makeFromBytes(data))
  val root = requireNotNull(dom.root) { "the SVG has no root element" }

  if (root.viewBox == null) {
    val intrinsicWidth = root.width.value.takeIf { it > 0f } ?: width.toFloat()
    val intrinsicHeight = root.height.value.takeIf { it > 0f } ?: height.toFloat()
    root.viewBox = org.jetbrains.skia.Rect.makeWH(intrinsicWidth, intrinsicHeight)
  }
  root.width = SVGLength(width.toFloat(), SVGLengthUnit.PX)
  root.height = SVGLength(height.toFloat(), SVGLengthUnit.PX)
  root.preserveAspectRatio = SVGPreserveAspectRatio(
    align = SVGPreserveAspectRatioAlign.XMID_YMID,
    scale = SVGPreserveAspectRatioScale.MEET,
  )

  val bitmap = Bitmap()
  bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
  dom.setContainerSize(width.toFloat(), height.toFloat())
  dom.render(Canvas(bitmap))
  bitmap.setImmutable()

  DecodeResult.Success(bitmap = bitmap, width = width, height = height)
} catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
  DecodeResult.Error(throwable)
}
