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

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.NetworkConfig
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds

@LargeTest
@RunWith(AndroidJUnit4::class)
class NetworkDeviceTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var server: LocalImageServer
  private lateinit var loader: Landscapist

  @Before fun start() {
    server = LocalImageServer()
    loader = Landscapist.builder().noDiskCache().build()
  }

  @After fun stop() = server.close()

  /** A load that never terminates fails here rather than hanging the run. */
  private fun load(url: String, width: Int = 64, height: Int = 64): ImageResult {
    val request = ImageRequest.builder()
      .model(url)
      .diskCachePolicy(CachePolicy.DISABLED)
      .size(width, height)
      .build()
    return runBlocking {
      withTimeoutOrNull(LOAD_TIMEOUT_MS) {
        loader.load(request).first { it is ImageResult.Success || it is ImageResult.Failure }
      }
    } ?: throw AssertionError("$url never reached a terminal state in ${LOAD_TIMEOUT_MS}ms")
  }

  @Test
  fun aMissingImageFailsRatherThanHanging() {
    server.fail(MISSING, status = 404)

    val result = load(server.url(MISSING))

    assertTrue("a 404 did not fail the load: $result", result is ImageResult.Failure)
    assertEquals("the loader did not even ask for the missing image", 1, server.hitCount(MISSING))
  }

  @Test
  fun aServerErrorFailsRatherThanHanging() {
    server.fail(BROKEN, status = 500)

    val result = load(server.url(BROKEN))

    assertTrue("a 500 did not fail the load: $result", result is ImageResult.Failure)
  }

  @Test
  fun bytesCutBeforeTheirHeaderFail() {
    // A download that stopped before the dimensions were readable.
    server.serve(CUT_HEADER, ImageFixtures.photo(64, 64).copyOf(48))

    val result = load(server.url(CUT_HEADER))

    assertTrue(
      "a body with no readable image header did not fail: $result",
      result is ImageResult.Failure,
    )
  }

  @Test
  fun aTruncatedJpegReachesATerminalStateAndNeverReportsMoreThanItHolds() {
    // BitmapFactory accepts an incomplete JPEG and returns the rows it read, so a success here
    // is legitimate. What may not happen is a hang, or a size the header never declared.
    server.serve(TRUNCATED, ImageFixtures.truncatedJpeg(64, 64))

    when (val result = load(server.url(TRUNCATED))) {
      is ImageResult.Failure -> assertTrue(
        "a truncated body failed with nothing to say why: $result",
        result.throwable != null || result.message != null,
      )
      is ImageResult.Success -> assertEquals(
        "a partial decode was reported at a width the 64px source never had",
        64,
        result.originalWidth,
      )
      else -> throw AssertionError("a truncated body settled on $result, which is not terminal")
    }
  }

  @Test
  fun aResponseThatNeverArrivesFailsAtTheConfiguredReadTimeout() {
    val gate = CountDownLatch(1)
    server.serve(STALLED, ImageFixtures.photo(64, 64), gate = gate)
    val impatient = Landscapist.builder()
      .noDiskCache()
      .config(LandscapistConfig(networkConfig = NetworkConfig(readTimeout = READ_TIMEOUT)))
      .build()
    val request = ImageRequest.builder()
      .model(server.url(STALLED))
      .diskCachePolicy(CachePolicy.DISABLED)
      .size(64, 64)
      .build()

    val started = System.nanoTime()
    val result = try {
      runBlocking {
        withTimeoutOrNull(LOAD_TIMEOUT_MS) {
          impatient.load(request).first {
            it is ImageResult.Success || it is ImageResult.Failure
          }
        }
      }
    } finally {
      // Released here, so the server thread is never left holding the response open.
      gate.countDown()
    }
    val elapsedMs = (System.nanoTime() - started) / 1_000_000

    assertEquals("the loader never opened the stalled request", 1, server.hitCount(STALLED))
    assertTrue(
      "a response that never arrived never settled at all in ${LOAD_TIMEOUT_MS}ms",
      result != null,
    )
    assertTrue("a stalled response did not fail the load: $result", result is ImageResult.Failure)
    assertTrue(
      "the load failed after ${elapsedMs}ms, past the ${TIMEOUT_SLACK_MS}ms this allows for the " +
        "${READ_TIMEOUT.inWholeMilliseconds}ms read timeout it was built with",
      elapsedMs < TIMEOUT_SLACK_MS,
    )
  }

  @Test
  fun aRedirectChainResolves() {
    // Two hops, because one hop is the case a client can get right by accident.
    server.redirect(FIRST_HOP, server.url(SECOND_HOP))
    server.redirect(SECOND_HOP, server.url(TARGET))
    server.serve(TARGET, ImageFixtures.photo(120, 90))

    val result = load(server.url(FIRST_HOP), width = 120, height = 90)

    assertTrue("the redirect chain did not resolve: $result", result is ImageResult.Success)
    assertEquals("the first hop was never followed", 1, server.hitCount(SECOND_HOP))
    assertEquals("the second hop was never followed", 1, server.hitCount(TARGET))
    assertEquals(
      "the image behind the redirect chain did not decode",
      120,
      (result as ImageResult.Success).originalWidth,
    )
  }

  @Test
  fun aSlowResponsePassesThroughLoadingBeforeSuccess() {
    server.serve(SLOW, ImageFixtures.photo(200, 200), delayMs = 700)
    val url = server.url(SLOW)
    val states = mutableListOf<LandscapistImageState>()

    compose.setContent {
      LandscapistImage(
        imageModel = { url },
        landscapist = loader,
        modifier = Modifier.size(100.dp),
        onImageStateChanged = { states += it },
      )
    }
    try {
      compose.waitUntil(LOAD_TIMEOUT_MS) {
        states.any { it is LandscapistImageState.Success || it is LandscapistImageState.Failure }
      }
    } catch (timeout: ComposeTimeoutException) {
      throw AssertionError("the slow image never settled, saw ${states.trace()}", timeout)
    }
    compose.waitForIdle()

    val loading = states.indexOfFirst { it is LandscapistImageState.Loading }
    val success = states.indexOfFirst { it is LandscapistImageState.Success }
    assertTrue("no loading state reached the caller, saw ${states.trace()}", loading >= 0)
    assertTrue("the image never succeeded, saw ${states.trace()}", success >= 0)
    assertTrue(
      "the loading state arrived at $loading, after the success at $success: ${states.trace()}",
      loading < success,
    )
    assertTrue(
      "the caller was told the image was loading again after it had already arrived: " +
        states.trace(),
      states.drop(success).none { it is LandscapistImageState.Loading },
    )
  }

  private fun List<LandscapistImageState>.trace(): String =
    joinToString { it::class.simpleName ?: "?" }

  private companion object {
    const val MISSING = "/missing.jpg"
    const val BROKEN = "/broken.jpg"
    const val CUT_HEADER = "/cut-header.jpg"
    const val TRUNCATED = "/truncated.jpg"
    const val FIRST_HOP = "/hop-one.jpg"
    const val SECOND_HOP = "/hop-two.jpg"
    const val TARGET = "/target.jpg"
    const val SLOW = "/slow.jpg"
    const val STALLED = "/stalled.jpg"
    const val LOAD_TIMEOUT_MS = 20_000L
    val READ_TIMEOUT = 1.seconds

    /** Room for a cold client, and no room for a longer timeout than the one under test. */
    val TIMEOUT_SLACK_MS = READ_TIMEOUT.inWholeMilliseconds * 4
  }

  @Test
  fun aDownloadThatStopsShortOfItsLengthDoesNotBecomeAHalfImage() {
    // What a dropped connection looks like: the response promises a length and the socket closes
    // before it arrives. Android's decoder is happy to return the rows it managed to read, so
    // without the short body being caught this would cache half an image under the real url.
    val whole = ImageFixtures.photo(200, 200)
    server.serve(
      "/short.jpg",
      whole.copyOf(whole.size / 3),
      declaredLength = whole.size,
    )

    val result = load(server.url("/short.jpg"), 100, 100)

    assertTrue("a truncated download succeeded: $result", result is ImageResult.Failure)
  }
}
