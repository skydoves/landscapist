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

/**
 * What the plugin tests share: a real image on a real screen, read back a pixel at a time.
 *
 * A plugin test that only asks whether the composable exists passes on a blank screen, so every
 * assertion here is made against what was actually rasterised. Two things make that possible on a
 * device. The image sits on a [Backdrop] of a colour no fixture contains, because a capture reads
 * the window and has no alpha channel of its own: what the image failed to paint comes back as the
 * backdrop rather than as transparency. And the test drives the frame clock itself, because these
 * plugins animate, one of them forever.
 */

/** The tag every image under test carries, so a capture knows which node to read back. */
internal const val PluginImageTag: String = "landscapistPluginImage"

/** The pixel size every request is decoded at. */
internal const val PluginRequestPx: Int = 240

/** The content type the fixtures are served as. */
internal const val PngContentType: String = "image/png"

/** One frame of the test clock. */
internal const val FrameMs: Long = 16L

/**
 * What is drawn behind the image under test.
 *
 * Pure green, and no fixture is made of a colour with any green in it, so the green channel of a
 * pixel is a measure of how much of the image is missing there.
 */
internal val Backdrop: Color = Color(0xFF00FF00)

/** The colour of the red fixture, as it must read back off the screen. */
internal val RedFixture: Color = Color(0xFFFF0000)

/** The colour of the blue fixture, as it must read back off the screen. */
internal val BlueFixture: Color = Color(0xFF0000FF)

/** The colour a shimmer is configured with, so what it drew is recognisable on screen. */
internal val ShimmerFixture: Color = Color(0xFFFF00FF)

/** The side of the image under test. */
internal val PluginImageSize = 96.dp

/**
 * The request every image under test is loaded with.
 *
 * The target size is set rather than left to the layout, so the key a warm up writes into the
 * memory cache is the same one the composable reads back out of it, and the image is drawn in the
 * frame the composable first appears in rather than a frame later.
 */
internal val PluginRequestBuilder: ImageRequest.Builder.() -> Unit = {
  diskCachePolicy(CachePolicy.DISABLED)
  size(PluginRequestPx, PluginRequestPx)
}

/** A loader that keeps nothing on disk, so every test starts from an empty memory cache. */
internal fun pluginLoader(): Landscapist = Landscapist.builder().noDiskCache().build()

/** The modifier every image under test carries. */
internal fun pluginImageModifier(): Modifier = Modifier
  .size(PluginImageSize)
  .testTag(PluginImageTag)

/**
 * A solid [color] image, encoded losslessly.
 *
 * PNG rather than JPEG for two reasons. Lossless means an assertion can name the exact colour the
 * fixture was made with instead of a range around it. And the decoder only reaches for a hardware
 * bitmap on a format that cannot carry alpha, so a JPEG here would hand the palette plugin pixels
 * that live in GPU memory and cannot be read back.
 */
internal fun solidPng(color: Color): ByteArray = ImageFixtures.solid(
  width = PluginRequestPx,
  height = PluginRequestPx,
  color = color.toArgb(),
  format = Bitmap.CompressFormat.PNG,
)

/** The four quadrant fixture, encoded losslessly, for a test whose image needs detail in it. */
internal fun quadrantPng(): ByteArray = ImageFixtures.photo(
  width = PluginRequestPx,
  height = PluginRequestPx,
  format = Bitmap.CompressFormat.PNG,
)

/**
 * Loads [url] into this loader before the composable exists.
 *
 * The image is then in the memory cache, which is what lets a test say what the very first frame
 * of the composable must look like.
 */
internal fun Landscapist.warm(url: String) {
  val result = runBlocking {
    load(ImageRequest.builder().model(url).apply(PluginRequestBuilder).build())
      .first { it is ImageResult.Success || it is ImageResult.Failure }
  }
  assertTrue("could not warm $url into the memory cache: $result", result is ImageResult.Success)
}

/** Places [content] in the middle of the [Backdrop]. */
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

/**
 * Advances the test clock a frame at a time until [condition] holds.
 *
 * These tests turn the clock's auto advance off. A shimmer animates forever, so waiting for
 * idleness would never return, and a fade has to be read part way through rather than after it has
 * settled. Nothing then recomposes on its own, so the test hands out the frames, and it sleeps
 * between them because the load it is waiting for resolves on a thread the clock knows nothing
 * about.
 */
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

/** The pixels of the image under test, as they are on screen right now. */
internal fun ComposeTestRule.readPixels(): PixelMap =
  onNodeWithTag(PluginImageTag).captureToImage().toPixelMap()

/**
 * Every [step]th pixel of the node, inset by one step.
 *
 * The inset skips the anti-aliased outermost row and column, where a pixel is part image and part
 * backdrop however well the plugin did its job.
 */
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

/** The pixel in the middle of the node. */
internal fun PixelMap.centre(): Color = this[width / 2, height / 2]

/** A pixel near the top left corner of the node, which a centred reveal reaches last. */
internal fun PixelMap.corner(): Color = this[4, 4]

/** Whether this pixel is the backdrop showing through rather than anything the image drew. */
internal fun Color.isBackdrop(): Boolean = green > 0.5f

/** How much of the node the image covered, between 0 and 1. */
internal fun PixelMap.covered(): Float {
  val pixels = samples()
  return pixels.count { !it.isBackdrop() }.toFloat() / pixels.size
}

/** Whether this pixel is [other] to within [tolerance] on every channel. */
internal fun Color.matches(other: Color, tolerance: Float = 0.06f): Boolean =
  abs(red - other.red) <= tolerance &&
    abs(green - other.green) <= tolerance &&
    abs(blue - other.blue) <= tolerance

/** How many of these pixels are not [colour], for a failure message that says how badly. */
internal fun List<Color>.notMatching(colour: Color, tolerance: Float = 0.06f): List<Color> =
  filterNot { it.matches(colour, tolerance) }
