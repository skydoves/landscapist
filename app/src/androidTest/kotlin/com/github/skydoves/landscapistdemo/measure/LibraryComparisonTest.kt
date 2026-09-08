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
package com.github.skydoves.landscapistdemo.measure

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.memory.MemoryCache
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.github.skydoves.landscapistdemo.measure.DeviceMeasure.formatBytes
import com.github.skydoves.landscapistdemo.measure.DeviceMeasure.formatNanos
import com.github.skydoves.landscapistdemo.measure.DeviceMeasure.median
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import coil3.request.ImageRequest as CoilRequest

/** Numbers land in logcat under MEASURE; a timing threshold on an emulator is a flaky test. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LibraryComparisonTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer
  private val images = 20
  private val side = 120

  @Before
  fun start() {
    server = LocalImageServer()
    repeat(images) { index ->
      server.serve("/image-$index.jpg", ImageFixtures.photo(360, 360))
    }
    warmBothStacks()
  }

  /**
   * Loads one image through each library, so neither pays for the other's class loading.
   *
   * Not enough on its own to make two loaders comparable in one process. See [claimTheProcess].
   */
  private fun warmBothStacks() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    server.serve("/warm.jpg", ImageFixtures.photo(360, 360))
    val url = server.url("/warm.jpg")
    runBlocking {
      Landscapist.builder().noDiskCache().build().load(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(side, side)
          .build(),
      ).first { it is ImageResult.Success || it is ImageResult.Failure }
      ImageLoader.Builder(context).memoryCache(null).diskCache(null).build().execute(
        CoilRequest.Builder(context).data(url).size(side, side).build(),
      )
    }
    server.resetCounts()
    DeviceMeasure.settle()
  }

  @After
  fun stop() = server.close()

  private fun urls() = List(images) { server.url("/image-$it.jpg") }

  /**
   * Refuses to measure a second loader in a process that has already measured one.
   *
   * Whichever ran second read as faster by more than the difference being measured, and warming
   * both stacks first did not fix it: the sockets, the thread pools and the JIT of everything under
   * Compose are shared too. Run one test per instrumentation invocation, so each gets its own
   * process:
   *
   * `-Pandroid.testInstrumentationRunnerArguments.class=...LibraryComparisonTest#coldLoadCoil`
   */
  private fun claimTheProcess(label: String) {
    val previous = measuredInThisProcess
    check(previous == null || previous == label) {
      "$previous was already measured in this process, so $label cannot be compared against it. " +
        "Run one measurement per instrumentation invocation."
    }
    measuredInThisProcess = label
  }

  private fun newLandscapist(): Landscapist = Landscapist.builder().noDiskCache().build()

  private fun newCoil(): ImageLoader {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return ImageLoader.Builder(context)
      .memoryCache { MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).build() }
      .diskCache(null)
      .build()
  }

  // One test each: a compose rule accepts setContent once, and whichever library went second
  // would find the server warm.
  @Test
  fun coldLoadLandscapist() {
    claimTheProcess("landscapist")
    val elapsed = measureFirstSuccess("landscapist") { url, done ->
      LandscapistImage(
        imageModel = { url },
        landscapist = newLandscapistShared,
        modifier = Modifier.size(side.dp),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        onImageStateChanged = { if (it is LandscapistImageState.Success) done() },
      )
    }
    DeviceMeasure.report(
      "cold load, $images images, until every one reported success",
      "landscapist ${elapsed.formatNanos()}",
    )
  }

  @Test
  fun coldLoadCoil() {
    claimTheProcess("coil")
    val elapsed = measureFirstSuccess("coil") { url, done ->
      AsyncImage(
        model = CoilRequest.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
          .data(url)
          .build(),
        imageLoader = sharedCoil,
        contentDescription = null,
        modifier = Modifier.size(side.dp),
        onState = { if (it is AsyncImagePainter.State.Success) done() },
      )
    }
    DeviceMeasure.report(
      "cold load, $images images, until every one reported success",
      "coil ${elapsed.formatNanos()}",
    )
  }

  private val newLandscapistShared by lazy { newLandscapist() }
  private val sharedCoil by lazy { newCoil() }

  /** Composes every url at once and returns how long until all of them reported success. */
  private fun measureFirstSuccess(
    label: String,
    content: @Composable (String, () -> Unit) -> Unit,
  ): Long {
    val urls = urls()
    val done = AtomicInteger()
    val firstAt = AtomicLong()
    val start = System.nanoTime()
    compose.setContent {
      for (url in urls) {
        content(url) {
          firstAt.compareAndSet(0, System.nanoTime() - start)
          done.incrementAndGet()
        }
      }
    }
    compose.waitUntil(timeoutMillis = 30_000) { done.get() >= urls.size }
    val elapsed = System.nanoTime() - start
    check(done.get() >= urls.size) { "$label loaded only ${done.get()} of ${urls.size}" }
    DeviceMeasure.report("first image on screen, $label", firstAt.get().formatNanos())
    return elapsed
  }

  @Test
  fun residentMemoryForAScreenOfImages() {
    claimTheProcess("landscapist")
    // The only number that sees native bitmaps, which the allocation counter cannot.
    val landscapist = residentCostOf { url, done ->
      LandscapistImage(
        imageModel = { url },
        landscapist = newLandscapistShared,
        modifier = Modifier.size(side.dp),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        onImageStateChanged = { if (it is LandscapistImageState.Success) done() },
      )
    }
    DeviceMeasure.report(
      "resident set above resting for $images images, landscapist",
      (landscapist * 1024).formatBytes(),
    )
  }

  @Test
  fun residentMemoryForAScreenOfImagesCoil() {
    claimTheProcess("coil")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val coil = residentCostOf { url, done ->
      AsyncImage(
        model = CoilRequest.Builder(context).data(url).build(),
        imageLoader = sharedCoil,
        contentDescription = null,
        modifier = Modifier.size(side.dp),
        onState = { if (it is AsyncImagePainter.State.Success) done() },
      )
    }
    DeviceMeasure.report(
      "resident set above resting for $images images, coil",
      (coil * 1024).formatBytes(),
    )
  }

  /** Kilobytes of resident set the process grew by while holding [images] on screen. */
  private fun residentCostOf(content: @Composable (String, () -> Unit) -> Unit): Long {
    val urls = urls()
    val done = AtomicInteger()
    DeviceMeasure.settle()
    val resting = DeviceMeasure.residentKb()
    compose.setContent {
      for (url in urls) content(url) { done.incrementAndGet() }
    }
    compose.waitUntil(timeoutMillis = 30_000) { done.get() >= urls.size }
    DeviceMeasure.settle()
    return DeviceMeasure.residentKb() - resting
  }

  @Test
  fun decodeALargePhotoDownToAThumbnail() {
    val photo = ImageFixtures.photo(2000, 1500)
    server.serve("/large.jpg", photo)
    val landscapist = newLandscapist()
    val url = server.url("/large.jpg")

    val timings = DeviceMeasure.timed(warmups = 2, iterations = 8) {
      runBlocking {
        val result = landscapist.load(
          ImageRequest.builder()
            .model(url)
            .diskCachePolicy(CachePolicy.DISABLED)
            .size(200, 150)
            .build(),
        ).first { it is ImageResult.Success || it is ImageResult.Failure }
        check(result is ImageResult.Success) { "decode failed: $result" }
      }
      landscapist.clearMemoryCache()
    }

    val allocated = DeviceMeasure.allocatedDuring {
      runBlocking {
        landscapist.load(
          ImageRequest.builder()
            .model(url)
            .diskCachePolicy(CachePolicy.DISABLED)
            .size(200, 150)
            .build(),
        ).first { it is ImageResult.Success || it is ImageResult.Failure }
      }
      landscapist.clearMemoryCache()
    }

    DeviceMeasure.report(
      "decode 2000x1500 to 200x150, landscapist",
      "median ${timings.median().formatNanos()}, allocated ${allocated.formatBytes()}",
    )
  }

  @Test
  fun coilDecodesTheSamePhoto() {
    // The same bytes and target through Coil's own pipeline, with both caches off.
    val photo = ImageFixtures.photo(2000, 1500)
    server.serve("/large-coil.jpg", photo)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val loader = ImageLoader.Builder(context).memoryCache(null).diskCache(null).build()
    val url = server.url("/large-coil.jpg")
    val request = CoilRequest.Builder(context)
      .data(url)
      .size(200, 150)
      .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
      .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
      .build()

    val timings = DeviceMeasure.timed(warmups = 2, iterations = 8) {
      val result = runBlocking { loader.execute(request) }
      check(result is coil3.request.SuccessResult) { "coil decode failed: $result" }
    }
    val allocated = DeviceMeasure.allocatedDuring {
      runBlocking { loader.execute(request) }
    }

    DeviceMeasure.report(
      "decode 2000x1500 to 200x150, coil",
      "median ${timings.median().formatNanos()}, allocated ${allocated.formatBytes()}",
    )
  }

  private companion object {
    /** Which loader this process has already measured, if any. */
    var measuredInThisProcess: String? = null
  }
}
