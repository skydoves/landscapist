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

import android.os.Debug
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.fresco.FrescoImage
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Comprehensive performance comparison with 5 rounds of testing for each library.
 *
 * Run with:
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.ComprehensivePerformanceTest
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ComprehensivePerformanceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val allResults = mutableMapOf<String, MutableList<RoundResult>>()

  companion object {
    private const val ROUNDS = 5
    private val TEST_IMAGES = listOf(
      "https://user-images.githubusercontent.com/24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
      "https://user-images.githubusercontent.com/24237865/75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
      "https://user-images.githubusercontent.com/24237865/75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
      "https://user-images.githubusercontent.com/24237865/75087932-5aec7280-553e-11ea-9301-3b12ddaae0a7.jpg",
    )
  }

  @Before
  fun setup() {
    allResults.clear()
    println("\n" + "=".repeat(100))
    println("COMPREHENSIVE PERFORMANCE COMPARISON TEST - $ROUNDS ROUNDS")
    println("Testing network image loading (non-cached) for each library")
    println("=".repeat(100))
    println()
  }

  @After
  fun printResults() {
    println("\n" + "=".repeat(100))
    println("FINAL RESULTS - AVERAGED OVER $ROUNDS ROUNDS")
    println("=".repeat(100))
    println()

    // Calculate statistics for each library
    val stats = allResults.mapValues { (_, results) ->
      LibraryStats(
        avgLoadTime = results.map { it.loadTimeMs }.average(),
        minLoadTime = results.minOf { it.loadTimeMs },
        maxLoadTime = results.maxOf { it.loadTimeMs },
        avgMemory = results.map { it.memoryKb }.average(),
        minMemory = results.minOf { it.memoryKb },
        maxMemory = results.maxOf { it.memoryKb },
        successRate = results.count { it.success } * 100.0 / results.size
      )
    }

    // Print detailed table
    println(String.format(
      "%-20s | %-12s | %-12s | %-12s | %-12s | %-12s | %-10s",
      "Library", "Avg Time", "Min Time", "Max Time", "Avg Memory", "Min Memory", "Success Rate"
    ))
    println("-".repeat(120))

    stats.entries.sortedBy { it.value.avgLoadTime }.forEach { (library, stat) ->
      println(String.format(
        "%-20s | %-12s | %-12s | %-12s | %-12s | %-12s | %-10s",
        library,
        "${stat.avgLoadTime.toInt()}ms",
        "${stat.minLoadTime}ms",
        "${stat.maxLoadTime}ms",
        "${stat.avgMemory.toInt()}KB",
        "${stat.minMemory}KB",
        "${stat.successRate.toInt()}%"
      ))
    }

    println()
    println("=".repeat(100))
    println("DETAILED ROUND-BY-ROUND RESULTS")
    println("=".repeat(100))
    println()

    allResults.forEach { (library, results) ->
      println("$library:")
      results.forEachIndexed { index, result ->
        println(String.format(
          "  Round %d: %dms, %dKB, %s",
          index + 1,
          result.loadTimeMs,
          result.memoryKb,
          if (result.success) "✓" else "✗"
        ))
      }
      println()
    }

    println("=".repeat(100))
    println("PERFORMANCE RANKING")
    println("=".repeat(100))
    println()

    val fastestByAvg = stats.entries.sortedBy { it.value.avgLoadTime }
    val lowestMemory = stats.entries.sortedBy { it.value.avgMemory }

    println("Fastest (by average load time):")
    fastestByAvg.forEachIndexed { index, (library, stat) ->
      val diff = if (index > 0) {
        val baseline = fastestByAvg[0].value.avgLoadTime
        val percent = ((stat.avgLoadTime - baseline) / baseline * 100).toInt()
        " (+${percent}%)"
      } else {
        " (baseline)"
      }
      println("  ${index + 1}. $library: ${stat.avgLoadTime.toInt()}ms$diff")
    }

    println()
    println("Lowest memory usage:")
    lowestMemory.forEachIndexed { index, (library, stat) ->
      val diff = if (index > 0) {
        val baseline = lowestMemory[0].value.avgMemory
        val percent = ((stat.avgMemory - baseline) / baseline * 100).toInt()
        " (+${percent}%)"
      } else {
        " (baseline)"
      }
      println("  ${index + 1}. $library: ${stat.avgMemory.toInt()}KB$diff")
    }

    println()
    println("=".repeat(100))
  }

  @Test
  fun runComprehensivePerformanceTest() {
    println("Starting comprehensive performance test with $ROUNDS rounds per library...\n")

    // Test each library multiple rounds
    repeat(ROUNDS) { round ->
      println("\n" + "-".repeat(100))
      println("ROUND ${round + 1}/$ROUNDS")
      println("-".repeat(100) + "\n")

      // Clear caches before each round
      clearAllCaches()
      Thread.sleep(2000)

      // Test each library in this round
      testLibraryRound("GlideImage", round) { roundIndex ->
        testGlideImage(TEST_IMAGES[roundIndex % TEST_IMAGES.size])
      }

      clearAllCaches()
      Thread.sleep(2000)

      testLibraryRound("CoilImage", round) { roundIndex ->
        testCoilImage(TEST_IMAGES[roundIndex % TEST_IMAGES.size])
      }

      clearAllCaches()
      Thread.sleep(2000)

      testLibraryRound("LandscapistImage", round) { roundIndex ->
        testLandscapistImage(TEST_IMAGES[roundIndex % TEST_IMAGES.size])
      }

      clearAllCaches()
      Thread.sleep(2000)

      testLibraryRound("FrescoImage", round) { roundIndex ->
        testFrescoImage(TEST_IMAGES[roundIndex % TEST_IMAGES.size])
      }

      println("\nRound ${round + 1} completed.")
    }
  }

  private fun testLibraryRound(
    libraryName: String,
    round: Int,
    test: (roundIndex: Int) -> RoundResult
  ) {
    println("[$libraryName] Round ${round + 1}...")
    val result = test(round)

    allResults.getOrPut(libraryName) { mutableListOf() }.add(result)

    println("  ✓ Time: ${result.loadTimeMs}ms, Memory: ${result.memoryKb}KB, Success: ${result.success}")
  }

  private fun testGlideImage(imageUrl: String): RoundResult {
    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.apply {
        setContent {
          GlideImage(
            imageModel = { imageUrl },
            modifier = Modifier
              .size(300.dp)
              .testTag("GlideImage"),
            imageOptions = ImageOptions(),
          )
        }

        waitForIdle()
        Thread.sleep(3000)  // Wait for network load
        success = true
      }
    }

    val endMemory = getUsedMemoryKb()

    return RoundResult(
      loadTimeMs = loadTime,
      memoryKb = (endMemory - startMemory).coerceAtLeast(0),
      success = success
    )
  }

  private fun testCoilImage(imageUrl: String): RoundResult {
    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.apply {
        setContent {
          CoilImage(
            imageModel = { imageUrl },
            modifier = Modifier
              .size(300.dp)
              .testTag("CoilImage"),
            imageOptions = ImageOptions(),
          )
        }

        waitForIdle()
        Thread.sleep(3000)
        success = true
      }
    }

    val endMemory = getUsedMemoryKb()

    return RoundResult(
      loadTimeMs = loadTime,
      memoryKb = (endMemory - startMemory).coerceAtLeast(0),
      success = success
    )
  }

  private fun testLandscapistImage(imageUrl: String): RoundResult {
    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.apply {
        setContent {
          LandscapistImage(
            imageModel = { imageUrl },
            modifier = Modifier
              .size(300.dp)
              .testTag("LandscapistImage"),
            imageOptions = ImageOptions(),
            onImageStateChanged = { state ->
              imageState = state
            }
          )
        }

        waitForIdle()
        waitUntil(timeoutMillis = 10_000) {
          imageState is LandscapistImageState.Success || imageState is LandscapistImageState.Failure
        }

        success = imageState is LandscapistImageState.Success
        Thread.sleep(500)
      }
    }

    val endMemory = getUsedMemoryKb()

    return RoundResult(
      loadTimeMs = loadTime,
      memoryKb = (endMemory - startMemory).coerceAtLeast(0),
      success = success
    )
  }

  private fun testFrescoImage(imageUrl: String): RoundResult {
    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.apply {
        setContent {
          FrescoImage(
            imageUrl = imageUrl,
            modifier = Modifier
              .size(300.dp)
              .testTag("FrescoImage"),
            imageOptions = ImageOptions(),
          )
        }

        waitForIdle()
        Thread.sleep(3000)
        success = true
      }
    }

    val endMemory = getUsedMemoryKb()

    return RoundResult(
      loadTimeMs = loadTime,
      memoryKb = (endMemory - startMemory).coerceAtLeast(0),
      success = success
    )
  }

  private fun clearAllCaches() {
    try {
      Glide.get(InstrumentationRegistry.getInstrumentation().targetContext).clearMemory()
      Runtime.getRuntime().gc()
      System.gc()
      Thread.sleep(500)
    } catch (e: Exception) {
      println("Warning: Could not clear caches: ${e.message}")
    }
  }

  private fun getUsedMemoryKb(): Long {
    Runtime.getRuntime().gc()
    Thread.sleep(100)
    val memInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memInfo)
    return memInfo.totalPss.toLong()
  }

  private data class RoundResult(
    val loadTimeMs: Long,
    val memoryKb: Long,
    val success: Boolean
  )

  private data class LibraryStats(
    val avgLoadTime: Double,
    val minLoadTime: Long,
    val maxLoadTime: Long,
    val avgMemory: Double,
    val minMemory: Long,
    val maxMemory: Long,
    val successRate: Double
  )
}
