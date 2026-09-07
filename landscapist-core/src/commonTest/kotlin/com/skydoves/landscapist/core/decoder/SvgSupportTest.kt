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
package com.skydoves.landscapist.core.decoder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgSupportTest {

  private fun svg(markup: String) = markup.encodeToByteArray()

  @Test
  fun `a bare svg document is detected`() {
    val bare = svg("<svg xmlns=\"http://www.w3.org/2000/svg\"/>")

    assertTrue(isSvg(bare, null))
  }

  @Test
  fun `an xml declaration and a doctype are skipped`() {
    val markup = """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "svg11.dtd">
      <!-- a comment -->
      <svg width="24" height="24"></svg>
    """.trimIndent()

    assertTrue(isSvg(svg(markup), null))
  }

  @Test
  fun `a byte order mark is skipped`() {
    val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + svg("<svg/>")

    assertTrue(isSvg(bytes, null))
  }

  @Test
  fun `an uppercase tag is detected`() {
    assertTrue(isSvg(svg("<SVG width=\"1\" height=\"1\"/>"), null))
  }

  @Test
  fun `the mime type alone is enough`() {
    assertTrue(isSvg(byteArrayOf(1, 2, 3), "image/svg+xml"))
    assertTrue(isSvg(byteArrayOf(1, 2, 3), "image/svg+xml; charset=utf-8"))
  }

  @Test
  fun `a mislabelled svg is still detected from its content`() {
    val mislabelled = svg("<svg viewBox=\"0 0 2 1\"/>")

    assertTrue(isSvg(mislabelled, "application/octet-stream"))
  }

  @Test
  fun `raster bytes are not svg`() {
    val png = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
      0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
      0, 0, 0, 8, 0, 0, 0, 8,
    )

    assertFalse(isSvg(png, "image/png"))
  }

  @Test
  fun `markup that is not svg is not detected`() {
    assertFalse(isSvg(svg("<svgfoo/>"), null))
    assertFalse(isSvg(ByteArray(0), null))
  }

  @Test
  fun `a root tag pushed past the prologue window is not detected`() {
    val padding = " ".repeat(2000)
    val padded = svg("<!--" + padding + "-->\n<svg/>")

    assertFalse(isSvg(padded, null))
  }

  @Test
  fun `an html page that embeds an svg is not an image`() {
    assertFalse(isSvg(svg("<html><body><svg/></body></html>"), null))
    assertFalse(isSvg(svg("<!DOCTYPE html>\n<html><svg/></html>"), null))
  }

  private fun sizeOf(markup: String) = readSvgDimensions(svg(markup))

  @Test
  fun `width and height attributes win over the view box`() {
    val size = sizeOf("<svg width=\"120\" height=\"60\" viewBox=\"0 0 24 24\"/>")

    assertEquals(ImageSize(120, 60), size)
  }

  @Test
  fun `units on width and height are ignored`() {
    assertEquals(ImageSize(64, 32), sizeOf("<svg width=\"64px\" height=\"32px\"/>"))
    assertEquals(ImageSize(10, 20), sizeOf("<svg width=\"10.4pt\" height=\"20pt\"/>"))
  }

  @Test
  fun `a relative width falls back to the view box`() {
    val size = sizeOf("<svg width=\"100%\" height=\"100%\" viewBox=\"0 0 800 400\"/>")

    assertEquals(ImageSize(800, 400), size)
  }

  @Test
  fun `a comma separated view box is read`() {
    assertEquals(ImageSize(16, 9), sizeOf("<svg viewBox=\"0,0,16,9\"/>"))
  }

  @Test
  fun `stroke-width is not mistaken for width`() {
    val size = sizeOf("<svg stroke-width=\"4\" viewBox=\"0 0 30 10\"/>")

    assertEquals(ImageSize(30, 10), size)
  }

  @Test
  fun `an attribute holding an angle bracket does not end the tag early`() {
    val size = sizeOf("<svg data-note=\"a > b\" width=\"12\" height=\"8\"/>")

    assertEquals(ImageSize(12, 8), size)
  }

  @Test
  fun `a zero width falls back to the view box`() {
    assertEquals(ImageSize(24, 24), sizeOf("<svg width=\"0\" height=\"0\" viewBox=\"0 0 24 24\"/>"))
  }

  @Test
  fun `a document with no declared size has none`() {
    assertNull(sizeOf("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"))
    assertNull(sizeOf("<svg width=\"0\" height=\"0\"/>"))
    assertNull(sizeOf("<svg"))
  }
}
