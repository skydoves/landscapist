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
package com.skydoves.landscapist.coil3

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Constraints
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import coil3.size.Size
import com.skydoves.landscapist.ImageOptions
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestWithConstraintsTest {

  private val context = PlatformContext.INSTANCE

  private fun request(scale: Scale? = null): ImageRequest =
    ImageRequest.Builder(context)
      .data("https://example.com/image.png")
      .apply { if (scale != null) scale(scale) }
      .build()

  private fun scaleFor(contentScale: ContentScale): Scale =
    request().withConstraints(ImageOptions(contentScale = contentScale)).scale

  @Test
  fun `fit and inside decode to fit inside the bounds`() {
    assertEquals(Scale.FIT, scaleFor(ContentScale.Fit))
    assertEquals(Scale.FIT, scaleFor(ContentScale.Inside))
  }

  @Test
  fun `crop and the fill content scales decode to fill the bounds`() {
    listOf(
      ContentScale.Crop,
      ContentScale.FillBounds,
      ContentScale.FillWidth,
      ContentScale.FillHeight,
    ).forEach { assertEquals(Scale.FILL, scaleFor(it), "$it") }
  }

  @Test
  fun `a scale set on the request is kept`() {
    val request = request(scale = Scale.FIT)
      .withConstraints(ImageOptions(contentScale = ContentScale.Crop))

    assertEquals(Scale.FIT, request.scale)
  }

  @Test
  fun `content scale none still requests the original size`() {
    val request = request().withConstraints(ImageOptions(contentScale = ContentScale.None))

    assertEquals(Size.ORIGINAL, runBlocking { request.sizeResolver.size() })
  }

  /**
   * A 400x300 image shown with [ContentScale.Crop] in a 132x132 box. Decoded with Coil's default
   * [Scale.FIT] it came out 132x99 and was then stretched to cover the box.
   */
  @Test
  fun `a crop image is decoded to cover the bounds`() {
    val request = ImageRequest.Builder(context)
      .data(png(width = 400, height = 300))
      .build()
      .withConstraints(ImageOptions(contentScale = ContentScale.Crop))
    (request.sizeResolver as ConstraintsSizeResolver).setConstraints(Constraints.fixed(132, 132))

    val image = runBlocking { (ImageLoader(context).execute(request) as SuccessResult).image }

    assertEquals(176 to 132, image.width to image.height)
  }

  private fun png(width: Int, height: Int): ByteArray {
    val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
    return Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
  }
}
