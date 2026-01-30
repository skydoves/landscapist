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

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance comparison test between LandscapistImage and direct Coil3.
 *
 * This test reproduces the investigation from GitHub issue #830:
 * https://github.com/skydoves/landscapist/issues/830
 *
 * The user observed that cached images load at 0ms with direct Coil3
 * but ~50ms with LandscapistImage.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LandscapistVsCoil3PerformanceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val TAG = "PerfComparison"

    // Test image URL - 600x600 JPG as mentioned in the issue
    private const val TEST_IMAGE_URL = "https://user-images.githubusercontent.com/" +
      "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"

    private val TEST_IMAGES = listOf(
      "https://user-images.githubusercontent.com/24237865/" +
        "75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
    )
  }

  /**
   * Test: Compare cached image load performance - the main issue
   * This measures time from composition start to success callback
   */
  @Test
  fun compareCachedLoadPerformance() {
    var directCoil3Time by mutableLongStateOf(0L)
    var landscapistTime by mutableLongStateOf(0L)
    var directCoil3Done by mutableStateOf(false)
    var landscapistDone by mutableStateOf(false)

    val latch = CountDownLatch(2)

    composeTestRule.setContent {
      Column {
        // Test Direct Coil3
        DirectCoil3Test(
          url = TEST_IMAGE_URL,
          onComplete = { time ->
            directCoil3Time = time
            directCoil3Done = true
            latch.countDown()
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Test LandscapistImage
        LandscapistTest(
          url = TEST_IMAGE_URL,
          onComplete = { time ->
            landscapistTime = time
            landscapistDone = true
            latch.countDown()
          },
        )
      }
    }

    // Wait for both tests to complete
    latch.await(30, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    // Print results
    println("\n" + "=".repeat(80))
    println("PERFORMANCE COMPARISON: LandscapistImage vs Direct Coil3")
    println("=".repeat(80))
    println()
    println(String.format("%-25s | %s", "Library", "Time to Success"))
    println("-".repeat(50))
    println(String.format("%-25s | %dms", "Direct Coil3", directCoil3Time))
    println(String.format("%-25s | %dms", "LandscapistImage", landscapistTime))
    println("-".repeat(50))

    val overhead = landscapistTime - directCoil3Time
    val ratio = if (directCoil3Time > 0) landscapistTime.toDouble() / directCoil3Time else 0.0

    println(String.format("Overhead: %dms (%.2fx)", overhead, ratio))
    println("=".repeat(80))

    // Assertions
    assert(directCoil3Done) { "Direct Coil3 test did not complete" }
    assert(landscapistDone) { "Landscapist test did not complete" }
  }

  /**
   * Test: Multiple iterations for more accurate measurements
   */
  @Test
  fun compareMultipleImagesPerformance() {
    val directCoil3Times = mutableListOf<Long>()
    val landscapistTimes = mutableListOf<Long>()
    val completedCount = mutableStateOf(0)

    val latch = CountDownLatch(TEST_IMAGES.size * 2)

    composeTestRule.setContent {
      Column {
        TEST_IMAGES.forEachIndexed { index, url ->
          Row {
            // Direct Coil3
            DirectCoil3Test(
              url = url,
              tag = "direct-$index",
              onComplete = { time ->
                synchronized(directCoil3Times) {
                  directCoil3Times.add(time)
                }
                latch.countDown()
              },
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Landscapist
            LandscapistTest(
              url = url,
              tag = "landscapist-$index",
              onComplete = { time ->
                synchronized(landscapistTimes) {
                  landscapistTimes.add(time)
                }
                latch.countDown()
              },
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }

    // Wait for all tests to complete
    latch.await(60, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    // Calculate averages
    val avgDirectCoil3 = directCoil3Times.average()
    val avgLandscapist = landscapistTimes.average()

    // Print results
    println("\n" + "=".repeat(80))
    println("MULTIPLE IMAGES PERFORMANCE (${TEST_IMAGES.size} images)")
    println("=".repeat(80))
    println()
    println("Individual Results:")
    println("-".repeat(50))
    TEST_IMAGES.indices.forEach { i ->
      val directTime = directCoil3Times.getOrNull(i) ?: 0L
      val landscapistTime = landscapistTimes.getOrNull(i) ?: 0L
      println(
        String.format(
          "  Image %d: Direct=%dms, Landscapist=%dms",
          i + 1,
          directTime,
          landscapistTime,
        ),
      )
    }
    println()
    println("Averages:")
    println("-".repeat(50))
    println(String.format("%-25s | %.1fms", "Direct Coil3 (avg)", avgDirectCoil3))
    println(String.format("%-25s | %.1fms", "Landscapist (avg)", avgLandscapist))
    println("-".repeat(50))
    println(String.format("Average Overhead: %.1fms", avgLandscapist - avgDirectCoil3))
    println("=".repeat(80))
  }

  /**
   * Test: Detailed time-to-first-bitmap measurement
   */
  @Test
  fun measureTimeToFirstBitmap() {
    var directStartTime by mutableLongStateOf(0L)
    var directSuccessTime by mutableLongStateOf(0L)
    var directBitmapInfo by mutableStateOf("")

    var landscapistStartTime by mutableLongStateOf(0L)
    var landscapistSuccessTime by mutableLongStateOf(0L)
    var landscapistBitmapInfo by mutableStateOf("")

    val latch = CountDownLatch(2)

    composeTestRule.setContent {
      Column {
        // Direct Coil3 with detailed timing
        DirectCoil3DetailedTest(
          url = TEST_IMAGE_URL,
          onStart = { directStartTime = System.currentTimeMillis() },
          onSuccess = { time, info ->
            directSuccessTime = time
            directBitmapInfo = info
            latch.countDown()
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Landscapist with detailed timing
        LandscapistDetailedTest(
          url = TEST_IMAGE_URL,
          onStart = { landscapistStartTime = System.currentTimeMillis() },
          onSuccess = { time, info ->
            landscapistSuccessTime = time
            landscapistBitmapInfo = info
            latch.countDown()
          },
        )
      }
    }

    latch.await(30, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    val directElapsed = directSuccessTime - directStartTime
    val landscapistElapsed = landscapistSuccessTime - landscapistStartTime

    println("\n" + "=".repeat(80))
    println("DETAILED TIME TO FIRST BITMAP")
    println("=".repeat(80))
    println()
    println("Direct Coil3:")
    println("  Start time:      $directStartTime")
    println("  Success time:    $directSuccessTime")
    println("  Elapsed:         ${directElapsed}ms")
    println("  Bitmap info:     $directBitmapInfo")
    println()
    println("LandscapistImage:")
    println("  Start time:      $landscapistStartTime")
    println("  Success time:    $landscapistSuccessTime")
    println("  Elapsed:         ${landscapistElapsed}ms")
    println("  Bitmap info:     $landscapistBitmapInfo")
    println()
    println("-".repeat(50))
    println("Difference: ${landscapistElapsed - directElapsed}ms overhead")
    println("=".repeat(80))
  }

  /**
   * Test: First load performance (network)
   */
  @Test
  fun compareFirstLoadPerformance() {
    var directCoil3Time by mutableLongStateOf(0L)
    var landscapistTime by mutableLongStateOf(0L)

    val latch = CountDownLatch(2)

    composeTestRule.setContent {
      Column {
        // Direct Coil3 (disable cache for first-load test)
        DirectCoil3Test(
          url = TEST_IMAGE_URL,
          cacheEnabled = true, // Cache should be warm from previous runs
          onComplete = { time ->
            directCoil3Time = time
            latch.countDown()
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // LandscapistImage
        LandscapistTest(
          url = TEST_IMAGE_URL,
          onComplete = { time ->
            landscapistTime = time
            latch.countDown()
          },
        )
      }
    }

    latch.await(30, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    println("\n" + "=".repeat(80))
    println("FIRST LOAD PERFORMANCE")
    println("=".repeat(80))
    println()
    println(String.format("%-25s | %dms", "Direct Coil3", directCoil3Time))
    println(String.format("%-25s | %dms", "LandscapistImage", landscapistTime))
    println("-".repeat(50))
    println(String.format("Overhead: %dms", landscapistTime - directCoil3Time))
    println("=".repeat(80))
  }

  @Composable
  private fun DirectCoil3Test(
    url: String,
    tag: String = "direct-coil3",
    cacheEnabled: Boolean = true,
    onComplete: (Long) -> Unit,
  ) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    var startTime by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    val cachePolicy = if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(cachePolicy)
        .diskCachePolicy(cachePolicy)
        .build(),
      imageLoader = imageLoader,
    )

    val state by painter.state.collectAsState()

    LaunchedEffect(Unit) {
      startTime = System.currentTimeMillis()
    }

    LaunchedEffect(state) {
      if (state is AsyncImagePainter.State.Success && !completed) {
        completed = true
        val elapsed = System.currentTimeMillis() - startTime
        onComplete(elapsed)
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(200.dp).testTag(tag),
      contentScale = ContentScale.Crop,
    )
  }

  @Composable
  private fun LandscapistTest(
    url: String,
    tag: String = "landscapist",
    onComplete: (Long) -> Unit,
  ) {
    var startTime by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      startTime = System.currentTimeMillis()
    }

    LandscapistImage(
      imageModel = { url },
      modifier = Modifier.size(200.dp).testTag(tag),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !completed) {
          completed = true
          val elapsed = System.currentTimeMillis() - startTime
          onComplete(elapsed)
        }
      },
    )
  }

  @Composable
  private fun DirectCoil3DetailedTest(
    url: String,
    onStart: () -> Unit,
    onSuccess: (Long, String) -> Unit,
  ) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    var completed by remember { mutableStateOf(false) }

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build(),
      imageLoader = imageLoader,
    )

    val state by painter.state.collectAsState()

    LaunchedEffect(Unit) {
      onStart()
    }

    LaunchedEffect(state) {
      if (state is AsyncImagePainter.State.Success && !completed) {
        completed = true
        val successState = state as AsyncImagePainter.State.Success
        val info = "${successState.result.image.width}x${successState.result.image.height}"
        onSuccess(System.currentTimeMillis(), info)
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(200.dp).testTag("direct-detailed"),
      contentScale = ContentScale.Crop,
    )
  }

  @Composable
  private fun LandscapistDetailedTest(
    url: String,
    onStart: () -> Unit,
    onSuccess: (Long, String) -> Unit,
  ) {
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      onStart()
    }

    LandscapistImage(
      imageModel = { url },
      modifier = Modifier.size(200.dp).testTag("landscapist-detailed"),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !completed) {
          completed = true
          val info = "${state.originalWidth}x${state.originalHeight}"
          onSuccess(System.currentTimeMillis(), info)
        }
      },
    )
  }

  // ===== RESOLUTION-BASED PERFORMANCE TESTS =====
  // These tests compare performance across different image resolutions

  /**
   * Test: Compare performance across different image resolutions (600x600, 1200x1200, 3000x3000)
   * Uses picsum.photos for consistent image delivery at exact dimensions.
   * All resolutions are tested sequentially using key() to force recomposition.
   */
  @Test
  fun comparePerformanceByResolution() {
    val resolutions = listOf(
      "600x600" to "https://picsum.photos/600/600",
      "1200x1200" to "https://picsum.photos/1200/1200",
      "3000x3000" to "https://picsum.photos/3000/3000",
    )

    // resolution -> (coil3Ms, landscapistMs)
    val results = mutableMapOf<String, Pair<Double, Double>>()
    var currentIndex by mutableStateOf(0)
    val coil3TimeNanos = AtomicLong(0L)
    val landscapistTimeNanos = AtomicLong(0L)
    var latch = CountDownLatch(2)

    composeTestRule.setContent {
      val (resolution, baseUrl) = resolutions[currentIndex]
      // Add timestamp to URL to prevent caching between resolutions
      val url = "$baseUrl?idx=$currentIndex&t=${System.currentTimeMillis()}"

      key(currentIndex) {
        Column {
          NetworkCoil3Test(
            url = url,
            tag = "coil3-$resolution",
            disableCache = true,
            onComplete = { nanos ->
              coil3TimeNanos.set(nanos)
              latch.countDown()
            },
          )

          Spacer(modifier = Modifier.height(8.dp))

          NetworkLandscapistTest(
            url = url,
            tag = "landscapist-$resolution",
            onComplete = { nanos ->
              landscapistTimeNanos.set(nanos)
              latch.countDown()
            },
          )
        }
      }
    }

    // Test each resolution
    for (idx in resolutions.indices) {
      if (idx > 0) {
        coil3TimeNanos.set(0L)
        landscapistTimeNanos.set(0L)
        latch = CountDownLatch(2)
        composeTestRule.runOnIdle { currentIndex = idx }
      }

      val done = latch.await(120, TimeUnit.SECONDS)
      composeTestRule.waitForIdle()

      val (resolution, _) = resolutions[idx]
      val coil3Ms = coil3TimeNanos.get() / 1_000_000.0
      val landscapistMs = landscapistTimeNanos.get() / 1_000_000.0

      results[resolution] = Pair(coil3Ms, landscapistMs)
      Log.d(TAG, "Resolution $resolution: Coil3=${coil3Ms}ms, Landscapist=${landscapistMs}ms")

      Thread.sleep(500) // Brief pause between resolutions
    }

    // Print markdown table
    println("\n")
    println("## Performance Comparison by Resolution")
    println("")
    println("| Resolution | Coil3 (ms) | Landscapist (ms) | Difference (ms) | Ratio |")
    println("|------------|------------|------------------|-----------------|-------|")

    for ((resolution, _) in resolutions) {
      val (coil3Ms, landscapistMs) = results[resolution] ?: continue
      val diff = landscapistMs - coil3Ms
      val ratio = if (coil3Ms > 0) landscapistMs / coil3Ms else 0.0
      println(
        String.format(
          "| %s | %.2f | %.2f | %.2f | %.2fx |",
          resolution,
          coil3Ms,
          landscapistMs,
          diff,
          ratio,
        ),
      )
    }
    println("")

    // Verify all tests completed
    assert(results.size == resolutions.size) { "Not all resolution tests completed" }
  }

  /**
   * Test: Multi-round performance comparison by resolution for statistical accuracy
   * Runs 3 rounds per resolution to get more reliable averages.
   */
  @Test
  fun comparePerformanceByResolutionMultiRound() {
    val resolutions = listOf(
      "600x600" to "https://picsum.photos/600/600",
      "1200x1200" to "https://picsum.photos/1200/1200",
      "3000x3000" to "https://picsum.photos/3000/3000",
    )
    val rounds = 3
    val totalTests = resolutions.size * rounds

    // resolution -> list of (coil3Ms, landscapistMs) for each round
    val allResults = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
    resolutions.forEach { (resolution, _) -> allResults[resolution] = mutableListOf() }

    var currentTestIndex by mutableStateOf(0)
    val coil3TimeNanos = AtomicLong(0L)
    val landscapistTimeNanos = AtomicLong(0L)
    var latch = CountDownLatch(2)

    composeTestRule.setContent {
      val resolutionIndex = currentTestIndex / rounds
      val round = currentTestIndex % rounds
      val (resolution, baseUrl) = resolutions[resolutionIndex]
      // Add unique params to bypass cache
      val url = "$baseUrl?round=$round&idx=$currentTestIndex&t=${System.currentTimeMillis()}"

      key(currentTestIndex) {
        Column {
          NetworkCoil3Test(
            url = url,
            tag = "coil3-$resolution-r$round",
            disableCache = true,
            onComplete = { nanos ->
              coil3TimeNanos.set(nanos)
              latch.countDown()
            },
          )

          Spacer(modifier = Modifier.height(8.dp))

          NetworkLandscapistTest(
            url = url,
            tag = "landscapist-$resolution-r$round",
            onComplete = { nanos ->
              landscapistTimeNanos.set(nanos)
              latch.countDown()
            },
          )
        }
      }
    }

    // Run all tests
    for (testIdx in 0 until totalTests) {
      if (testIdx > 0) {
        coil3TimeNanos.set(0L)
        landscapistTimeNanos.set(0L)
        latch = CountDownLatch(2)
        composeTestRule.runOnIdle { currentTestIndex = testIdx }
      }

      val done = latch.await(120, TimeUnit.SECONDS)
      composeTestRule.waitForIdle()

      val resolutionIndex = testIdx / rounds
      val round = testIdx % rounds
      val (resolution, _) = resolutions[resolutionIndex]

      val coil3Ms = coil3TimeNanos.get() / 1_000_000.0
      val landscapistMs = landscapistTimeNanos.get() / 1_000_000.0

      allResults[resolution]?.add(Pair(coil3Ms, landscapistMs))
      Log.d(
        TAG,
        "Resolution $resolution Round $round: Coil3=${coil3Ms}ms, Landscapist=${landscapistMs}ms",
      )

      Thread.sleep(300)
    }

    // Print detailed results
    println("\n")
    println("## Performance Comparison by Resolution (Multi-Round)")
    println("")
    println("### Individual Round Results")
    println("")

    for ((resolution, _) in resolutions) {
      val roundResults = allResults[resolution] ?: continue
      println("**$resolution:**")
      for ((idx, pair) in roundResults.withIndex()) {
        val msg = "- Round ${idx + 1}: Coil3 = %.2fms, Landscapist = %.2fms"
        println(msg.format(pair.first, pair.second))
      }
      println("")
    }

    // Print summary table with averages
    println("### Summary (Averages)")
    println("")
    println("| Resolution | Coil3 Avg (ms) | Landscapist Avg (ms) | Difference (ms) | Ratio |")
    println("|------------|----------------|----------------------|-----------------|-------|")

    for ((resolution, _) in resolutions) {
      val roundResults = allResults[resolution] ?: continue
      val avgCoil3 = roundResults.map { it.first }.average()
      val avgLandscapist = roundResults.map { it.second }.average()
      val diff = avgLandscapist - avgCoil3
      val ratio = if (avgCoil3 > 0) avgLandscapist / avgCoil3 else 0.0

      println(
        String.format(
          "| %s | %.2f | %.2f | %.2f | %.2fx |",
          resolution,
          avgCoil3,
          avgLandscapist,
          diff,
          ratio,
        ),
      )
    }
    println("")
  }

  /**
   * Test: Compare performance across different image resolutions using in-memory bitmaps.
   * This eliminates network variability and tests pure rendering performance.
   */
  @Test
  fun comparePerformanceByResolutionInMemory() {
    val resolutions = listOf(
      "600x600" to (600 to 600),
      "1200x1200" to (1200 to 1200),
      "3000x3000" to (3000 to 3000),
    )
    val rounds = 3
    val totalTests = resolutions.size * rounds

    // resolution -> list of (coil3Ms, landscapistMs) for each round
    val allResults = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
    resolutions.forEach { (resolution, _) -> allResults[resolution] = mutableListOf() }

    var currentTestIndex by mutableStateOf(0)
    val coil3TimeNanos = AtomicLong(0L)
    val landscapistTimeNanos = AtomicLong(0L)
    var latch = CountDownLatch(2)

    composeTestRule.setContent {
      val resolutionIndex = currentTestIndex / rounds
      val round = currentTestIndex % rounds
      val (resolution, dimensions) = resolutions[resolutionIndex]
      val (width, height) = dimensions

      key(currentTestIndex) {
        Column {
          BitmapCoil3Test(
            width = width,
            height = height,
            tag = "coil3-$resolution-r$round",
            onComplete = { nanos ->
              coil3TimeNanos.set(nanos)
              latch.countDown()
            },
          )

          Spacer(modifier = Modifier.height(8.dp))

          BitmapLandscapistTest(
            width = width,
            height = height,
            tag = "landscapist-$resolution-r$round",
            onComplete = { nanos ->
              landscapistTimeNanos.set(nanos)
              latch.countDown()
            },
          )
        }
      }
    }

    // Run all tests
    for (testIdx in 0 until totalTests) {
      if (testIdx > 0) {
        coil3TimeNanos.set(0L)
        landscapistTimeNanos.set(0L)
        latch = CountDownLatch(2)
        composeTestRule.runOnIdle { currentTestIndex = testIdx }
      }

      val done = latch.await(30, TimeUnit.SECONDS)
      composeTestRule.waitForIdle()

      val resolutionIndex = testIdx / rounds
      val round = testIdx % rounds
      val (resolution, _) = resolutions[resolutionIndex]

      val coil3Ms = coil3TimeNanos.get() / 1_000_000.0
      val landscapistMs = landscapistTimeNanos.get() / 1_000_000.0

      allResults[resolution]?.add(Pair(coil3Ms, landscapistMs))
      Log.d(
        TAG,
        "Resolution $resolution Round $round: Coil3=${coil3Ms}ms, Landscapist=${landscapistMs}ms",
      )

      Thread.sleep(200)
    }

    // Print markdown results
    println("\n")
    println("## Performance Comparison by Resolution")
    println("")
    println("### Test Configuration")
    println("- **Rounds per resolution**: $rounds")
    println("- **Image source**: In-memory generated bitmaps")
    println("")
    println("### Individual Round Results")
    println("")

    for ((resolution, _) in resolutions) {
      val roundResults = allResults[resolution] ?: continue
      println("**$resolution:**")
      println("")
      println("| Round | Coil3 (ms) | Landscapist (ms) | Difference (ms) |")
      println("|-------|------------|------------------|-----------------|")
      for ((idx, pair) in roundResults.withIndex()) {
        println(
          "| ${idx + 1} | %.2f | %.2f | %.2f |".format(
            pair.first,
            pair.second,
            pair.second - pair.first,
          ),
        )
      }
      println("")
    }

    // Print summary table
    println("### Summary (Averages)")
    println("")
    println("| Resolution | Coil3 Avg (ms) | Landscapist Avg (ms) | Difference (ms) | Ratio |")
    println("|------------|----------------|----------------------|-----------------|-------|")

    for ((resolution, _) in resolutions) {
      val roundResults = allResults[resolution] ?: continue
      val avgCoil3 = roundResults.map { it.first }.average()
      val avgLandscapist = roundResults.map { it.second }.average()
      val diff = avgLandscapist - avgCoil3
      val ratio = if (avgCoil3 > 0) avgLandscapist / avgCoil3 else 0.0

      println(
        "| %s | %.2f | %.2f | %.2f | %.2fx |".format(
          resolution,
          avgCoil3,
          avgLandscapist,
          diff,
          ratio,
        ),
      )
    }
    println("")
  }

  // ===== In-Memory Bitmap Helper Composables =====

  @Composable
  private fun BitmapCoil3Test(
    width: Int,
    height: Int,
    tag: String,
    onComplete: (Long) -> Unit,
  ) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    var startNanos by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    // Generate bitmap in-memory
    val bitmap = remember(width, height) {
      android.graphics.Bitmap.createBitmap(
        width,
        height,
        android.graphics.Bitmap.Config.ARGB_8888,
      ).apply {
        eraseColor(
          android.graphics.Color.rgb((0..255).random(), (0..255).random(), (0..255).random()),
        )
      }
    }

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context)
        .data(bitmap)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build(),
      imageLoader = imageLoader,
    )

    val state by painter.state.collectAsState()

    LaunchedEffect(Unit) {
      startNanos = System.nanoTime()
    }

    LaunchedEffect(state) {
      if (state is AsyncImagePainter.State.Success && !completed) {
        completed = true
        onComplete(System.nanoTime() - startNanos)
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(200.dp).testTag(tag),
      contentScale = ContentScale.Crop,
    )
  }

  @Composable
  private fun BitmapLandscapistTest(
    width: Int,
    height: Int,
    tag: String,
    onComplete: (Long) -> Unit,
  ) {
    var startNanos by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    // Generate bitmap in-memory
    val bitmap = remember(width, height) {
      android.graphics.Bitmap.createBitmap(
        width,
        height,
        android.graphics.Bitmap.Config.ARGB_8888,
      ).apply {
        eraseColor(
          android.graphics.Color.rgb((0..255).random(), (0..255).random(), (0..255).random()),
        )
      }
    }

    LaunchedEffect(Unit) {
      startNanos = System.nanoTime()
    }

    LandscapistImage(
      imageModel = { bitmap },
      modifier = Modifier.size(200.dp).testTag(tag),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !completed) {
          completed = true
          onComplete(System.nanoTime() - startNanos)
        }
      },
    )
  }

  // ===== Network Image Helper Composables =====

  @Composable
  private fun NetworkCoil3Test(
    url: String,
    tag: String,
    disableCache: Boolean = false,
    onComplete: (Long) -> Unit,
  ) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    var startNanos by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    val cachePolicy = if (disableCache) CachePolicy.DISABLED else CachePolicy.ENABLED

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(cachePolicy)
        .diskCachePolicy(cachePolicy)
        .build(),
      imageLoader = imageLoader,
    )

    val state by painter.state.collectAsState()

    LaunchedEffect(Unit) {
      startNanos = System.nanoTime()
    }

    LaunchedEffect(state) {
      if (state is AsyncImagePainter.State.Success && !completed) {
        completed = true
        onComplete(System.nanoTime() - startNanos)
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(200.dp).testTag(tag),
      contentScale = ContentScale.Crop,
    )
  }

  @Composable
  private fun NetworkLandscapistTest(
    url: String,
    tag: String,
    onComplete: (Long) -> Unit,
  ) {
    var startNanos by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      startNanos = System.nanoTime()
    }

    LandscapistImage(
      imageModel = { url },
      modifier = Modifier.size(200.dp).testTag(tag),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !completed) {
          completed = true
          onComplete(System.nanoTime() - startNanos)
        }
      },
    )
  }

  // ===== LOCAL DRAWABLE RESOURCE PERFORMANCE TESTS =====
  // These tests use local drawable resources to eliminate network variability
  // and provide reliable, reproducible performance measurements.

  /**
   * Compare single drawable resource load: Direct Coil3 vs LandscapistImage.
   * Both composables are rendered side by side in a single setContent call.
   * Uses nanoTime for precision.
   */
  @Test
  fun compareLocalDrawableLoadPerformance() {
    val coil3TimeNanos = AtomicLong(0L)
    val landscapistTimeNanos = AtomicLong(0L)
    val latch = CountDownLatch(2)

    composeTestRule.setContent {
      Column {
        DirectCoil3DrawableTest(
          drawableRes = R.drawable.poster,
          tag = "coil3-local",
          onComplete = { nanos ->
            coil3TimeNanos.set(nanos)
            latch.countDown()
          },
        )

        Spacer(modifier = Modifier.height(8.dp))

        LandscapistDrawableTest(
          drawableRes = R.drawable.poster,
          tag = "landscapist-local",
          onComplete = { nanos ->
            landscapistTimeNanos.set(nanos)
            latch.countDown()
          },
        )
      }
    }

    val allDone = latch.await(10, TimeUnit.SECONDS)

    val coil3Ms = coil3TimeNanos.get() / 1_000_000.0
    val landscapistMs = landscapistTimeNanos.get() / 1_000_000.0
    val overheadMs = landscapistMs - coil3Ms

    Log.d(TAG, "")
    Log.d(TAG, "=".repeat(70))
    Log.d(TAG, "LOCAL DRAWABLE: Single Load Performance")
    Log.d(TAG, "=".repeat(70))
    Log.d(TAG, String.format("%-25s | %.2fms", "Direct Coil3", coil3Ms))
    Log.d(TAG, String.format("%-25s | %.2fms", "LandscapistImage", landscapistMs))
    Log.d(TAG, "-".repeat(50))
    Log.d(TAG, String.format("Overhead: %.2fms", overheadMs))
    Log.d(TAG, "=".repeat(70))

    println("\n" + "=".repeat(70))
    println("LOCAL DRAWABLE: Single Load Performance")
    println("=".repeat(70))
    println(String.format("%-25s | %.2fms", "Direct Coil3", coil3Ms))
    println(String.format("%-25s | %.2fms", "LandscapistImage", landscapistMs))
    println("-".repeat(50))
    println(String.format("Overhead: %.2fms", overheadMs))
    println("=".repeat(70))

    assert(allDone) { "Not all tests completed within timeout" }
  }

  /**
   * Multi-round comparison with local drawables for statistical accuracy.
   * Loads one pair at a time (sequentially) to avoid concurrent load contention.
   * Uses key(currentRound) to force fresh composable instances each round.
   */
  @Test
  fun compareLocalDrawableMultiRound() {
    val rounds = 5
    val coil3NanosList = mutableListOf<Long>()
    val landscapistNanosList = mutableListOf<Long>()
    var currentRound by mutableStateOf(0)
    // Shared atomic values written by callbacks, read by test thread after latch
    val coilNanos = AtomicLong(0L)
    val landscapistNanos = AtomicLong(0L)
    var roundLatch = CountDownLatch(2)
    val pairDone = java.util.concurrent.atomic.AtomicInteger(0)

    composeTestRule.setContent {
      key(currentRound) {
        Column {
          DirectCoil3DrawableTest(
            drawableRes = R.drawable.poster,
            tag = "coil3-round-$currentRound",
            onComplete = { nanos ->
              coilNanos.set(nanos)
              roundLatch.countDown()
            },
          )

          Spacer(modifier = Modifier.height(8.dp))

          LandscapistDrawableTest(
            drawableRes = R.drawable.poster,
            tag = "landscapist-round-$currentRound",
            onComplete = { nanos ->
              landscapistNanos.set(nanos)
              roundLatch.countDown()
            },
          )
        }
      }
    }

    // Wait for initial composition and effects to settle before starting timing loop.
    composeTestRule.waitForIdle()

    // Round 0 starts automatically from initial composition.
    // For subsequent rounds, we set currentRound → key(currentRound) forces recreation.
    for (round in 0 until rounds) {
      if (round > 0) {
        coilNanos.set(0L)
        landscapistNanos.set(0L)
        roundLatch = CountDownLatch(2)
        composeTestRule.runOnIdle { currentRound = round }
        composeTestRule.waitForIdle()
      }

      val done = roundLatch.await(10, TimeUnit.SECONDS)
      Log.d(
        TAG,
        "Round $round done=$done: Coil3=${coilNanos.get() / 1_000_000.0}ms, " +
          "Landscapist=${landscapistNanos.get() / 1_000_000.0}ms",
      )
      coil3NanosList.add(coilNanos.get())
      landscapistNanosList.add(landscapistNanos.get())
    }

    val coil3Ms = coil3NanosList.map { it / 1_000_000.0 }
    val landscapistMs = landscapistNanosList.map { it / 1_000_000.0 }

    val avgCoil3 = coil3Ms.average()
    val avgLandscapist = landscapistMs.average()
    val minCoil3 = coil3Ms.min()
    val minLandscapist = landscapistMs.min()
    val maxCoil3 = coil3Ms.max()
    val maxLandscapist = landscapistMs.max()

    Log.d(TAG, "")
    Log.d(TAG, "=".repeat(70))
    Log.d(TAG, "LOCAL DRAWABLE: Multi-Round Performance ($rounds rounds)")
    Log.d(TAG, "=".repeat(70))
    for (i in 0 until rounds) {
      Log.d(
        TAG,
        String.format(
          "  Round %d: Coil3=%.2fms, Landscapist=%.2fms, diff=%.2fms",
          i + 1,
          coil3Ms[i],
          landscapistMs[i],
          landscapistMs[i] - coil3Ms[i],
        ),
      )
    }
    Log.d(TAG, String.format("%-15s | %10s | %10s | %10s", "", "Avg", "Min", "Max"))
    Log.d(TAG, "-".repeat(55))
    Log.d(
      TAG,
      String.format(
        "%-15s | %8.2fms | %8.2fms | %8.2fms",
        "Direct Coil3",
        avgCoil3,
        minCoil3,
        maxCoil3,
      ),
    )
    Log.d(
      TAG,
      String.format(
        "%-15s | %8.2fms | %8.2fms | %8.2fms",
        "Landscapist",
        avgLandscapist,
        minLandscapist,
        maxLandscapist,
      ),
    )
    Log.d(TAG, String.format("Avg Overhead: %.2fms", avgLandscapist - avgCoil3))
    if (avgCoil3 > 0) Log.d(TAG, String.format("Ratio: %.2fx", avgLandscapist / avgCoil3))
    Log.d(TAG, "=".repeat(70))

    println("\n" + "=".repeat(70))
    println("LOCAL DRAWABLE: Multi-Round Performance ($rounds rounds)")
    println("=".repeat(70))
    for (i in 0 until rounds) {
      println(
        String.format(
          "  Round %d: Coil3=%.2fms, Landscapist=%.2fms, diff=%.2fms",
          i + 1,
          coil3Ms[i],
          landscapistMs[i],
          landscapistMs[i] - coil3Ms[i],
        ),
      )
    }
    println(String.format("%-15s | %10s | %10s | %10s", "", "Avg", "Min", "Max"))
    println("-".repeat(55))
    println(
      String.format(
        "%-15s | %8.2fms | %8.2fms | %8.2fms",
        "Direct Coil3",
        avgCoil3,
        minCoil3,
        maxCoil3,
      ),
    )
    println(
      String.format(
        "%-15s | %8.2fms | %8.2fms | %8.2fms",
        "Landscapist",
        avgLandscapist,
        minLandscapist,
        maxLandscapist,
      ),
    )
    println(String.format("Avg Overhead: %.2fms", avgLandscapist - avgCoil3))
    if (avgCoil3 > 0) println(String.format("Ratio: %.2fx", avgLandscapist / avgCoil3))
    println("=".repeat(70))

    assert(coil3NanosList[0] > 0) { "Coil3 round 0 did not complete" }
    assert(landscapistNanosList[0] > 0) { "Landscapist round 0 did not complete" }
  }

  // ===== Local Drawable Helper Composables =====

  @Composable
  private fun DirectCoil3DrawableTest(
    drawableRes: Int,
    tag: String = "coil3-drawable",
    size: Int = 200,
    onComplete: (Long) -> Unit,
  ) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    var startNanos by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context)
        .data(drawableRes)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build(),
      imageLoader = imageLoader,
    )

    val state by painter.state.collectAsState()

    LaunchedEffect(Unit) {
      startNanos = System.nanoTime()
    }

    LaunchedEffect(state) {
      if (state is AsyncImagePainter.State.Success && !completed) {
        completed = true
        onComplete(System.nanoTime() - startNanos)
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(size.dp).testTag(tag),
      contentScale = ContentScale.Crop,
    )
  }

  @Composable
  private fun LandscapistDrawableTest(
    drawableRes: Int,
    tag: String = "landscapist-drawable",
    size: Int = 200,
    onComplete: (Long) -> Unit,
  ) {
    var startNanos by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      startNanos = System.nanoTime()
    }

    LandscapistImage(
      imageModel = { drawableRes },
      modifier = Modifier.size(size.dp).testTag(tag),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !completed) {
          completed = true
          onComplete(System.nanoTime() - startNanos)
        }
      },
    )
  }
}
