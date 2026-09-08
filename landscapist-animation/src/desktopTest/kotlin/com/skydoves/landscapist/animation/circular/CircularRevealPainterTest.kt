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
package com.skydoves.landscapist.animation.circular

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rasterizes the painter and reads the pixels back.
 *
 * `Image` sizes a painter with its `contentScale` and then asks it to fill exactly that size, so a
 * painter that rescales the image again inside `onDraw` overrides whatever the caller asked for.
 * That is issue #431: with a `CircularRevealPlugin` every content scale rendered as `Crop`.
 */
class CircularRevealPainterTest {

  private val red = Color(0xFFFF0000)
  private val green = Color(0xFF00FF00)
  private val blue = Color(0xFF0000FF)

  /** A 40x20 image whose first column is red, last column blue, everything between green. */
  private val stripes: ImageBitmap = renderToBitmap(40, 20) {
    drawRect(color = green, topLeft = androidx.compose.ui.geometry.Offset.Zero, size = size)
    drawRect(
      color = red,
      topLeft = androidx.compose.ui.geometry.Offset.Zero,
      size = Size(1f, size.height),
    )
    drawRect(
      color = blue,
      topLeft = androidx.compose.ui.geometry.Offset(size.width - 1f, 0f),
      size = Size(1f, size.height),
    )
  }

  private fun renderToBitmap(width: Int, height: Int, block: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(bitmap),
      size = Size(width.toFloat(), height.toFloat()),
      block = block,
    )
    return bitmap
  }

  /** Draws [painter] at [radius] into a [width] by [height] box, the way `Image` would. */
  private fun revealTo(
    painter: Painter,
    width: Int,
    height: Int,
    radius: Float = 1f,
  ): ImageBitmap {
    val reveal = CircularRevealPainter(painter = painter).apply { this.radius = radius }
    return renderToBitmap(width, height) { with(reveal) { draw(size) } }
  }

  private fun ImageBitmap.at(x: Int, y: Int): Color = toPixelMap()[x, y]

  private fun assertHue(expected: Color, actual: Color, message: String) {
    assertEquals(expected.red, actual.red, 0.06f, "$message (red channel)")
    assertEquals(expected.green, actual.green, 0.06f, "$message (green channel)")
    assertEquals(expected.blue, actual.blue, 0.06f, "$message (blue channel)")
    assertEquals(1f, actual.alpha, 0.06f, "$message (alpha)")
  }

  @Test
  fun `the painter fills the size it is given instead of rescaling the image itself`() {
    // Image works out the draw size from its contentScale and then asks the painter to fill exactly
    // that. A 40x20 image filling a 100x100 box is what FillBounds produces; rescaling inside the
    // painter instead centres a uniformly scaled copy and cuts both ends off, which is issue #431.
    val rendered = revealTo(BitmapPainter(stripes), width = 100, height = 100)

    assertHue(red, rendered.at(0, 50), "the left edge of the image is missing")
    assertHue(blue, rendered.at(99, 50), "the right edge of the image is missing")
    assertHue(green, rendered.at(50, 50), "the middle of the image is wrong")
  }

  @Test
  fun `the image reaches the very edge of the box`() {
    // The old implementation nudged its own draw by half a pixel, which left the outermost column
    // half transparent. Nothing should be inset now.
    val rendered = revealTo(BitmapPainter(stripes), width = 200, height = 100)

    assertHue(red, rendered.at(0, 50), "the left edge of the image is inset or missing")
    assertHue(blue, rendered.at(199, 50), "the right edge of the image is inset or missing")
  }

  @Test
  fun `a translucent image keeps its transparency inside the reveal`() {
    // The mask must composite with SrcIn. SrcAtop looks identical for opaque content but bleeds the
    // black mask through wherever the image is translucent.
    val halfOpaque = object : Painter() {
      override val intrinsicSize: Size = Size(40f, 20f)
      override fun DrawScope.onDraw() {
        drawRect(color = Color(0x80FFFFFF), size = size)
      }
    }

    val rendered = revealTo(halfOpaque, width = 100, height = 100)

    val pixel = rendered.at(50, 50)
    assertEquals(0.5f, pixel.alpha, 0.02f, "the image's own transparency was not preserved")
    assertEquals(1f, pixel.red, 0.02f, "the mask bled through a translucent image")
    assertEquals(1f, pixel.green, 0.02f, "the mask bled through a translucent image")
    assertEquals(1f, pixel.blue, 0.02f, "the mask bled through a translucent image")
  }

  @Test
  fun `the reveal is measured against the longest side of the box`() {
    // Both boxes are 200 on their long side and 100 on their short one, so a radius of 0.52 reaches
    // 104px against the long side and only 52px against the short one. The sampled points sit 98px
    // from the centre, inside the first and outside the second, and the corners are 110px out and
    // stay outside either way. Testing both orientations pins the long side specifically, rather
    // than width or height.
    val wide = revealTo(BitmapPainter(stripes), width = 200, height = 100, radius = 0.52f)
    val tall = revealTo(BitmapPainter(stripes), width = 100, height = 200, radius = 0.52f)

    assertTrue(wide.at(2, 50).alpha > 0.9f, "the long edge should be inside the reveal")
    assertTrue(tall.at(50, 2).alpha > 0.9f, "the long edge should be inside the reveal")
    assertTrue(wide.at(1, 1).alpha < 0.1f, "the corner should still be outside the reveal")
    assertTrue(tall.at(1, 1).alpha < 0.1f, "the corner should still be outside the reveal")
  }

  @Test
  fun `the wrapped painter is what gets drawn`() {
    // Painter plugins compose onto each other, so a reveal placed after another plugin has to draw
    // that plugin's painter rather than the original bitmap.
    val yellow = Color(0xFFFFFF00)
    val previousPlugin = object : Painter() {
      override val intrinsicSize: Size = Size(40f, 20f)
      override fun DrawScope.onDraw() {
        drawRect(color = yellow, size = size)
      }
    }

    val rendered = revealTo(previousPlugin, width = 100, height = 100)

    assertHue(yellow, rendered.at(50, 50), "the wrapped painter was ignored")
  }

  @Test
  fun `the reveal clips to a growing circle`() {
    val half = revealTo(BitmapPainter(stripes), width = 100, height = 100, radius = 0.5f)

    // Radius 0.5 of the longest side is 50px out, so the centre is in and the corner is not.
    assertTrue(half.at(50, 50).alpha > 0.9f, "the centre of the reveal should be drawn")
    assertTrue(half.at(2, 2).alpha < 0.1f, "the corner should be outside the reveal")
  }

  @Test
  fun `nothing is drawn before the reveal starts`() {
    val none = revealTo(BitmapPainter(stripes), width = 100, height = 100, radius = 0f)

    assertTrue(none.at(50, 50).alpha < 0.1f, "nothing should be drawn at radius zero")
  }

  @Test
  fun `a finished reveal covers the corners`() {
    val full = revealTo(BitmapPainter(stripes), width = 100, height = 100, radius = 1f)

    assertTrue(full.at(0, 0).alpha > 0.9f, "a finished reveal should cover the whole box")
    assertTrue(full.at(99, 99).alpha > 0.9f, "a finished reveal should cover the whole box")
  }

  @Test
  fun `the intrinsic size is the wrapped painter's`() {
    val reveal = CircularRevealPainter(painter = BitmapPainter(stripes))

    assertEquals(Size(40f, 20f), reveal.intrinsicSize)
  }
}
