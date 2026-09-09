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
import com.skydoves.landscapist.core.LandscapistConfig
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

/**
 * Numbers land in logcat under MEASURE; a timing threshold on an emulator is a flaky test.
 *
 * Every test here claims the process before it measures anything, and only one measurement fits in
 * a process. See [DeviceMeasure.claimTheProcess]: the guard used to cover four of these six, and to
 * allow two tests with the same label to share a process, which is how a resident set measurement
 * ended up following a cold load measurement of the same library.
 */
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
    server.serve("/warm.jpg", ImageFixtures.photo(360, 360))
  }

  @After
  fun stop() = server.close()

  private fun urls() = List(images) { server.url("/image-$it.jpg") }

  /**
   * Loads one image through the stack about to be measured, so it pays for no class loading.
   *
   * Only that one stack: a process measures a single loader now, and warming the other one first
   * put its class loading, its sockets and its thread pools into every measurement, always in the
   * same order, which is a thumb on the scale rather than a control for one.
   */
  private fun warmLandscapist() {
    val url = server.url("/warm.jpg")
    runBlocking {
      newLandscapist().load(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .size(side, side)
          .build(),
      ).first { it is ImageResult.Success || it is ImageResult.Failure }
    }
    server.resetCounts()
    DeviceMeasure.settle()
  }

  private fun warmCoil() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val url = server.url("/warm.jpg")
    runBlocking {
      newCoil().execute(CoilRequest.Builder(context).data(url).size(side, side).build())
    }
    server.resetCounts()
    DeviceMeasure.settle()
  }

  // Both loaders get the same memory cache. Landscapist used to take the 64 MiB default while
  // Coil was handed 32 MiB, so the two rows were not budgeted alike.
  private fun newLandscapist(): Landscapist = Landscapist.builder()
    .config(LandscapistConfig(memoryCacheSize = DeviceMeasure.MEMORY_CACHE_BYTES))
    .noDiskCache()
    .build()

  private fun newCoil(): ImageLoader {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return ImageLoader.Builder(context)
      .memoryCache {
        MemoryCache.Builder().maxSizeBytes(DeviceMeasure.MEMORY_CACHE_BYTES).build()
      }
      .diskCache(null)
      .build()
  }

  private val sharedLandscapist by lazy { newLandscapist() }
  private val sharedCoil by lazy { newCoil() }

  // One test each: a compose rule accepts setContent once, and whichever library went second
  // would find the server warm.
  @Test
  fun coldLoadLandscapist() {
    DeviceMeasure.claimTheProcess("landscapist cold load")
    warmLandscapist()
    val elapsed = measureFirstSuccess("landscapist") { url, success, failure ->
      LandscapistImage(
        imageModel = { url },
        landscapist = sharedLandscapist,
        modifier = Modifier.size(side.dp),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        onImageStateChanged = { state ->
          when (state) {
            is LandscapistImageState.Success -> success()
            is LandscapistImageState.Failure -> failure()
            else -> Unit
          }
        },
      )
    }
    DeviceMeasure.report(
      "cold load, $images images, until every one reported success",
      "landscapist ${elapsed.formatNanos()}",
    )
  }

  @Test
  fun coldLoadCoil() {
    DeviceMeasure.claimTheProcess("coil cold load")
    warmCoil()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val elapsed = measureFirstSuccess("coil") { url, success, failure ->
      AsyncImage(
        model = CoilRequest.Builder(context).data(url).build(),
        imageLoader = sharedCoil,
        contentDescription = null,
        modifier = Modifier.size(side.dp),
        onState = { state ->
          when (state) {
            is AsyncImagePainter.State.Success -> success()
            is AsyncImagePainter.State.Error -> failure()
            else -> Unit
          }
        },
      )
    }
    DeviceMeasure.report(
      "cold load, $images images, until every one reported success",
      "coil ${elapsed.formatNanos()}",
    )
  }

  /** Composes every url at once and returns how long until all of them reported success. */
  private fun measureFirstSuccess(
    label: String,
    content: @Composable (String, () -> Unit, () -> Unit) -> Unit,
  ): Long {
    val urls = urls()
    val done = AtomicInteger()
    val failed = AtomicInteger()
    val firstAt = AtomicLong()
    val start = System.nanoTime()
    compose.setContent {
      for (url in urls) {
        content(
          url,
          {
            firstAt.compareAndSet(0, System.nanoTime() - start)
            done.incrementAndGet()
          },
          { failed.incrementAndGet() },
        )
      }
    }
    compose.waitUntil(timeoutMillis = TIMEOUT_MS) { done.get() + failed.get() >= urls.size }
    val elapsed = System.nanoTime() - start
    // A failure ends the wait as a success does, so this can fire. The check it replaces sat
    // behind a waitUntil that throws on its own and could not.
    check(failed.get() == 0) { "$label failed to load ${failed.get()} of ${urls.size} images" }
    DeviceMeasure.report("first image on screen, $label", firstAt.get().formatNanos())
    return elapsed
  }

  @Test
  fun residentMemoryForAScreenOfImages() {
    DeviceMeasure.claimTheProcess("landscapist resident set")
    warmLandscapist()
    // The only number that sees native bitmaps, which the allocation counter cannot.
    val landscapist = residentCostOf("landscapist") { url, success, failure ->
      LandscapistImage(
        imageModel = { url },
        landscapist = sharedLandscapist,
        modifier = Modifier.size(side.dp),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        onImageStateChanged = { state ->
          when (state) {
            is LandscapistImageState.Success -> success()
            is LandscapistImageState.Failure -> failure()
            else -> Unit
          }
        },
      )
    }
    DeviceMeasure.report(
      "resident set above resting for $images images, landscapist",
      (landscapist * 1024).formatBytes(),
    )
  }

  @Test
  fun residentMemoryForAScreenOfImagesCoil() {
    DeviceMeasure.claimTheProcess("coil resident set")
    warmCoil()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val coil = residentCostOf("coil") { url, success, failure ->
      AsyncImage(
        model = CoilRequest.Builder(context).data(url).build(),
        imageLoader = sharedCoil,
        contentDescription = null,
        modifier = Modifier.size(side.dp),
        onState = { state ->
          when (state) {
            is AsyncImagePainter.State.Success -> success()
            is AsyncImagePainter.State.Error -> failure()
            else -> Unit
          }
        },
      )
    }
    DeviceMeasure.report(
      "resident set above resting for $images images, coil",
      (coil * 1024).formatBytes(),
    )
  }

  /** Kilobytes of resident set the process grew by while holding [images] on screen. */
  private fun residentCostOf(
    label: String,
    content: @Composable (String, () -> Unit, () -> Unit) -> Unit,
  ): Long {
    val urls = urls()
    val done = AtomicInteger()
    val failed = AtomicInteger()
    DeviceMeasure.settle()
    val resting = DeviceMeasure.residentKb()
    compose.setContent {
      for (url in urls) {
        content(url, { done.incrementAndGet() }, { failed.incrementAndGet() })
      }
    }
    compose.waitUntil(timeoutMillis = TIMEOUT_MS) { done.get() + failed.get() >= urls.size }
    // An image that failed holds no bitmap, so a failure would read as a smaller resident set.
    check(failed.get() == 0) { "$label failed to load ${failed.get()} of ${urls.size} images" }
    DeviceMeasure.settle()
    return DeviceMeasure.residentKb() - resting
  }

  @Test
  fun decodeALargePhotoDownToAThumbnail() {
    DeviceMeasure.claimTheProcess("landscapist decode")
    warmLandscapist()
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
    DeviceMeasure.claimTheProcess("coil decode")
    warmCoil()
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
    const val TIMEOUT_MS = 30_000L
  }
}
