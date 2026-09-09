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
package com.skydoves.landscapist.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap

/**
 * The colour every stubbed image is painted, so a frame can be asked what is in it. Counting
 * opaque pixels is not enough: a placeholder or a half faded crossfade is opaque too.
 */
internal const val STUB_COLOR: Int = 0xFF6750A4.toInt()

/** Composes, lays out and rasterizes offscreen. No window, no display, no frame clock. */
internal fun benchmarkScene(
  width: Int,
  height: Int,
  content: @Composable () -> Unit,
): ImageComposeScene = ImageComposeScene(
  width = width,
  height = height,
  density = Density(1f),
  coroutineContext = Dispatchers.Unconfined,
  content = content,
)

/** The share of one rendered frame that holds actual image pixels. */
internal fun ImageComposeScene.imagePixelFraction(nanoTime: Long = 0L): Double {
  val image = render(nanoTime)
  try {
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(image.width, image.height)
    check(image.readPixels(bitmap, 0, 0)) { "could not read the rendered frame" }
    val pixels = bitmap.readPixels() ?: error("no pixels")
    var matched = 0
    var i = 0
    // N32 is BGRA here, and full alpha is required so a half faded crossfade frame fails.
    val b = (STUB_COLOR and 0xFF).toByte()
    val g = (STUB_COLOR shr 8 and 0xFF).toByte()
    val r = (STUB_COLOR shr 16 and 0xFF).toByte()
    while (i + 3 < pixels.size) {
      if (pixels[i] == b && pixels[i + 1] == g && pixels[i + 2] == r &&
        pixels[i + 3] == 0xFF.toByte()
      ) {
        matched++
      }
      i += 4
    }
    return matched.toDouble() / (image.width * image.height)
  } finally {
    image.close()
  }
}

/** A percentage, for a table. */
internal fun Double.asPercent(): String = String.format(java.util.Locale.ROOT, "%.1f%%", this * 100)
