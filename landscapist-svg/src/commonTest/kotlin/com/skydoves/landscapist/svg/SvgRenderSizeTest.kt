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

import kotlin.test.Test
import kotlin.test.assertEquals

class SvgRenderSizeTest {

  private fun size(
    intrinsicWidth: Int? = 48,
    intrinsicHeight: Int? = 24,
    targetWidth: Int? = null,
    targetHeight: Int? = null,
    maxSize: Int = 4096,
  ) = svgRenderSize(intrinsicWidth, intrinsicHeight, targetWidth, targetHeight, maxSize)

  @Test
  fun `the measured size wins`() {
    assertEquals(SvgRenderSize(200, 100), size(targetWidth = 200, targetHeight = 100))
  }

  @Test
  fun `one bound derives the other from the aspect ratio`() {
    assertEquals(SvgRenderSize(200, 100), size(targetWidth = 200))
    assertEquals(SvgRenderSize(200, 100), size(targetHeight = 100))
  }

  @Test
  fun `an unbounded dimension is not a bound`() {
    // Int.MAX_VALUE is how an unbounded layout dimension reaches the decoder.
    assertEquals(SvgRenderSize(200, 100), size(targetWidth = 200, targetHeight = Int.MAX_VALUE))
  }

  @Test
  fun `no target falls back to the declared size`() {
    assertEquals(SvgRenderSize(48, 24), size())
  }

  @Test
  fun `a document that declares nothing gets a default`() {
    assertEquals(
      SvgRenderSize(DEFAULT_SVG_SIZE, DEFAULT_SVG_SIZE),
      size(intrinsicWidth = null, intrinsicHeight = null),
    )
  }

  @Test
  fun `an unusable declared size does not drive the aspect ratio`() {
    assertEquals(SvgRenderSize(300, DEFAULT_SVG_SIZE), size(intrinsicHeight = 0, targetWidth = 300))
  }

  @Test
  fun `everything is clamped to the maximum bitmap size`() {
    val clamped = size(targetWidth = 10_000, targetHeight = 10_000, maxSize = 1024)

    assertEquals(SvgRenderSize(1024, 1024), clamped)
  }

  @Test
  fun `a derived size never rounds down to zero`() {
    // 1px wide against a 4000:1 document derives a height of 0.00025.
    val derived = size(intrinsicWidth = 4000, intrinsicHeight = 1, targetWidth = 1)

    assertEquals(SvgRenderSize(1, 1), derived)
  }
}
