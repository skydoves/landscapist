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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.github.skydoves.landscapistdemo.measure.DeviceMeasure.formatBytes
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.image.LandscapistImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import coil3.request.ImageRequest as CoilRequest

/**
 * Allocation while a list scrolls, one loader per process.
 *
 * This class had no process guard at all while running both loaders in one process, which is the
 * ordering problem [DeviceMeasure.claimTheProcess] exists for. It also gave landscapist a 64 MiB
 * memory cache and Coil 32 MiB, and reported a number without ever checking that the list moved.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollComparisonTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  // Long enough that the measured swipes cannot reach the end of it. A list that bottoms out
  // stops doing work, and the swipes after that are measuring an idle list.
  private val items = 240
  private val rowHeight = 90

  @Before
  fun start() {
    server = LocalImageServer()
    // One encoding, served at [items] paths: the fixture is deterministic, so distinct bytes were
    // never distinct, and distinct urls are what keep the caches from sharing an entry.
    val body = ImageFixtures.photo(360, 270)
    repeat(items) { server.serve("/row-$it.jpg", body) }
  }

  @After
  fun stop() = server.close()

  private fun urls() = List(items) { server.url("/row-$it.jpg") }

  @Test
  fun scrollLandscapist() {
    DeviceMeasure.claimTheProcess("landscapist scroll")
    val loader = Landscapist.builder()
      .config(LandscapistConfig(memoryCacheSize = DeviceMeasure.MEMORY_CACHE_BYTES))
      .noDiskCache()
      .build()
    val urls = urls()
    val state = LazyListState(0, 0)
    measureScroll("landscapist", state) {
      LazyColumn(state = state) {
        items(urls) { url ->
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            modifier = Modifier.fillMaxWidth().height(rowHeight.dp),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
  }

  @Test
  fun scrollCoil() {
    DeviceMeasure.claimTheProcess("coil scroll")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val loader = ImageLoader.Builder(context)
      .memoryCache {
        MemoryCache.Builder().maxSizeBytes(DeviceMeasure.MEMORY_CACHE_BYTES).build()
      }
      .diskCache(null)
      .build()
    val urls = urls()
    val state = LazyListState(0, 0)
    measureScroll("coil", state) {
      LazyColumn(state = state) {
        items(urls) { url ->
          AsyncImage(
            model = CoilRequest.Builder(context).data(url).build(),
            imageLoader = loader,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(rowHeight.dp),
          )
        }
      }
    }
  }

  /** Scrolled first to fill the caches, so the measured pass is over images already held. */
  private fun measureScroll(
    label: String,
    state: LazyListState,
    content: @Composable () -> Unit,
  ) {
    compose.setContent { content() }
    compose.waitForIdle()
    val resting = compose.runOnIdle { lastVisibleIndex(state) }

    val warmDepth = swipeThereAndBack(state, WARM_ROUNDS)
    check(warmDepth > resting) {
      "$label did not scroll during the warm up: still showing item $warmDepth of $items"
    }
    DeviceMeasure.settle()
    // Counted from here, so what follows is what the measured pass itself asked for rather than
    // anything the warm up left behind.
    server.resetCounts()

    var depth = 0
    val allocated = DeviceMeasure.allocatedDuring {
      depth = swipeThereAndBack(state, MEASURED_ROUNDS)
    }

    check(depth > resting) {
      "$label did not scroll during the measured pass: still showing item $depth of $items"
    }
    // A swipe that runs out of list stops doing work, so the row would be measuring an idle list.
    check(depth < items - 1) {
      "$label reached the last item, so the later swipes had nothing left to scroll"
    }
    // Nothing here checked that either arm ever loaded an image. An arm whose loads all failed
    // would have reported the allocation of scrolling empty rows and won the row on that, so the
    // number was not falsifiable in the direction that flatters it.
    val rowsServed = (0 until items).count { server.hitCount("/row-$it.jpg") > 0 }
    check(rowsServed >= MIN_ROWS_SERVED) {
      "$label had only $rowsServed of $items rows served during the measured pass, fewer than " +
        "the $MIN_ROWS_SERVED it takes to call this a scroll over images: the allocation is of a " +
        "list that scrolled without loading anything"
    }

    DeviceMeasure.report(
      "scroll ${MEASURED_ROUNDS * 2} swipes over $items rows, $label",
      "allocated ${allocated.formatBytes()}, $rowsServed of $items rows served",
    )
    check(allocated > 0) { "the allocation counter is not available on this device" }
  }

  /** Swipes down the list and back up, returning the deepest item index it reached. */
  private fun swipeThereAndBack(state: LazyListState, rounds: Int): Int {
    var deepest = 0
    repeat(rounds) {
      compose.onRoot().performTouchInput { swipeUp() }
      compose.waitForIdle()
      deepest = maxOf(deepest, compose.runOnIdle { lastVisibleIndex(state) })
    }
    repeat(rounds) {
      compose.onRoot().performTouchInput { swipeDown() }
      compose.waitForIdle()
    }
    return deepest
  }

  private fun lastVisibleIndex(state: LazyListState): Int =
    state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

  private companion object {
    const val WARM_ROUNDS = 3
    const val MEASURED_ROUNDS = 4

    /**
     * Distinct rows the measured pass has to have fetched for its allocation to mean anything.
     *
     * A quarter of the list. Both arms clear this by a wide margin because 240 rows of 360x270 do
     * not fit in the memory cache either loader is given, so the pass re-fetches as it goes.
     */
    const val MIN_ROWS_SERVED = 60
  }
}
