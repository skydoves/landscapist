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
package com.github.skydoves.landscapistdemo.plugins

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import kotlin.math.abs

internal const val PluginImageTag: String = "landscapistPluginImage"

internal const val PluginRequestPx: Int = 240

internal const val PngContentType: String = "image/png"

internal const val FrameMs: Long = 16L

/** Pure green, and no fixture has any green in it, so an unpainted pixel reads as backdrop. */
internal val Backdrop: Color = Color(0xFF00FF00)

internal val RedFixture: Color = Color(0xFFFF0000)

internal val BlueFixture: Color = Color(0xFF0000FF)

internal val ShimmerFixture: Color = Color(0xFFFF00FF)

internal val PluginImageSize = 96.dp

/** The size is set explicitly, so a warm up writes the key the composable reads back. */
internal val PluginRequestBuilder: ImageRequest.Builder.() -> Unit = {
  diskCachePolicy(CachePolicy.DISABLED)
  size(PluginRequestPx, PluginRequestPx)
}

/** One per test, so each starts from an empty memory cache. */
internal fun visualPluginLoader(): Landscapist = Landscapist.builder().noDiskCache().build()

internal fun visualImageModifier(): Modifier = Modifier
  .size(PluginImageSize)
  .testTag(PluginImageTag)

/** PNG, not JPEG: lossless, and a JPEG would decode to an unreadable hardware bitmap. */
internal fun solidPng(color: Color): ByteArray = ImageFixtures.solid(
  width = PluginRequestPx,
  height = PluginRequestPx,
  color = color.toArgb(),
  format = Bitmap.CompressFormat.PNG,
)

internal fun quadrantPng(): ByteArray = ImageFixtures.photo(
  width = PluginRequestPx,
  height = PluginRequestPx,
  format = Bitmap.CompressFormat.PNG,
)

/** Loads [url] into the memory cache before the composable exists. */
internal fun Landscapist.warm(url: String) {
  val result = runBlocking {
    load(ImageRequest.builder().model(url).apply(PluginRequestBuilder).build())
      .first { it is ImageResult.Success || it is ImageResult.Failure }
  }
  assertTrue("could not warm $url into the memory cache: $result", result is ImageResult.Success)
}

@Composable
internal fun OnBackdrop(content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Backdrop),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** Auto advance is off, since a shimmer animates forever, so the test hands out the frames. */
internal fun ComposeTestRule.advanceUntil(
  what: String,
  timeoutMs: Long = 15_000,
  condition: () -> Boolean,
) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (!condition()) {
    assertTrue(
      "timed out after ${timeoutMs}ms waiting for $what",
      System.currentTimeMillis() < deadline,
    )
    mainClock.advanceTimeBy(FrameMs)
    Thread.sleep(2)
  }
}

internal fun ComposeTestRule.readVisualPixels(): PixelMap =
  onNodeWithTag(PluginImageTag).captureToImage().toPixelMap()

/** Every [step]th pixel, inset by one step to skip the anti-aliased edge. */
internal fun PixelMap.samples(step: Int = 4): List<Color> = buildList {
  var y = step
  while (y < height - step) {
    var x = step
    while (x < width - step) {
      add(this@samples[x, y])
      x += step
    }
    y += step
  }
}

internal fun PixelMap.centre(): Color = this[width / 2, height / 2]

/** Near the top left, which a centred reveal reaches last. */
internal fun PixelMap.corner(): Color = this[4, 4]

/** The backdrop's own colour, so a node painted anything else is not read as unpainted. */
internal fun Color.isBackdrop(): Boolean = matches(Backdrop)

/** The fraction of the node painted [colour], not merely painted something. */
internal fun PixelMap.coveredBy(colour: Color, tolerance: Float = 0.06f): Float {
  val pixels = samples()
  return pixels.count { it.matches(colour, tolerance) }.toFloat() / pixels.size
}

/** Part way between the red and the blue fixture: a blank frame carries neither colour. */
internal fun Color.isRedBlueDissolve(): Boolean =
  !matches(RedFixture) && !matches(BlueFixture) && red > 0.15f && blue > 0.15f

internal fun Color.matches(other: Color, tolerance: Float = 0.06f): Boolean =
  abs(red - other.red) <= tolerance &&
    abs(green - other.green) <= tolerance &&
    abs(blue - other.blue) <= tolerance

internal fun List<Color>.notMatching(colour: Color, tolerance: Float = 0.06f): List<Color> =
  filterNot { it.matches(colour, tolerance) }
