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

/**
 * What the content and transformation plugin tests need on top of the shared harness.
 *
 * These plugins do not animate, so the clock is left to run itself here and the only thing a test
 * waits for is a load resolving on another thread. What they do instead is put something on screen
 * that is not the image, either before it arrives or in place of it, which is why so much of this
 * is about telling one drawn thing from another by its pixels.
 */

/** How long a load is given before a test gives up on it. */
internal const val LoadTimeoutMs: Long = 15_000

/**
 * A request with no size of its own, so the layout decides what to decode at.
 *
 * The thumbnail and progressive plugins ask for a size, and [PluginRequestBuilder] would settle it
 * before they were asked: a request that already carries a target size is passed through untouched,
 * and the size a loading plugin asks for is dropped.
 */
internal val UnsizedRequestBuilder: ImageRequest.Builder.() -> Unit = {
  diskCachePolicy(CachePolicy.DISABLED)
}

/**
 * A component holding exactly [plugins], built outside a composition.
 *
 * [com.skydoves.landscapist.components.rememberImageComponent] keeps the first component it built
 * and discards every later one, so a plugin reconfigured between compositions would never reach
 * the image. A test that changes a plugin has to hand the image a component that really changed.
 */
internal fun pluginComponent(vararg plugins: ImagePlugin): ImageComponent =
  ImagePluginComponent().addPlugins(plugins.toList())

/** The tag an image under test carries, so a capture knows which node to read back. */
internal const val ContentImageTag: String = "landscapistContentImage"

/** A loader that keeps nothing on disk, so every test starts from an empty memory cache. */
internal fun contentPluginLoader(): Landscapist = Landscapist.builder().noDiskCache().build()

/** A loader that fetches through [fetcher], for a test that needs to see the requests. */
internal fun contentPluginLoader(fetcher: ImageFetcher): Landscapist =
  Landscapist.builder().noDiskCache().fetcher(fetcher).build()

/** The modifier an image under test carries, tagged so a capture can find it again. */
internal fun contentImageModifier(tag: String = ContentImageTag): Modifier = Modifier
  .size(PluginImageSize)
  .testTag(tag)

/** The pixels of the image tagged [tag], as they are on screen right now. */
internal fun ComposeTestRule.readContentPixels(tag: String = ContentImageTag): PixelMap =
  onNodeWithTag(tag).captureToImage().toPixelMap()

/**
 * Waits until [condition] holds, or fails saying what it was waiting for.
 *
 * The counterpart of [advanceUntil] for tests that leave the clock alone: nothing here animates,
 * and driving the clock by hand would only hide a plugin that never asked for a frame.
 */
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
 * The real network fetcher, with the target size of every request recorded.
 *
 * A target size never reaches the wire, so a plugin that asks for a thumbnail and the image that
 * replaces it look identical to the server. This is the only place the two can be told apart, and
 * the bytes still come off the socket: every fetch is delegated to the fetcher the loader would
 * have built for itself.
 *
 * A request wider than [holdLargerThan] waits for [release], which is what keeps one image in its
 * loading state while another finishes. A delay on the route cannot do that job, because both
 * requests are for the same URL and the route would hold them both.
 */
internal class RecordingFetcher(
  private val holdLargerThan: Int = Int.MAX_VALUE,
  private val delegate: ImageFetcher = KtorImageFetcher.create(),
) : ImageFetcher {

  private val released = CountDownLatch(1)
  private val recorded = CopyOnWriteArrayList<Fetched>()

  /** What was asked for, and at what size, in the order the requests were made. */
  val fetches: List<Fetched> get() = recorded

  /** The target size of every request, in the order they were made. */
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

  /** Lets the held request through. */
  fun release() {
    released.countDown()
  }

  /** One request the loader made, as the fetcher saw it. */
  data class Fetched(val model: String, val size: IntSize)
}

/** The colour at [x], [y], given as fractions of the node. */
internal fun PixelMap.at(x: Float, y: Float): Color {
  val column = (width * x).toInt().coerceIn(0, width - 1)
  val row = (height * y).toInt().coerceIn(0, height - 1)
  return this[column, row]
}

/**
 * How much of the node holds red, which is how much of the quadrant fixture is on it.
 *
 * [covered] cannot answer that here: it reads the backdrop off the green channel, and two of the
 * four quadrants have green in them. Nothing in the backdrop has any red, and half of the fixture
 * does, so a node showing the fixture at any size comes out near a half and an empty one at zero.
 */
internal fun PixelMap.quadrantCoverage(): Float {
  val pixels = samples()
  return pixels.count { it.red > 0.3f }.toFloat() / pixels.size
}

/**
 * How much fine detail survived, as the mean difference in brightness between side by side pixels.
 *
 * Blurring lowers it, and lowers it further the larger the radius; decoding at fifteen pixels and
 * drawing the result across the node lowers it too. It is the only way to say "this is a smoothed
 * version of that" without a reference image, and unlike a checksum it says which way round the
 * two are.
 */
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

/** Decodes fixture bytes, for a test that hands a plugin an image rather than a URL. */
internal fun ByteArray.decodeToImageBitmap(): ImageBitmap =
  checkNotNull(BitmapFactory.decodeByteArray(this, 0, size)) { "the fixture did not decode" }
    .asImageBitmap()

/** A colour named the way a failure message can read it. */
internal fun Color.describe(): String =
  "rgb(${(red * 255).toInt()}, ${(green * 255).toInt()}, ${(blue * 255).toInt()})"

private fun luma(colour: Color): Float =
  0.299f * colour.red + 0.587f * colour.green + 0.114f * colour.blue
