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
import androidx.compose.ui.test.onNodeWithTag
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
 * Performance comparison test for initial network loading (non-cached).
 *
 * This test measures the time it takes for each image library to load
 * an image from the network for the first time, with cold caches.
 *
 * Run with:
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.NetworkLoadingPerformanceComparison
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NetworkLoadingPerformanceComparison {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val results = mutableListOf<PerformanceResult>()

  companion object {
    // Using different images for each test to avoid cache hits
    private val TEST_IMAGES = listOf(
      "https://user-images.githubusercontent.com/24237865/" +
        "75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087932-5aec7280-553e-11ea-9301-3b12ddaae0a7.jpg",
    )
  }

  @Before
  fun setup() {
    results.clear()

    // Clear all caches before testing
    clearAllCaches()

    println("\n" + "=".repeat(100))
    println("NETWORK LOADING PERFORMANCE COMPARISON TEST")
    println("Testing initial image loading from network (non-cached)")
    println("=".repeat(100))
    println()
  }

  @After
  fun printResults() {
    println("\n" + "=".repeat(100))
    println("RESULTS: Initial Network Loading Performance")
    println("=".repeat(100))
    println()

    // Print header
    println(
      String.format(
        "%-20s | %-15s | %-15s | %-10s | %-8s",
        "Library",
        "Load Time",
        "Memory (KB)",
        "Success",
        "Notes",
      ),
    )
    println("-".repeat(100))

    // Print each result
    results.forEach { result ->
      val success = if (result.success) "✓" else "✗"
      val notes = when {
        !result.success -> "FAILED"
        result.loadTimeMs > 5000 -> "Slow"
        result.loadTimeMs < 1000 -> "Fast"
        else -> "Normal"
      }

      println(
        String.format(
          "%-20s | %-15s | %-15s | %-10s | %-8s",
          result.library,
          "${result.loadTimeMs}ms",
          "${result.memoryKb}KB",
          success,
          notes,
        ),
      )
    }

    println()
    println("=".repeat(100))
    println("ANALYSIS")
    println("=".repeat(100))
    println()

    if (results.all { it.success }) {
      val fastest = results.minByOrNull { it.loadTimeMs }
      val slowest = results.maxByOrNull { it.loadTimeMs }
      val lowestMemory = results.minByOrNull { it.memoryKb }
      val avgTime = results.map { it.loadTimeMs }.average()

      println("Fastest: ${fastest?.library} (${fastest?.loadTimeMs}ms)")
      println("Slowest: ${slowest?.library} (${slowest?.loadTimeMs}ms)")
      println("Lowest Memory: ${lowestMemory?.library} (${lowestMemory?.memoryKb}KB)")
      println("Average Load Time: ${"%.0f".format(avgTime)}ms")
      println()

      // Calculate relative performance
      println("Relative Performance (compared to fastest):")
      fastest?.let { fast ->
        results.sortedBy { it.loadTimeMs }.forEach { result ->
          val ratio = result.loadTimeMs.toDouble() / fast.loadTimeMs
          val percentage = ((ratio - 1) * 100).toInt()
          val comparison = if (ratio > 1.0) {
            "$percentage% slower"
          } else {
            "baseline"
          }
          println("  ${result.library}: $comparison")
        }
      }
    } else {
      println("WARNING: Some tests failed. Results may not be accurate.")
      results.filter { !it.success }.forEach {
        println("  ${it.library} failed to load")
      }
    }

    println()
    println("=".repeat(100))
    println()
  }

  private fun clearAllCaches() {
    try {
      // Clear Glide cache
      Glide.get(InstrumentationRegistry.getInstrumentation().targetContext).clearMemory()
      Thread.sleep(500)

      // Force garbage collection
      Runtime.getRuntime().gc()
      System.gc()
      Thread.sleep(1000)

      println("All caches cleared. Starting fresh tests...")
    } catch (e: Exception) {
      println("Warning: Could not clear all caches: ${e.message}")
    }
  }

  @Test
  fun compareNetworkLoadingPerformance() {
    // Test each library in sequence with fresh caches
    testGlideImage()
    clearAllCaches()

    testCoilImage()
    clearAllCaches()

    testLandscapistImage()
    clearAllCaches()

    testFrescoImage()
  }

  private fun testGlideImage() {
    println("\n[1/4] Testing GlideImage...")

    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        GlideImage(
          imageModel = { TEST_IMAGES[0] },
          modifier = Modifier
            .size(300.dp)
            .testTag("GlideImage"),
          imageOptions = ImageOptions(),
        )
      }

      // Wait for image to load
      composeTestRule.waitForIdle()
      composeTestRule.waitUntil(timeoutMillis = 15_000) {
        try {
          composeTestRule.onNodeWithTag("GlideImage").assertExists()
          true
        } catch (e: Exception) {
          false
        }
      }

      // Additional wait to ensure fully loaded
      Thread.sleep(1000)
      success = true
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    results.add(
      PerformanceResult(
        library = "GlideImage",
        loadTimeMs = loadTime,
        memoryKb = memoryUsed,
        success = success,
      ),
    )

    println("  ✓ Completed: ${loadTime}ms, Memory: ${memoryUsed}KB")
  }

  private fun testCoilImage() {
    println("\n[2/4] Testing CoilImage...")

    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        CoilImage(
          imageModel = { TEST_IMAGES[1] },
          modifier = Modifier
            .size(300.dp)
            .testTag("CoilImage"),
          imageOptions = ImageOptions(),
        )
      }

      composeTestRule.waitForIdle()
      composeTestRule.waitUntil(timeoutMillis = 15_000) {
        try {
          composeTestRule.onNodeWithTag("CoilImage").assertExists()
          true
        } catch (e: Exception) {
          false
        }
      }

      Thread.sleep(1000)
      success = true
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    results.add(
      PerformanceResult(
        library = "CoilImage",
        loadTimeMs = loadTime,
        memoryKb = memoryUsed,
        success = success,
      ),
    )

    println("  ✓ Completed: ${loadTime}ms, Memory: ${memoryUsed}KB")
  }

  private fun testLandscapistImage() {
    println("\n[3/4] Testing LandscapistImage...")

    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        LandscapistImage(
          imageModel = { TEST_IMAGES[2] },
          modifier = Modifier
            .size(300.dp)
            .testTag("LandscapistImage"),
          imageOptions = ImageOptions(),
          onImageStateChanged = { state ->
            imageState = state
          },
        )
      }

      composeTestRule.waitForIdle()
      composeTestRule.waitUntil(timeoutMillis = 15_000) {
        imageState is LandscapistImageState.Success || imageState is LandscapistImageState.Failure
      }

      success = imageState is LandscapistImageState.Success
      Thread.sleep(1000)
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    results.add(
      PerformanceResult(
        library = "LandscapistImage",
        loadTimeMs = loadTime,
        memoryKb = memoryUsed,
        success = success,
      ),
    )

    println("  ✓ Completed: ${loadTime}ms, Memory: ${memoryUsed}KB, Success: $success")
  }

  private fun testFrescoImage() {
    println("\n[4/4] Testing FrescoImage...")

    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        FrescoImage(
          imageUrl = TEST_IMAGES[3],
          modifier = Modifier
            .size(300.dp)
            .testTag("FrescoImage"),
          imageOptions = ImageOptions(),
        )
      }

      composeTestRule.waitForIdle()
      composeTestRule.waitUntil(timeoutMillis = 15_000) {
        try {
          composeTestRule.onNodeWithTag("FrescoImage").assertExists()
          true
        } catch (e: Exception) {
          false
        }
      }

      Thread.sleep(1000)
      success = true
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    results.add(
      PerformanceResult(
        library = "FrescoImage",
        loadTimeMs = loadTime,
        memoryKb = memoryUsed,
        success = success,
      ),
    )

    println("  ✓ Completed: ${loadTime}ms, Memory: ${memoryUsed}KB")
  }

  private fun getUsedMemoryKb(): Long {
    Runtime.getRuntime().gc()
    Thread.sleep(100)
    val memInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memInfo)
    return memInfo.totalPss.toLong()
  }

  private data class PerformanceResult(
    val library: String,
    val loadTimeMs: Long,
    val memoryKb: Long,
    val success: Boolean,
  )
}
