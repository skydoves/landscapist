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

import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.skydoves.landscapist.core.decoder.DecodeResult

/**
 * Renders with AndroidSVG, which is what Android needs since the platform has no SVG support of
 * its own.
 *
 * The document is stretched to the requested box by clearing its own width and height and giving
 * it a view box, so the result matches the size the composable was measured at. A document that
 * declares neither is rendered at the requested size as is.
 */
internal actual fun rasterizeSvg(data: ByteArray, width: Int, height: Int): DecodeResult = try {
  val svg = SVG.getFromInputStream(data.inputStream())

  if (svg.documentViewBox == null) {
    // Without a view box there is nothing to scale against, so adopt the document's own size.
    svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
  }
  svg.setDocumentWidth("100%")
  svg.setDocumentHeight("100%")

  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val viewPort = RenderOptions.create().viewPort(0f, 0f, width.toFloat(), height.toFloat())
  svg.renderToCanvas(Canvas(bitmap), viewPort)

  DecodeResult.Success(bitmap = bitmap, width = width, height = height)
} catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
  DecodeResult.Error(throwable)
}
