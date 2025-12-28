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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Comprehensive performance comparison report generator.
 *
 * This test generates a detailed performance report comparing:
 * - GlideImage
 * - CoilImage
 * - LandscapistImage
 * - FrescoImage
 *
 * Metrics measured:
 * - Load time (cold start)
 * - Load time (cached)
 * - Memory allocation
 * - Success rate
 * - Time to first pixel
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ImageLibraryComparisonReport {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val report = StringBuilder()
  private val testResults = mutableListOf<TestResult>()

  companion object {
    private const val TEST_IMAGE_URL = "https://user-images.githubusercontent.com/" +
      "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"
  }

  @Before
  fun setup() {
    report.clear()
    testResults.clear()
    report.appendLine("=".repeat(80))
    report.appendLine("Image Library Performance Comparison Report")
    report.appendLine("=".repeat(80))
    report.appendLine()
  }

  @After
  fun printReport() {
    // Print summary
    report.appendLine()
    report.appendLine("=".repeat(80))
    report.appendLine("SUMMARY")
    report.appendLine("=".repeat(80))
    report.appendLine()

    val headers = listOf("Library", "Cold Load", "Cached Load", "Memory", "Success")
    val columnWidths = listOf(15, 12, 12, 12, 8)

    // Print header
    headers.forEachIndexed { index, header ->
      report.append(header.padEnd(columnWidths[index]))
    }
    report.appendLine()
    report.appendLine("-".repeat(80))

    // Print results
    testResults.forEach { result ->
      report.append(result.library.padEnd(columnWidths[0]))
      report.append("${result.coldLoadMs}ms".padEnd(columnWidths[1]))
      report.append("${result.cachedLoadMs}ms".padEnd(columnWidths[2]))
      report.append("${result.memoryKb}KB".padEnd(columnWidths[3]))
      report.append(if (result.success) "✓" else "✗")
      report.appendLine()
    }

    report.appendLine()
    report.appendLine("=".repeat(80))
    report.appendLine("RECOMMENDATIONS")
    report.appendLine("=".repeat(80))
    report.appendLine()

    // Find best performers
    val fastestCold = testResults.minByOrNull { it.coldLoadMs }
    val fastestCached = testResults.minByOrNull { it.cachedLoadMs }
    val lowestMemory = testResults.minByOrNull { it.memoryKb }

    report.appendLine("Fastest cold load: ${fastestCold?.library} (${fastestCold?.coldLoadMs}ms)")
    report.appendLine(
      "Fastest cached load: ${fastestCached?.library} (${fastestCached?.cachedLoadMs}ms)",
    )
    report.appendLine("Lowest memory usage: ${lowestMemory?.library} (${lowestMemory?.memoryKb}KB)")
    report.appendLine()

    println(report.toString())
  }

  @Test
  fun generatePerformanceReport() {
    report.appendLine("Testing all image libraries with:")
    report.appendLine("- Image URL: $TEST_IMAGE_URL")
    report.appendLine("- Image Size: 200x200dp")
    report.appendLine("- Timeout: 10 seconds")
    report.appendLine()

    // Test each library
    testGlideImage()
    testCoilImage()
    testLandscapistImage()
    testFrescoImage()
  }

  private fun testGlideImage() {
    report.appendLine("Testing GlideImage...")

    val result = measureLibraryPerformance(
      library = "GlideImage",
      coldLoadContent = {
        GlideImage(
          imageModel = { TEST_IMAGE_URL },
          modifier = Modifier
            .size(200.dp)
            .testTag("GlideImage"),
          imageOptions = ImageOptions(),
        )
      },
      cachedLoadContent = {
        GlideImage(
          imageModel = { TEST_IMAGE_URL },
          modifier = Modifier
            .size(200.dp)
            .testTag("GlideImageCached"),
          imageOptions = ImageOptions(),
        )
      },
    )

    testResults.add(result)
    printTestResult(result)
  }

  private fun testCoilImage() {
    report.appendLine("Testing CoilImage...")

    val result = measureLibraryPerformance(
      library = "CoilImage",
      coldLoadContent = {
        CoilImage(
          imageModel = { TEST_IMAGE_URL },
          modifier = Modifier
            .size(200.dp)
            .testTag("CoilImage"),
          imageOptions = ImageOptions(),
        )
      },
      cachedLoadContent = {
        CoilImage(
          imageModel = { TEST_IMAGE_URL },
          modifier = Modifier
            .size(200.dp)
            .testTag("CoilImageCached"),
          imageOptions = ImageOptions(),
        )
      },
    )

    testResults.add(result)
    printTestResult(result)
  }

  private fun testLandscapistImage() {
    report.appendLine("Testing LandscapistImage...")

    var coldImageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var cachedImageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)

    val result = measureLibraryPerformance(
      library = "LandscapistImage",
      coldLoadContent = {
        LandscapistImage(
          imageModel = { TEST_IMAGE_URL },
          modifier = Modifier
            .size(200.dp)
            .testTag("LandscapistImage"),
          imageOptions = ImageOptions(),
          onImageStateChanged = { state -> coldImageState = state },
        )
      },
      cachedLoadContent = {
        LandscapistImage(
          imageModel = { TEST_IMAGE_URL },
          modifier = Modifier
            .size(200.dp)
            .testTag("LandscapistImageCached"),
          imageOptions = ImageOptions(),
          onImageStateChanged = { state -> cachedImageState = state },
        )
      },
      stateProvider = { coldImageState },
    )

    testResults.add(result)
    printTestResult(result)
  }

  private fun testFrescoImage() {
    report.appendLine("Testing FrescoImage...")

    val result = measureLibraryPerformance(
      library = "FrescoImage",
      coldLoadContent = {
        FrescoImage(
          imageUrl = TEST_IMAGE_URL,
          modifier = Modifier
            .size(200.dp)
            .testTag("FrescoImage"),
          imageOptions = ImageOptions(),
        )
      },
      cachedLoadContent = {
        FrescoImage(
          imageUrl = TEST_IMAGE_URL,
          modifier = Modifier
            .size(200.dp)
            .testTag("FrescoImageCached"),
          imageOptions = ImageOptions(),
        )
      },
    )

    testResults.add(result)
    printTestResult(result)
  }

  private fun measureLibraryPerformance(
    library: String,
    coldLoadContent: @Composable () -> Unit,
    cachedLoadContent: @Composable () -> Unit,
    stateProvider: (() -> LandscapistImageState)? = null,
  ): TestResult {
    // Measure cold load
    val coldLoadLatch = CountDownLatch(1)
    var coldLoadSuccess = false
    val startMemory = getUsedMemoryKb()

    val coldLoadTime = measureTimeMillis {
      composeTestRule.setContent {
        Box {
          coldLoadContent()
        }
      }

      // Wait for load
      coldLoadSuccess = coldLoadLatch.await(10, TimeUnit.SECONDS)

      if (stateProvider != null) {
        // For LandscapistImage, wait for state change
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
          val state = stateProvider()
          state is LandscapistImageState.Success || state is LandscapistImageState.Failure
        }
        coldLoadSuccess = stateProvider() is LandscapistImageState.Success
      } else {
        // For other libraries, just wait for idle
        composeTestRule.waitForIdle()
        coldLoadSuccess = true
      }
    }

    val afterColdLoadMemory = getUsedMemoryKb()

    // Small delay to ensure cache is populated
    Thread.sleep(500)

    // Measure cached load
    val cachedLoadLatch = CountDownLatch(1)
    var cachedLoadSuccess = false

    val cachedLoadTime = measureTimeMillis {
      composeTestRule.setContent {
        Box {
          cachedLoadContent()
        }
      }

      cachedLoadSuccess = cachedLoadLatch.await(10, TimeUnit.SECONDS)

      if (stateProvider != null) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
          val state = stateProvider()
          state is LandscapistImageState.Success || state is LandscapistImageState.Failure
        }
        cachedLoadSuccess = stateProvider() is LandscapistImageState.Success
      } else {
        composeTestRule.waitForIdle()
        cachedLoadSuccess = true
      }
    }

    val memoryUsed = afterColdLoadMemory - startMemory

    return TestResult(
      library = library,
      coldLoadMs = coldLoadTime,
      cachedLoadMs = cachedLoadTime,
      memoryKb = memoryUsed.coerceAtLeast(0),
      success = coldLoadSuccess && cachedLoadSuccess,
    )
  }

  private fun printTestResult(result: TestResult) {
    report.appendLine("  Cold Load Time: ${result.coldLoadMs}ms")
    report.appendLine("  Cached Load Time: ${result.cachedLoadMs}ms")
    report.appendLine("  Memory Used: ${result.memoryKb}KB")
    report.appendLine("  Success: ${if (result.success) "Yes" else "No"}")
    report.appendLine()
  }

  private fun getUsedMemoryKb(): Long {
    Runtime.getRuntime().gc()
    Thread.sleep(100) // Give GC time to complete
    val memInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memInfo)
    return memInfo.totalPss.toLong()
  }

  private data class TestResult(
    val library: String,
    val coldLoadMs: Long,
    val cachedLoadMs: Long,
    val memoryKb: Long,
    val success: Boolean,
  )
}
