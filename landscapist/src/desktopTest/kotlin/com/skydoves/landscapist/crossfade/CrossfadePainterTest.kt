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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.InternalLandscapistApi
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [rememberCrossfadePainter]: the crossfade an image gets with nothing composed inside it. */
@OptIn(InternalLandscapistApi::class)
class CrossfadePainterTest {

  private val sceneSize = 16
  private val durationMs = 200

  /** A painter of one flat colour that records how many times it was asked to draw. */
  private class CountingPainter(
    private val colour: Color,
    private val width: Float,
    private val height: Float = width,
  ) : Painter() {
    var draws = 0
      private set

    override val intrinsicSize: Size get() = Size(width, height)

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

    private val side = sceneSize

    /** The centre pixel of the frame rendered at [nanos], as ARGB. */
    fun render(nanos: Long): Int = renderAt(nanos, side / 2, side / 2)

    /** The pixel at [x], [y] of the frame rendered at [nanos], as ARGB. */
    fun renderAt(nanos: Long, x: Int, y: Int): Int {
      val image = scene.render(nanos)
      try {
        val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
          bitmap.allocN32Pixels(image.width, image.height)
          check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
          bitmap.readPixels() ?: error("no pixels")
        }
        val at = (y * side + x) * 4
        return (bytes[at + 3].toInt() and 0xFF shl 24) or
          (bytes[at + 2].toInt() and 0xFF shl 16) or
          (bytes[at + 1].toInt() and 0xFF shl 8) or
          (bytes[at].toInt() and 0xFF)
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

  /** Draws [painter] once outside the scene, which will not repeat a draw nothing invalidated. */
  private fun drawOnce(painter: Painter) {
    val bitmap = ImageBitmap(sceneSize, sceneSize)
    val size = Size(sceneSize.toFloat(), sceneSize.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
      with(painter) { draw(size) }
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

    // assertSame, not "draws opaquely": no wrapper is allocated at all for a cached image.
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
    // The field holding it is cleared on the same frame, so the replaced bitmap is released.
    val outgoing = red()
    val (faded, afterFade) = harness(outgoing) { harness ->
      harness.render(0L)
      harness.switchTo(blue())
      repeat(12) { frame -> harness.render(frame * 40L * 1_000_000) }
      checkNotNull(harness.resolved) { "nothing was resolved to draw" } to outgoing.draws
    }

    // Asking the painter itself, not the scene. Once the animation ends nothing invalidates, so
    // the scene will not run the draw again and the counter cannot move whatever the painter holds.
    repeat(4) { drawOnce(faded) }

    assertEquals(
      afterFade,
      outgoing.draws,
      "the replaced painter was still being drawn after the fade finished",
    )
  }

  @Test
  fun `the arriving image is faded in rather than swapped in`() {
    val frames = harness(red()) { harness ->
      harness.render(0L)
      harness.switchTo(blue())
      (1..4).map { frame -> harness.render(frame * 10L * 1_000_000) }
    }

    // Early in the fade the viewer is still mostly looking at what was there. With no opacity
    // ramp the arriving blue covers it on the first frame, and the only red left is what
    // desaturating blue produces, which is a fraction of this.
    assertTrue(
      frames.first().red() > 0x80,
      "the replacement appeared at once, the frames were " +
        frames.joinToString { it.toUInt().toString(16) },
    )
  }

  @Test
  fun `the outgoing image covers the box the arriving one is measured for`() {
    // A wide image being replaced by a square one. Scaled to fit it would band across the middle
    // and leave the top and bottom of the box to the arriving image alone, which is barely there
    // yet, so the fade would show through to nothing at the edges.
    val outgoing = CountingPainter(Color.Red, sceneSize * 2f, sceneSize / 2f)
    val corner = harness(outgoing) { harness ->
      harness.render(0L)
      harness.switchTo(blue())
      harness.renderAt(10L * 1_000_000, 1, 1)
    }

    assertEquals(
      0xFF,
      corner.alpha(),
      "the top of the box was not covered while the replacement faded in, it was " +
        corner.toUInt().toString(16),
    )
    assertTrue(corner.red() > 0x80, "the corner was not the outgoing image")
  }

  @Test
  fun `a duration of zero hands the painter back untouched`() {
    // On a replacement, which is the only place a fade would otherwise be built. The painter an
    // image enters with is handed back whatever the duration, so it proves nothing about this.
    val replacement = blue()
    var painter by mutableStateOf<Painter?>(red())
    var resolved: Painter? = null
    val scene = ImageComposeScene(
      width = sceneSize,
      height = sceneSize,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = { resolved = rememberCrossfadePainter(painter, durationMs = 0) },
    )
    try {
      scene.render(0L).close()
      painter = replacement
      Snapshot.sendApplyNotifications()
      scene.render(10L * 1_000_000).close()
    } finally {
      scene.close()
    }

    assertSame(replacement, resolved, "a zero duration still wrapped the replacement")
  }

  @Test
  fun `the replaced painter is released once the fade is over`() {
    // The draw counter cannot see this: the settled branch returns before drawing the outgoing
    // whether or not the field was cleared, so what is left to observe is reachability.
    var outgoing: Painter? = red()
    val weak = java.lang.ref.WeakReference(outgoing)
    val faded = harness(outgoing) { harness ->
      harness.render(0L)
      harness.switchTo(blue())
      repeat(12) { frame -> harness.render(frame * 40L * 1_000_000) }
      checkNotNull(harness.resolved)
    }
    outgoing = null
    // The settled branch drops the field on its next draw, so give it one outside the scene.
    drawOnce(faded)

    var collected = false
    repeat(20) {
      if (weak.get() == null) {
        collected = true
        return@repeat
      }
      System.gc()
      Thread.sleep(20)
    }
    assertTrue(collected, "the replaced painter was still reachable after the fade finished")
  }

  @Test
  fun `an image that left its success state does not come back underneath the next one`() {
    // A reload drops the painter to null, and what was on screen goes with it.
    val frames = harness(red()) { harness ->
      harness.render(0L)
      harness.switchTo(null)
      harness.render(25L * 1_000_000)
      harness.switchTo(blue())
      (2..12).map { frame -> harness.render(frame * 25L * 1_000_000) }
    }

    // Red against blue, not against zero: a blue frame part way through the fade has red in it.
    assertTrue(
      frames.none { it.red() > it.blue() },
      "the replaced image was drawn again after the gap, the colours were " +
        frames.joinToString { it.toUInt().toString(16) },
    )
  }
}
