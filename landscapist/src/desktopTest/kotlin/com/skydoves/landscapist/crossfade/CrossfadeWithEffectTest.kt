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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.InternalLandscapistApi
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the crossfade does on the frame the composable first appears on. */
@OptIn(InternalLandscapistApi::class)
class CrossfadeWithEffectTest {

  private val size = 16
  private val durationMs = 200
  private val red = 0xFFFF0000.toInt()
  private val blue = 0xFF0000FF.toInt()

  /** Drives a scene whose crossfade target can be changed between frames. */
  private class Harness(enabled: Boolean, durationMs: Int, private val size: Int) {
    var target by mutableStateOf("red")

    private val scene = ImageComposeScene(
      width = size,
      height = size,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = {
        CrossfadeWithEffect(
          targetState = target,
          durationMs = durationMs,
          enabled = enabled,
        ) { state ->
          val color = if (state == "red") Color.Red else Color.Blue
          Box(Modifier.fillMaxSize().background(color))
        }
      },
    )

    /** The centre pixel of the frame rendered at [nanos], as ARGB. */
    fun renderCentre(nanos: Long): Int {
      val image = scene.render(nanos)
      try {
        val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
          bitmap.allocN32Pixels(image.width, image.height)
          check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
          bitmap.readPixels() ?: error("no pixels")
        }
        val offset = ((size / 2) * size + size / 2) * 4
        return (bytes[offset + 3].toInt() and 0xFF shl 24) or
          (bytes[offset + 2].toInt() and 0xFF shl 16) or
          (bytes[offset + 1].toInt() and 0xFF shl 8) or
          (bytes[offset].toInt() and 0xFF)
      } finally {
        image.close()
      }
    }

    fun switchTo(next: String) {
      target = next
      Snapshot.sendApplyNotifications()
    }

