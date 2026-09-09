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

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.ImagePluginComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.core.network.KtorImageFetcher
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.plugins.ImagePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal const val LoadTimeoutMs: Long = 15_000

/** No size of its own: a request that already carries one ignores the size a plugin asks for. */
internal val UnsizedRequestBuilder: ImageRequest.Builder.() -> Unit = {
  diskCachePolicy(CachePolicy.DISABLED)
}

/** rememberImageComponent keeps its first component, so a changed plugin would never arrive. */
internal fun pluginComponent(vararg plugins: ImagePlugin): ImageComponent =
  ImagePluginComponent().addPlugins(plugins.toList())

internal const val ContentImageTag: String = "landscapistContentImage"

/** One per test, so each starts from an empty memory cache. */
internal fun contentPluginLoader(): Landscapist = Landscapist.builder().noDiskCache().build()

internal fun contentPluginLoader(fetcher: ImageFetcher): Landscapist =
  Landscapist.builder().noDiskCache().fetcher(fetcher).build()

internal fun contentPluginLoader(fetcher: ImageFetcher, decoder: ImageDecoder): Landscapist =
  Landscapist.builder().noDiskCache().fetcher(fetcher).decoder(decoder).build()

internal fun contentImageModifier(tag: String = ContentImageTag): Modifier = Modifier
  .size(PluginImageSize)
  .testTag(tag)

internal fun ComposeTestRule.readContentPixels(tag: String = ContentImageTag): PixelMap =
  onNodeWithTag(tag).captureToImage().toPixelMap()

/** The counterpart of [advanceUntil] where nothing animates and the clock is left alone. */
internal fun ComposeTestRule.awaitUntil(
  what: String,
  timeoutMs: Long = LoadTimeoutMs,
  condition: () -> Boolean,
) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (!condition()) {
    assertTrue(
      "timed out after ${timeoutMs}ms waiting for $what",
      System.currentTimeMillis() < deadline,
    )
    Thread.sleep(10)
    waitForIdle()
  }
}

/** Keeps the composition running for [ms], so a negative is not read the instant it is true. */
internal fun ComposeTestRule.idleFor(ms: Long) {
  val until = System.currentTimeMillis() + ms
  while (System.currentTimeMillis() < until) {
    Thread.sleep(10)
    waitForIdle()
  }
}

/** Every state an image reported, readable from the test thread while the device draws. */
internal class StateRecorder {

  private val seen = CopyOnWriteArrayList<LandscapistImageState>()

  val isSuccess: Boolean get() = seen.lastOrNull() is LandscapistImageState.Success

  val isFailure: Boolean get() = seen.lastOrNull() is LandscapistImageState.Failure

  fun record(state: LandscapistImageState) {
    seen += state
  }

  override fun toString(): String = seen.joinToString { it::class.simpleName ?: "?" }
}

/**
 * Holds a decode wider than [holdLargerThan] until [release], and lets smaller ones through.
 *
 * A preview plugin asks for the same url at a small size, so the download is shared with the full
 * image and there is no longer a second fetch to hold. What separates the two is the decode, which
 * is what this holds and what the plugin actually buys.
 */
internal class HoldingDecoder(
  private val holdLargerThan: Int,
  private val delegate: ImageDecoder = createPlatformDecoder(),
) : ImageDecoder {

  private val released = CountDownLatch(1)
  private val recorded = CopyOnWriteArrayList<IntSize>()

  /** Every target size a decode was asked for, which is per request where a fetch is shared. */
  val sizes: List<IntSize> get() = recorded

  override suspend fun decode(
    data: ByteArray,
    mimeType: String?,
    targetWidth: Int?,
    targetHeight: Int?,
    config: LandscapistConfig,
  ): DecodeResult {
    recorded += IntSize(targetWidth ?: 0, targetHeight ?: 0)
    if ((targetWidth ?: 0) > holdLargerThan) {
      withContext(Dispatchers.IO) { released.await(30, TimeUnit.SECONDS) }
    }
    return delegate.decode(data, mimeType, targetWidth, targetHeight, config)
  }

  fun release() {
    released.countDown()
  }
}

/** Records target sizes, and holds a request wider than [holdLargerThan] open until [release]. */
internal class RecordingFetcher(
  private val holdLargerThan: Int = Int.MAX_VALUE,
  private val delegate: ImageFetcher = KtorImageFetcher.create(),
) : ImageFetcher {

  private val released = CountDownLatch(1)
  private val recorded = CopyOnWriteArrayList<Fetched>()

  val fetches: List<Fetched> get() = recorded

  val sizes: List<IntSize> get() = recorded.map { it.size }

  override fun canHandle(model: Any?): Boolean = delegate.canHandle(model)

  override suspend fun fetch(request: ImageRequest): FetchResult {
    recorded += Fetched(
      model = request.model.toString(),
      size = IntSize(request.targetWidth ?: 0, request.targetHeight ?: 0),
    )
    if ((request.targetWidth ?: 0) > holdLargerThan) {
      withContext(Dispatchers.IO) { released.await(30, TimeUnit.SECONDS) }
    }
    return delegate.fetch(request)
  }

  fun release() {
    released.countDown()
  }

  data class Fetched(val model: String, val size: IntSize)
}

/** The colour at [x], [y], given as fractions of the node. */
internal fun PixelMap.at(x: Float, y: Float): Color {
  val column = (width * x).toInt().coerceIn(0, width - 1)
  val row = (height * y).toInt().coerceIn(0, height - 1)
  return this[column, row]
}

/** Read off red: a quadrant of the fixture is the backdrop's own green. */
internal fun PixelMap.quadrantCoverage(): Float {
  val pixels = samples()
  return pixels.count { it.red > 0.3f }.toFloat() / pixels.size
}

/** Mean brightness difference between neighbouring pixels; blur and downscaling lower it. */
internal fun PixelMap.localContrast(): Float {
  var total = 0f
  var count = 0
  var y = 1
  while (y < height - 1) {
    var x = 1
    while (x < width - 2) {
      total += abs(luma(this[x, y]) - luma(this[x + 1, y]))
      count++
      x++
    }
    y += 2
  }
  return if (count == 0) 0f else total / count
}

/** The average colour at the centre of the ARGB pixels a placeholder decoder produced. */
internal fun IntArray.centreColour(width: Int, height: Int): Color {
  var red = 0f
  var green = 0f
  var blue = 0f
  var count = 0
  val fromX = (width * 0.45f).toInt().coerceIn(0, width - 1)
  val toX = (width * 0.55f).toInt().coerceIn(fromX + 1, width)
  val fromY = (height * 0.45f).toInt().coerceIn(0, height - 1)
  val toY = (height * 0.55f).toInt().coerceIn(fromY + 1, height)
  for (y in fromY until toY) {
    for (x in fromX until toX) {
      val colour = Color(this[y * width + x])
      red += colour.red
      green += colour.green
      blue += colour.blue
      count++
    }
  }
  return Color(red / count, green / count, blue / count)
}

internal fun ByteArray.decodeToImageBitmap(): ImageBitmap =
  checkNotNull(BitmapFactory.decodeByteArray(this, 0, size)) { "the fixture did not decode" }
    .asImageBitmap()

internal fun Color.describe(): String =
  "rgb(${(red * 255).toInt()}, ${(green * 255).toInt()}, ${(blue * 255).toInt()})"

private fun luma(colour: Color): Float =
  0.299f * colour.red + 0.587f * colour.green + 0.114f * colour.blue
