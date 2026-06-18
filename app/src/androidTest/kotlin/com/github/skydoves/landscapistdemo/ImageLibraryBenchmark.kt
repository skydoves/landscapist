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
package com.github.skydoves.landscapistdemo

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.coil3.CoilImageState
import com.skydoves.landscapist.fresco.FrescoImage
import com.skydoves.landscapist.fresco.FrescoImageState
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.glide.GlideImageState
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Fair, symmetric load-time benchmark for the four Landscapist wrappers
 * (CoilImage/Coil3, GlideImage, FrescoImage, LandscapistImage).
 *
 * Why this replaces the previous performance tests: every old test stopped the timer for
 * Glide/Coil/Fresco with a fixed `Thread.sleep(...)` (or a node-exists check, or a latch that was
 * never counted down) and hardcoded `success = true`, while only LandscapistImage waited for its
 * real completion callback. That made the comparison meaningless: the competitors' "load time" was
 * just the sleep constant. This benchmark measures every library identically.
 *
 * Methodology (identical for all four libraries):
 * - The timer runs from the moment the image model changes until that library's own
 *   `onImageStateChanged` reports a terminal state (`Success` or `Failure`) FOR THE CURRENT url.
 *   No sleeps, no hardcoded success. A timeout is recorded as a failed measurement, not thrown away.
 * - A single [androidx.compose.ui.test.junit4.ComposeContentTestRule.setContent] composition per
 *   library is reused; each measured round swaps in a new model, so the timer captures one real
 *   reload. The terminal callback is gated on the url it loaded, so a late callback from the
 *   previous round cannot end the next round early.
 * - One warmup load per library is discarded so first-composition cost is excluded symmetrically.
 * - Each measured load uses a per-run unique URL ([RUN_ID]), so every run is a cold load regardless
 *   of disk-cache state. Within a run, the four libraries each load their own distinct URLs.
 *
 * Two honest caveats for whoever reads the results:
 * - All four composables are Landscapist wrappers, so this compares loaders THROUGH Landscapist's
 *   common render layer (Coil3-via-Landscapist vs landscapist-image), not raw Coil3 vs the core
 *   engine. For an engine-level, wrapper-free comparison see `UnitPerformanceTest`.
 * - Fresco is initialized in `App` with network downsample/resize enabled, so it may decode a
 *   slightly different pixel budget than the others, which size from the layout. This matches how a
 *   Fresco user configures it, but it is not a byte-identical workload.
 *
 * Memory and scrolling-jank are intentionally NOT measured here: a single-shot heap delta is too
 * noisy to publish (it is what produced the old, misleading "0 KB" figure). Use the AndroidX
 * Macrobenchmark in `:benchmark-landscapist` (FrameTimingMetric over a scrolling list) and the
 * Android Studio Memory Profiler for those dimensions.
 *
 * Run on a connected device or emulator:
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.ImageLibraryBenchmark
 * ```
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ImageLibraryBenchmark {

  @get:Rule
  val composeTestRule = createComposeRule()

  /**
   * Drives one library through a warmup load and then [ROUNDS] measured cold reloads at each size
   * in [SIZES]. [content] renders the library's composable, forwarding the current url/size and
   * reporting a terminal result through `onTerminal(loadedUrl, success)`.
   */
  private fun benchmark(
    library: String,
    content: @Composable (url: String, sizeDp: Int, onTerminal: (String, Boolean) -> Unit) -> Unit,
  ) {
    var url by mutableStateOf(imageUrl("${library}_warmup", SIZES.first()))
    var sizeDp by mutableStateOf(SIZES.first())
    // (loadedUrl, success); gating on the url prevents a stale callback from ending a later round.
    var terminal by mutableStateOf<Pair<String, Boolean>?>(null)

    composeTestRule.setContent {
      content(url, sizeDp) { loadedUrl, success -> terminal = loadedUrl to success }
    }

    // Warmup: pay first-composition and engine-init cost once, unmeasured. Swallow a timeout so a
    // slow warmup never aborts the whole benchmark.
    val warmupUrl = url
    composeTestRule.waitForIdle()
    runCatching {
      composeTestRule.waitUntil(LOAD_TIMEOUT_MS) { terminal?.first == warmupUrl }
    }

    for (size in SIZES) {
      for (round in 0 until ROUNDS) {
        terminal = null
        sizeDp = size
        val target = imageUrl("${library}_${size}_$round", size)
        url = target
        composeTestRule.waitForIdle()

        var timedOut = false
        val loadMs = measureTimeMillis {
          try {
            composeTestRule.waitUntil(LOAD_TIMEOUT_MS) { terminal?.first == target }
          } catch (e: ComposeTimeoutException) {
            timedOut = true
          }
        }
        val success = !timedOut && terminal?.second == true
        allResults.add(Measurement(library, size, round, loadMs, success))
      }
    }
  }

  @Test
  fun coil3() = benchmark("CoilImage (Coil3)") { url, sizeDp, onTerminal ->
    CoilImage(
      imageModel = { url },
      modifier = Modifier.size(sizeDp.dp).testTag(BENCH_TAG),
      imageOptions = ImageOptions(),
      onImageStateChanged = { state ->
        when (state) {
          is CoilImageState.Success -> onTerminal(url, true)
          is CoilImageState.Failure -> onTerminal(url, false)
          else -> Unit
        }
      },
    )
  }

  @Test
  fun glide() = benchmark("GlideImage") { url, sizeDp, onTerminal ->
    GlideImage(
      imageModel = { url },
      modifier = Modifier.size(sizeDp.dp).testTag(BENCH_TAG),
      imageOptions = ImageOptions(),
      onImageStateChanged = { state ->
        when (state) {
          is GlideImageState.Success -> onTerminal(url, true)
          is GlideImageState.Failure -> onTerminal(url, false)
          else -> Unit
        }
      },
    )
  }

  @Test
  fun fresco() = benchmark("FrescoImage") { url, sizeDp, onTerminal ->
    FrescoImage(
      imageUrl = url,
      modifier = Modifier.size(sizeDp.dp).testTag(BENCH_TAG),
      imageOptions = ImageOptions(),
      onImageStateChanged = { state ->
        when (state) {
          is FrescoImageState.Success -> onTerminal(url, true)
          is FrescoImageState.Failure -> onTerminal(url, false)
          else -> Unit
        }
      },
    )
  }

  @Test
  fun landscapist() = benchmark("LandscapistImage") { url, sizeDp, onTerminal ->
    LandscapistImage(
      imageModel = { url },
      modifier = Modifier.size(sizeDp.dp).testTag(BENCH_TAG),
      imageOptions = ImageOptions(),
      onImageStateChanged = { state ->
        when (state) {
          is LandscapistImageState.Success -> onTerminal(url, true)
          is LandscapistImageState.Failure -> onTerminal(url, false)
          else -> Unit
        }
      },
    )
  }

  private data class Measurement(
    val library: String,
    val sizeDp: Int,
    val round: Int,
    val loadMs: Long,
    val success: Boolean,
  )

  companion object {
    private const val ROUNDS = 5
    private const val LOAD_TIMEOUT_MS = 20_000L
    private const val BENCH_TAG = "benchImage"
    private val SIZES = listOf(300, 1200)

    /** Unique per test process, so re-running the suite always loads cold (never disk-cached) URLs. */
    private val RUN_ID = UUID.randomUUID().toString().take(8)

    /**
     * Base URL for the image source, overridable with `-e baseUrl <url>` (e.g. a local server via
     * `adb reverse` to remove network noise). Defaults to picsum.photos.
     */
    private val baseUrl: String by lazy {
      InstrumentationRegistry.getArguments().getString("baseUrl")?.trimEnd('/')
        ?: "https://picsum.photos"
    }

    /**
     * Each measured load uses a distinct, cacheable URL so the timing reflects a cold load.
     * `seed` (plus [RUN_ID]) guarantees a unique image; `size` requests a server-resized image so
     * the decode cost tracks the display size. Override [baseUrl] to benchmark against your own CDN.
     */
    private fun imageUrl(seed: String, size: Int): String =
      "$baseUrl/seed/${RUN_ID}_$seed/$size/$size"

    private val allResults = mutableListOf<Measurement>()

    @JvmStatic
    @AfterClass
    fun reportResults() {
      if (allResults.isEmpty()) return

      println("\n" + "=".repeat(78))
      println("IMAGE LIBRARY BENCHMARK  (load time = model change -> terminal state callback)")
      println("=".repeat(78))

      val byLibAndSize = allResults.groupBy { it.library to it.sizeDp }

      SIZES.forEach { size ->
        println("\nDisplay size: ${size}dp")
        println("-".repeat(78))
        println(
          String.format(
            Locale.US,
            "%-22s | %10s | %10s | %10s | %8s",
            "Library",
            "Avg",
            "Min",
            "Max",
            "Success",
          ),
        )
        println("-".repeat(78))
        byLibAndSize.entries
          .filter { it.key.second == size }
          .map { (key, rows) -> key.first to rows }
          .sortedBy { (_, rows) -> rows.map { it.loadMs }.average() }
          .forEach { (library, rows) ->
            val times = rows.map { it.loadMs }
            val successRate = rows.count { it.success } * 100 / rows.size
            println(
              String.format(
                Locale.US,
                "%-22s | %8.0fms | %8dms | %8dms | %6d%%",
                library,
                times.average(),
                times.min(),
                times.max(),
                successRate,
              ),
            )
          }
      }
      println("\n" + "=".repeat(78))
      println(
        "Note: cold-network load time only. Memory and scroll-jank -> :benchmark-landscapist.",
      )
      println("=".repeat(78) + "\n")
      allResults.clear()
    }
  }
}
