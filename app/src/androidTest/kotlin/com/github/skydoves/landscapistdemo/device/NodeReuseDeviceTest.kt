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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/** Every url serves an image of its own width, so a reported success names where it came from. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NodeReuseDeviceTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  /** Built per test, so each starts with an empty memory cache. */
  private lateinit var loader: Landscapist

  /** Written from the main thread by the image, read from the test thread. */
  private val reported = CopyOnWriteArrayList<Int>()

  @Before fun start() {
    server = LocalImageServer()
    loader = Landscapist.builder().noDiskCache().build()
  }

  @After fun stop() = server.close()

  /** Small enough that nothing is downsampled, so a decoded width is the width served. */
  private fun serveNamed(path: String, width: Int, gate: CountDownLatch? = null) {
    server.serve(path, ImageFixtures.solid(width, IMAGE_HEIGHT, Color.RED), gate = gate)
  }

  private fun record(state: LandscapistImageState) {
    if (state is LandscapistImageState.Success) reported += state.originalWidth
  }

  /** Loads [url] into the memory cache before any composable exists. */
  private fun warm(url: String) {
    val result = runBlocking {
      loader.load(
        ImageRequest.builder().model(url).diskCachePolicy(CachePolicy.DISABLED).build(),
      ).first { it is ImageResult.Success || it is ImageResult.Failure }
    }
    assertTrue("could not warm $url into the memory cache: $result", result is ImageResult.Success)
  }

  /** Fails rather than hanging when the image never reaches the caller. */
  private fun awaitReported(width: Int) {
    try {
      compose.waitUntil(LOAD_TIMEOUT_MS) { reported.contains(width) }
    } catch (timeout: ComposeTimeoutException) {
      throw AssertionError("the ${width}px image never reached the caller, saw $reported", timeout)
    }
    compose.waitForIdle()
  }

  @Test
  fun changingTheModelLoadsTheNewImageAndReportsIt() {
    serveNamed(FIRST, FIRST_WIDTH)
    serveNamed(SECOND, SECOND_WIDTH)
    val first = server.url(FIRST)
    val second = server.url(SECOND)
    var model by mutableStateOf(first)

    compose.setContent {
      LandscapistImage(
        imageModel = { model },
        landscapist = loader,
        modifier = Modifier.size(SLOT_DP.dp),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        onImageStateChanged = { record(it) },
      )
    }
    awaitReported(FIRST_WIDTH)
    compose.runOnIdle { model = second }
    awaitReported(SECOND_WIDTH)

    assertEquals(
      "the call site did not move from the first image to the second",
      listOf(FIRST_WIDTH, SECOND_WIDTH),
      reported.distinct(),
    )
    assertEquals("the second image was never fetched", 1, server.hitCount(SECOND))
  }

  @Test
  fun aReusedNodeDoesNotPublishThePreviousRowsImage() {
    serveNamed(FIRST, FIRST_WIDTH)
    serveNamed(SECOND, SECOND_WIDTH)
    val first = server.url(FIRST)
    val second = server.url(SECOND)
    // Both already in memory, so the reused node could read either of them synchronously.
    warm(first)
    warm(second)
    var model by mutableStateOf(first)

    compose.setContent {
      // Reused rather than disposed and rebuilt, which a plain state change would not do.
      ReusableContent(model) {
        LandscapistImage(
          imageModel = { model },
          landscapist = loader,
          modifier = Modifier.size(SLOT_DP.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { record(it) },
        )
      }
    }
    awaitReported(FIRST_WIDTH)
    reported.clear()
    compose.runOnIdle { model = second }
    awaitReported(SECOND_WIDTH)

    assertEquals(
      "the reused node published the previous row's image",
      listOf(SECOND_WIDTH),
      reported.distinct(),
    )
  }

  @Test
  fun aLoadLeftBehindByARebindDoesNotPublishOverTheImageThatReplacedIt() {
    val gate = CountDownLatch(1)
    serveNamed(SLOW, FIRST_WIDTH, gate = gate)
    serveNamed(SECOND, SECOND_WIDTH)
    val slow = server.url(SLOW)
    val quick = server.url(SECOND)
    var model by mutableStateOf(slow)

    try {
      compose.setContent {
        LandscapistImage(
          imageModel = { model },
          landscapist = loader,
          modifier = Modifier.size(SLOT_DP.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { record(it) },
        )
      }
      compose.waitForIdle()
      // Rebound before the first image arrives, as a scrolling list does.
      compose.runOnIdle { model = quick }
      awaitReported(SECOND_WIDTH)
    } finally {
      // Released here so the server thread is never left holding the response open.
      gate.countDown()
    }
    // Let the abandoned load finish; nothing it publishes may reach the composable.
    repeat(SETTLE_ROUNDS) {
      compose.waitForIdle()
      Thread.sleep(SETTLE_MS)
    }

    assertTrue(
      "the stalled image was never requested, so no load was left behind to publish anything",
      server.hitCount(SLOW) > 0,
    )
    assertEquals(
      "the load left behind by the rebind published over the image that replaced it",
      listOf(SECOND_WIDTH),
      reported.distinct(),
    )
  }

  @Test
  fun everyRowOfARecycledListShowsItsOwnImage() {
    repeat(ROWS) { serveNamed(rowPath(it), ROW_BASE_WIDTH + it) }
    val seen = ConcurrentHashMap<Int, CopyOnWriteArrayList<Int>>()
    // Held by the test rather than remembered, so it can read what is on screen at any point.
    val listState = LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)

    compose.setContent {
      LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(LIST)) {
        items(count = ROWS, key = { it }) { index ->
          LandscapistImage(
            imageModel = { server.url(rowPath(index)) },
            landscapist = loader,
            modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT_DP.dp),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
            onImageStateChanged = { state ->
              if (state is LandscapistImageState.Success) {
                seen.getOrPut(index) { CopyOnWriteArrayList() } += state.originalWidth
              }
            },
          )
        }
      }
    }
    compose.waitForIdle()
    val firstScreen = visibleRows(listState)
    awaitRows(firstScreen, seen)
    var target = SCROLL_STEP
    while (target < ROWS) {
      compose.onNodeWithTag(LIST).performScrollToIndex(target)
      compose.waitForIdle()
      awaitRows(visibleRows(listState), seen)
      target += SCROLL_STEP
    }
    val lastScreen = visibleRows(listState)

    assertTrue("the list never laid out a row", firstScreen.isNotEmpty())
    assertTrue(
      "the list never left the rows it started on, so nothing was ever recycled",
      lastScreen.none { it in firstScreen },
    )
    assertTrue(
      "only ${seen.size} rows ever loaded, no more than the ${firstScreen.size} on the first " +
        "screen, so no row was recycled into another",
      seen.size > firstScreen.size,
    )
    for (row in seen.keys.sorted()) {
      assertEquals(
        "row $row was handed another row's image",
        listOf(ROW_BASE_WIDTH + row),
        seen.getValue(row).distinct(),
      )
    }
  }

  private fun rowPath(index: Int): String = "/row-$index.jpg"

  private fun visibleRows(state: LazyListState): List<Int> =
    compose.runOnIdle { state.layoutInfo.visibleItemsInfo.map { it.index } }

  /** Fails rather than hanging when a row on screen never gets an image. */
  private fun awaitRows(rows: List<Int>, seen: Map<Int, List<Int>>) {
    try {
      compose.waitUntil(LOAD_TIMEOUT_MS) { rows.all { !seen[it].isNullOrEmpty() } }
    } catch (timeout: ComposeTimeoutException) {
      val missing = rows.filter { seen[it].isNullOrEmpty() }
      throw AssertionError("rows $missing were on screen and never got an image", timeout)
    }
    compose.waitForIdle()
  }

  private companion object {
    const val FIRST = "/first.jpg"
    const val SECOND = "/second.jpg"
    const val SLOW = "/slow.jpg"
    const val LIST = "recycledList"
    const val FIRST_WIDTH = 40
    const val SECOND_WIDTH = 64
    const val IMAGE_HEIGHT = 32
    const val SLOT_DP = 120
    const val ROWS = 40
    const val ROW_BASE_WIDTH = 40
    const val ROW_HEIGHT_DP = 100
    const val SCROLL_STEP = 6
    const val SETTLE_ROUNDS = 5
    const val SETTLE_MS = 60L
    const val LOAD_TIMEOUT_MS = 20_000L
  }
}
