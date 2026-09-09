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
package com.github.skydoves.landscapistdemo.device

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.cache.DiskLruCache
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

@LargeTest
@RunWith(AndroidJUnit4::class)
class CachingDeviceTest {

  private lateinit var server: LocalImageServer
  private val diskDirectories = mutableListOf<File>()

  @Before fun start() {
    server = LocalImageServer()
  }

  @After fun stop() {
    server.close()
    diskDirectories.forEach { it.deleteRecursively() }
  }

  /** The real platform decoder, counted. */
  private class CountingDecoder : ImageDecoder {
    private val delegate = createPlatformDecoder()
    val decodes = AtomicInteger()

    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      decodes.incrementAndGet()
      return delegate.decode(data, mimeType, targetWidth, targetHeight, config)
    }
  }

  private class Loader(
    val landscapist: Landscapist,
    val decoder: CountingDecoder,
    val diskCache: DiskLruCache? = null,
  ) {
    val decodes: Int get() = decoder.decodes.get()
  }

  /** No disk cache, so anything not served from memory is a request the server sees. */
  private fun memoryOnlyLoader(): Loader {
    val decoder = CountingDecoder()
    return Loader(
      Landscapist.builder().noDiskCache().decoder(decoder).build(),
      decoder,
    )
  }

  /** A disk cache in a directory this test deletes afterwards. */
  private fun diskBackedLoader(): Loader {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.cacheDir, "landscapist-device-${System.nanoTime()}")
    diskDirectories += directory
    val decoder = CountingDecoder()
    val disk = DiskLruCache.create(directory.toOkioPath(), DISK_CACHE_BYTES, FileSystem.SYSTEM)
    return Loader(
      Landscapist.builder()
        // Strong references only, so clearMemoryCache really empties it and a later hit can
        // only have come from disk.
        .config(LandscapistConfig(weakReferencesEnabled = false))
        .diskCache(disk)
        .decoder(decoder)
        .build(),
      decoder,
      disk,
    )
  }

  private fun request(url: String, width: Int, height: Int, disk: CachePolicy) =
    ImageRequest.builder()
      .model(url)
      .diskCachePolicy(disk)
      .size(width, height)
      .build()

  private suspend fun Landscapist.terminal(request: ImageRequest): ImageResult =
    load(request).first { it is ImageResult.Success || it is ImageResult.Failure }

  /** Disk is out of the way by default, so a load not served from memory reaches the server. */
  private fun Loader.load(
    url: String,
    width: Int,
    height: Int,
    disk: CachePolicy = CachePolicy.DISABLED,
  ): ImageResult.Success {
    val result = runBlocking {
      withTimeoutOrNull(LOAD_TIMEOUT_MS) {
        landscapist.terminal(request(url, width, height, disk))
      }
    } ?: throw AssertionError("loading $url at ${width}x$height never finished")
    return result as? ImageResult.Success
      ?: throw AssertionError("loading $url at ${width}x$height failed: $result")
  }

  @Test
  fun aSecondLoadOfTheSameUrlAtTheSameSizeDoesNotReachTheServer() {
    server.serve(SQUARE, largeSquare())
    val url = server.url(SQUARE)
    val loader = memoryOnlyLoader()

    val first = loader.load(url, 360, 360)
    val second = loader.load(url, 360, 360)

    assertEquals(
      "the first load did not come off the network",
      DataSource.NETWORK,
      first.dataSource,
    )
    assertEquals("the second load did not come from memory", DataSource.MEMORY, second.dataSource)
    assertEquals("the same url at the same size was fetched twice", 1, server.hitCount(SQUARE))
    assertEquals("the same url at the same size was decoded twice", 1, loader.decodes)
    assertEquals(
      "the cached entry came back at a different size than it was decoded at",
      first.originalWidth,
      second.originalWidth,
    )
  }

  @Test
  fun theSizesAGridActuallyMeasuresShareOneDecode() {
    // A grid whose columns do not divide evenly asks for 359, 360 and 361 wide.
    server.serve(SQUARE, largeSquare())
    val url = server.url(SQUARE)
    val loader = memoryOnlyLoader()

    val first = loader.load(url, 360, 360)
    val narrower = loader.load(url, 359, 359)
    val wider = loader.load(url, 361, 361)

    assertEquals("359, 360 and 361 wide were fetched separately", 1, server.hitCount(SQUARE))
    assertEquals("359, 360 and 361 wide were decoded separately", 1, loader.decodes)
    for ((label, reused) in listOf("359" to narrower, "361" to wider)) {
      assertEquals(
        "the $label wide request did not reuse the entry decoded for 360",
        DataSource.MEMORY,
        reused.dataSource,
      )
      assertEquals(
        "the $label wide request came back as a different image than the one cached",
        first.originalWidth,
        reused.originalWidth,
      )
    }
  }

  @Test
  fun aSomewhatLargerEntryServesASmallerRequest() {
    server.serve(SQUARE, largeSquare())
    val url = server.url(SQUARE)
    val loader = memoryOnlyLoader()

    val large = loader.load(url, 500, 500)
    val smaller = loader.load(url, 360, 360)

    assertEquals(
      "an entry big enough to draw the smaller slot was decoded again anyway",
      1,
      loader.decodes,
    )
    assertEquals("the smaller request went back to the network", 1, server.hitCount(SQUARE))
    assertEquals(
      "the smaller request did not reuse the larger entry",
      large.originalWidth,
      smaller.originalWidth,
    )
  }

  @Test
  fun aMuchLargerRequestDecodesAgain() {
    // The other half of the tolerance: reuse may not hand a slot fewer pixels than it draws.
    server.serve(SQUARE, largeSquare())
    val url = server.url(SQUARE)
    val loader = memoryOnlyLoader()

    val small = loader.load(url, 360, 360)
    val large = loader.load(url, 1080, 1080)

    assertEquals("the 1080 wide request reused an entry decoded for 360", 2, loader.decodes)
    assertEquals("the 1080 wide request did not go back for the bytes", 2, server.hitCount(SQUARE))
    assertTrue(
      "the second decode came back at ${large.originalWidth}px, no larger than the " +
        "${small.originalWidth}px entry it was supposed to improve on",
      large.originalWidth > small.originalWidth,
    )
  }

  @Test
  fun anEntryThePlatformCannotDecodeSmallerIsReusedRatherThanDecodedAgain() {
    // A panorama the platform decoder cannot shrink: it samples by powers of two and only while
    // both axes still cover the target, so 4000x500 comes back whole whatever is asked for.
    // Refusing it would decode the same pixels again for nothing.
    server.serve(PANORAMA, ImageFixtures.solid(4000, 500, Color.GREEN))
    val url = server.url(PANORAMA)
    val loader = memoryOnlyLoader()

    val wide = loader.load(url, 1080, 1080)
    loader.load(url, 360, 360)

    assertTrue(
      "the panorama was cached at ${wide.originalWidth}x${wide.originalHeight}, which is not " +
        "oversized on the width alone, so this proves nothing about the rule under test",
      wide.originalWidth > 360 * 2 && wide.originalHeight <= 360 * 2,
    )
    assertEquals(
      "the panorama was decoded again although the platform cannot return anything smaller",
      1,
      loader.decodes,
    )
    assertEquals("the narrow slot went back for bytes it already had", 1, server.hitCount(PANORAMA))
  }

  @Test
  fun anEntryThePlatformCanDecodeSmallerIsDecodedAgain() {
    // The other side of the same rule: a square source does divide evenly, so asking for a smaller
    // box really does come back smaller and keeping the large entry would waste the difference.
    server.serve(SQUARE, largeSquare())
    val url = server.url(SQUARE)
    val loader = memoryOnlyLoader()

    val wide = loader.load(url, 1080, 1080)
    val narrow = loader.load(url, 200, 200)

    assertTrue(
      "the wide load came back at ${wide.originalWidth}px, which is not larger than twice the " +
        "narrow slot, so this proves nothing about the rule under test",
      wide.originalWidth > 200 * 2,
    )
    assertTrue(
      "the narrow slot was served the ${narrow.originalWidth}px entry rather than a smaller one",
      narrow.originalWidth < wide.originalWidth,
    )
    assertEquals("the narrow slot reused the oversized entry", 2, loader.decodes)
  }

  @Test
  fun concurrentLoadsOfTheSameUrlReachTheServerOnce() {
    // The response is held open while every caller starts, so they are in flight together.
    val gate = CountDownLatch(1)
    server.serve(SQUARE, largeSquare(), gate = gate)
    val url = server.url(SQUARE)
    val loader = memoryOnlyLoader()

    val results = runBlocking {
      val loads = (1..CONCURRENT_CALLERS).map {
        async(Dispatchers.Default) {
          withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            loader.landscapist.terminal(request(url, 300, 300, CachePolicy.DISABLED))
          }
        }
      }
      // Give the other callers room to open a request of their own if coalescing is broken.
      withTimeoutOrNull(FIRST_HIT_TIMEOUT_MS) {
        while (server.hitCount(SQUARE) == 0) delay(20)
      }
      delay(500)
      val whileHeld = server.hitCount(SQUARE)
      gate.countDown()
      whileHeld to loads.awaitAll()
    }
    val (whileHeld, terminals) = results
    val trace = terminals.map { it?.let { result -> result::class.simpleName } ?: "never finished" }

    assertTrue(
      "no request ever reached the server, so nothing was ever held open and this test measured " +
        "nothing",
      whileHeld > 0,
    )
    assertEquals(
      "$CONCURRENT_CALLERS concurrent loads of one url opened $whileHeld requests",
      1,
      whileHeld,
    )
    assertEquals(
      "the coalesced load was fetched more than once in total",
      1,
      server.hitCount(SQUARE),
    )
    assertEquals("the coalesced load was decoded more than once", 1, loader.decodes)
    assertEquals(
      "not every caller got an image: $trace",
      CONCURRENT_CALLERS,
      terminals.count { it is ImageResult.Success },
    )
  }

  @Test
  fun aDiskCachedImageOutlivesTheMemoryCache() {
    server.serve(SQUARE, largeSquare())
    val url = server.url(SQUARE)
    val loader = diskBackedLoader()

    val first = loader.load(url, 300, 300, CachePolicy.ENABLED)
    assertEquals(
      "the first load did not come off the network",
      DataSource.NETWORK,
      first.dataSource,
    )
    val written = first.diskCachePath
      ?: throw AssertionError("the loader reported no disk cache path, so nothing was written")
    val disk = loader.diskCache
      ?: throw AssertionError("the disk backed loader was built without a disk cache")
    // The disk write runs off the critical path, so the entry is not there the instant the
    // result is.
    awaitDiskWrite(disk, File(written))

    loader.landscapist.clearMemoryCache()
    val second = loader.load(url, 300, 300, CachePolicy.ENABLED)

    assertEquals(
      "the second load came from ${second.dataSource} rather than disk",
      DataSource.DISK,
      second.dataSource,
    )
    assertEquals("the second load went back to the network", 1, server.hitCount(SQUARE))
    assertEquals(
      "the image came back from disk at a different size than it was first decoded at",
      first.originalWidth,
      second.originalWidth,
    )
  }

  /** Blocks until [cache] has committed the entry at [file]. */
  private fun awaitDiskWrite(cache: DiskLruCache, file: File) {
    val deadline = System.nanoTime() + DISK_WRITE_TIMEOUT_MS * 1_000_000
    while (System.nanoTime() < deadline) {
      // The size is only counted once the entry is registered, so it is the safer wait.
      if (cache.size > 0 && file.isFile) return
      Thread.sleep(20)
    }
    throw AssertionError("the disk cache never committed anything for $file")
  }

  /** Square, because the decoder halves only while both axes still cover the request. */
  private fun largeSquare(): ByteArray = ImageFixtures.solid(1200, 1200, Color.BLUE)

  private companion object {
    const val SQUARE = "/square.jpg"
    const val PANORAMA = "/panorama.jpg"
    const val CONCURRENT_CALLERS = 8
    const val LOAD_TIMEOUT_MS = 30_000L
    const val FIRST_HIT_TIMEOUT_MS = 10_000L
    const val DISK_WRITE_TIMEOUT_MS = 10_000L
    const val DISK_CACHE_BYTES = 8L * 1024 * 1024
  }
}