    fun close(): Unit = scene.close()
  }

  private fun <R> harness(enabled: Boolean = true, body: (Harness) -> R): R {
    val harness = Harness(enabled, durationMs, size)
    return try {
      body(harness)
    } finally {
      harness.close()
    }
  }

  private fun Int.alpha(): Int = this ushr 24

  private fun pastTheAnimation(extra: Long = 0L): Long =
    durationMs.toLong() * 4 * 1_000_000 + extra

  /** Renders frames until the animation runs out; it starts a frame after it was composed. */
  private fun Harness.renderSettled(from: Long): Int {
    var pixel = 0
    repeat(4) { frame -> pixel = renderCentre(from + pastTheAnimation() * (frame + 1)) }
    return pixel
  }

  @Test
  fun `the state a crossfade enters with is opaque on the very first frame`() {
    val pixel = harness { it.renderCentre(0L) }

    assertEquals(0xFF, pixel.alpha(), "the first frame was not fully drawn")
    assertEquals(red, pixel, "the first frame was not the entry state")
  }

  @Test
  fun `a disabled crossfade is also opaque on the very first frame`() {
    val pixel = harness(enabled = false) { it.renderCentre(0L) }

    assertEquals(red, pixel, "the first frame was not the entry state")
  }

  @Test
  fun `content that arrives later fades in rather than appearing at once`() {
    val (partial, settled) = harness { harness ->
      harness.renderCentre(0L)
      harness.switchTo("blue")
      // The alpha separates a fade from nothing drawn: an empty frame is not blue either.
      val alphas = (1..12).map { frame -> harness.renderCentre(frame * 25L * 1_000_000) ushr 24 }
      alphas to harness.renderSettled(1L)
    }

    assertTrue(
      partial.any { it in 1..0xFE },
      "no frame was part way through the fade, the alphas were $partial",
    )
    assertEquals(blue, settled, "the new content never reached full strength")
  }

  @Test
  fun `returning to the entry state fades in, because it is no longer what is on screen`() {
    val justAfter = harness { harness ->
      harness.renderCentre(0L)
      harness.switchTo("blue")
      harness.renderSettled(1L)
      harness.switchTo("red")
      harness.renderCentre(pastTheAnimation() * 5 + 1L)
    }

    // Not merely "different from red": a frame that drew nothing is different from red too.
    val alpha = justAfter ushr 24
    assertTrue(
      justAfter != red && alpha > 0,
      "coming back to the entry state either skipped the animation or drew nothing, read " +
        justAfter.toUInt().toString(16),
    )
  }

  @Test
  fun `turning the crossfade on does not fade a stale state back in`() {
    // Installing a crossfade plugin after the image resolves flips enabled while it is on screen.
    var target by mutableStateOf("red")
    var enabled by mutableStateOf(false)
    val scene = ImageComposeScene(
      width = size,
      height = size,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = {
        CrossfadeWithEffect(
          targetState = target,
          durationMs = durationMs,
          enabled = enabled,
          contentKey = { it },
        ) { state ->
          val color = if (state == "red") Color.Red else Color.Blue
          Box(Modifier.fillMaxSize().background(color))
        }
      },
    )
    val afterFlip = try {
      scene.render(0L).close()
      target = "blue"
      Snapshot.sendApplyNotifications()
      scene.render(1L).close()
      enabled = true
      Snapshot.sendApplyNotifications()
      val image = scene.render(2L)
      try {
        val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
          bitmap.allocN32Pixels(image.width, image.height)
          check(image.readPixels(bitmap, 0, 0))
          bitmap.readPixels() ?: error("no pixels")
        }
        val offset = ((size / 2) * size + size / 2) * 4
        (bytes[offset + 3].toInt() and 0xFF shl 24) or
          (bytes[offset + 2].toInt() and 0xFF shl 16) or
          (bytes[offset + 1].toInt() and 0xFF shl 8) or
          (bytes[offset].toInt() and 0xFF)
      } finally {
        image.close()
      }
    } finally {
      scene.close()
    }

    assertEquals(blue, afterFlip, "turning the crossfade on brought an older state back")
  }

  @Test
  fun `a disabled crossfade keeps every sibling its content emits`() {
    // The content lambda emits several children and the disabled path adds no wrapper of its own.
    val boxSize = size

    // How the image composables call it: minimum constraints forwarded, so every sibling fills.
    val filled = renderAll {
      Box(Modifier.size(boxSize.dp), propagateMinConstraints = true) {
        CrossfadeWithEffect(targetState = "red", durationMs = durationMs, enabled = false) {
          Box(Modifier.fillMaxSize().background(Color.Red))
          Box(Modifier.fillMaxSize().background(Color.Blue))
        }
      }
    }
    assertTrue(filled.all { it == blue }, "the siblings were not stacked in the order emitted")

    // Without that forwarding, a sibling keeps the size it asked for and sits at the top left.
    val stacked = renderAll {
      Box(Modifier.size(boxSize.dp)) {
        CrossfadeWithEffect(targetState = "red", durationMs = durationMs, enabled = false) {
          Box(Modifier.fillMaxSize().background(Color.Red))
          Box(Modifier.size((boxSize / 2).dp).background(Color.Blue))
        }
      }
    }
    assertEquals(blue, stacked[0], "the second sibling was not drawn on top")
    assertEquals(
      red,
      stacked[(boxSize - 1) * boxSize + (boxSize - 1)],
      "the first sibling did not keep the rest of the box",
    )
  }

  /** Renders one frame of [content] and returns every pixel as ARGB, row major. */
  private fun renderAll(content: @Composable () -> Unit): IntArray {
    val scene = ImageComposeScene(
      width = size,
      height = size,
      density = Density(1f),
      coroutineContext = Dispatchers.Unconfined,
      content = content,
    )
    try {
      val image = scene.render(0L)
      try {
        val bytes = org.jetbrains.skia.Bitmap().use { bitmap ->
          bitmap.allocN32Pixels(image.width, image.height)
          check(image.readPixels(bitmap, 0, 0)) { "could not read the frame back" }
          bitmap.readPixels() ?: error("no pixels")
        }
        return IntArray(size * size) { index ->
          val offset = index * 4
          (bytes[offset + 3].toInt() and 0xFF shl 24) or
            (bytes[offset + 2].toInt() and 0xFF shl 16) or
            (bytes[offset + 1].toInt() and 0xFF shl 8) or
            (bytes[offset].toInt() and 0xFF)
        }
      } finally {
        image.close()
      }
    } finally {
      scene.close()
    }
  }
}
