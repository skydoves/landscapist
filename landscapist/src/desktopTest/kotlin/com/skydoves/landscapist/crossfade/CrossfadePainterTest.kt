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
package com.skydoves.landscapist.crossfade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.InternalLandscapistApi
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [rememberCrossfadePainter], which is the crossfade an image gets when nothing has to be composed
 * inside it.
 *
 * The fade happens inside one painter rather than by stacking two composables, which is what lets a
 * crossfaded image keep the cheap drawing path. That leaves this painter owning three things the
 * stacked version got for free: not fading in what the viewer is already looking at, drawing the
 * outgoing image underneath so the composite never dips through transparent, and letting go of that
 * outgoing image once it is no longer being drawn.
 */
@OptIn(InternalLandscapistApi::class)
class CrossfadePainterTest {

  private val sceneSize = 16
  private val durationMs = 200

  /** A painter of one flat colour that records how many times it was asked to draw. */
  private class CountingPainter(private val colour: Color, private val side: Float) : Painter() {
    var draws = 0
      private set

    override val intrinsicSize: Size get() = Size(side, side)

    override fun DrawScope.onDraw() {
      draws++
      drawRect(colour, size = size)
    }
  }

  /** Renders frames of a scene whose painter can be changed between them. */
  private class Harness(sceneSize: Int, durationMs: Int, initial: Painter?) {
    var painter by mutableStateOf(initial)
    var resolved: Painter? = null
      private set

    private val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = {
        val faded = rememberCrossfadePainter(painter, durationMs)
        resolved = faded
        Box(Modifier.size(sceneSize.dp).drawBehind { faded?.run { draw(size) } })
      },
    )

    private val centre = ((sceneSize / 2) * sceneSize + sceneSize / 2) * 4

    /** The centre pixel of the frame rendered at [nanos], as ARGB. */
    fun render(nanos: Long): Int {
      val image = scene.render(nanos)
      try {
        val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
          bitmap.allocN32Pixels(image.width, image.height)
          check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
          bitmap.readPixels() ?: error("no pixels")
        }
        return (bytes[centre + 3].toInt() and 0xFF shl 24) or
          (bytes[centre + 2].toInt() and 0xFF shl 16) or
          (bytes[centre + 1].toInt() and 0xFF shl 8) or
          (bytes[centre].toInt() and 0xFF)
      } finally {
        image.close()
      }
    }

    fun switchTo(next: Painter?) {
      painter = next
      Snapshot.sendApplyNotifications()
    }

    fun close(): Unit = scene.close()
  }

  private fun <R> harness(initial: Painter?, body: (Harness) -> R): R {
    val harness = Harness(sceneSize, durationMs, initial)
    return try {
      body(harness)
    } finally {
      harness.close()
    }
  }

  private fun Int.red(): Int = this ushr 16 and 0xFF
  private fun Int.blue(): Int = this and 0xFF
  private fun Int.alpha(): Int = this ushr 24

  private fun red() = CountingPainter(Color.Red, sceneSize.toFloat())
  private fun blue() = CountingPainter(Color.Blue, sceneSize.toFloat())

  @Test
  fun `the painter an image enters with is handed back untouched`() {
    val first = red()

    val resolved = harness(first) { harness ->
      harness.render(0L)
      harness.resolved
    }

    // Not merely "draws opaquely": the point is that no wrapper is allocated at all for an image
    // read straight from the memory cache, which is the common case on a scrolled list.
    assertSame(first, resolved, "the entry painter was wrapped")
  }

  @Test
  fun `a replacement dissolves over what it replaces rather than over nothing`() {
    val frames = harness(red()) { harness ->
      harness.render(0L)
      harness.switchTo(blue())
      (1..12).map { frame -> harness.render(frame * 25L * 1_000_000) }
    }

    assertTrue(
      frames.all { it.alpha() == 0xFF },
      "the image dipped through transparent, the alphas were ${frames.map { it.alpha() }}",
    )
    assertTrue(
      frames.any { it.red() in 1..0xFE && it.blue() in 1..0xFE },
      "no frame held both images at once, the colours were " +
        frames.joinToString { it.toUInt().toString(16) },
    )
  }

  @Test
  fun `the replaced painter stops being drawn once the fade is over`() {
    // It is also released here: the field holding it is cleared on the same frame that stops
    // drawing it. Without that, a painter keeps the bitmap it replaced alive for as long as it is
    // on screen, which on a list that swaps images in place is a second full size bitmap per row.
    val outgoing = red()
    val settled = harness(outgoing) { harness ->
      harness.render(0L)
      harness.switchTo(blue())
      repeat(12) { frame -> harness.render(frame * 40L * 1_000_000) }
      val afterFade = outgoing.draws
      repeat(4) { frame -> harness.render((20 + frame) * 40L * 1_000_000) }
      afterFade to outgoing.draws
    }

    assertEquals(
      settled.first,
      settled.second,
      "the replaced painter was still being drawn after the fade finished",
    )
  }

  @Test
  fun `an image that left its success state does not come back underneath the next one`() {
    // A reload drops the painter to null while it resolves. Whatever was on screen has gone with
    // it, so it is no longer something to dissolve over: keeping it would draw it underneath the
    // arriving image, which reads as the replaced image flashing back after the gap.
    val frames = harness(red()) { harness ->
      harness.render(0L)
      harness.switchTo(null)
      harness.render(25L * 1_000_000)
      harness.switchTo(blue())
      (2..12).map { frame -> harness.render(frame * 25L * 1_000_000) }
    }

    // Red against blue rather than red against zero: the fade desaturates on its way in, so a
    // blue image part way through it has some red in it. What only the outgoing image produces is
    // red being the stronger of the two.
    assertTrue(
      frames.none { it.red() > it.blue() },
      "the replaced image was drawn again after the gap, the colours were " +
        frames.joinToString { it.toUInt().toString(16) },
    )
  }
}
