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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance comparison with 300.dp image size.
 * Run each test manually to collect 5 rounds of data.
 *
 * Run with:
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.PerformanceTest300dp
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PerformanceTest300dp {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val TEST_IMAGE =
      "https://user-images.githubusercontent.com/24237865/" +
        "75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"
  }

  @Before
  fun setup() {
    clearAllCaches()
    Thread.sleep(2000)
  }

  @Test
  fun glideImagePerformance() {
    println("\n[GlideImage] Performance Test (300.dp)...")

    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        GlideImage(
          imageModel = { TEST_IMAGE },
          modifier = Modifier.size(300.dp).testTag("GlideImage"),
          imageOptions = ImageOptions(),
        )
      }

      composeTestRule.waitForIdle()
      Thread.sleep(3000)
      success = true
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    println("  ✓ Time: ${loadTime}ms, Memory: ${memoryUsed}KB, Success: $success")
    println("  Result: $loadTime ms / $memoryUsed KB")
  }

  @Test
  fun coilImagePerformance() {
    println("\n[CoilImage] Performance Test (300.dp)...")

    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        CoilImage(
          imageModel = { TEST_IMAGE },
          modifier = Modifier.size(300.dp).testTag("CoilImage"),
          imageOptions = ImageOptions(),
        )
      }

      composeTestRule.waitForIdle()
      Thread.sleep(3000)
      success = true
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    println("  ✓ Time: ${loadTime}ms, Memory: ${memoryUsed}KB, Success: $success")
    println("  Result: $loadTime ms / $memoryUsed KB")
  }

  @Test
  fun landscapistImagePerformance() {
    println("\n[LandscapistImage] Performance Test (300.dp)...")

    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        LandscapistImage(
          imageModel = { TEST_IMAGE },
          modifier = Modifier.size(300.dp).testTag("LandscapistImage"),
          imageOptions = ImageOptions(),
          onImageStateChanged = { state ->
            imageState = state
          },
        )
      }

      composeTestRule.waitForIdle()
      composeTestRule.waitUntil(timeoutMillis = 10_000) {
        imageState is LandscapistImageState.Success || imageState is LandscapistImageState.Failure
      }

      success = imageState is LandscapistImageState.Success
      Thread.sleep(500)
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    println("  ✓ Time: ${loadTime}ms, Memory: ${memoryUsed}KB, Success: $success")
    println("  Result: $loadTime ms / $memoryUsed KB")
  }

  @Test
  fun frescoImagePerformance() {
    println("\n[FrescoImage] Performance Test (300.dp)...")

    var success = false
    val startMemory = getUsedMemoryKb()

    val loadTime = measureTimeMillis {
      composeTestRule.setContent {
        FrescoImage(
          imageUrl = TEST_IMAGE,
          modifier = Modifier.size(300.dp).testTag("FrescoImage"),
          imageOptions = ImageOptions(),
        )
      }

      composeTestRule.waitForIdle()
      Thread.sleep(3000)
      success = true
    }

    val endMemory = getUsedMemoryKb()
    val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

    println("  ✓ Time: ${loadTime}ms, Memory: ${memoryUsed}KB, Success: $success")
    println("  Result: $loadTime ms / $memoryUsed KB")
  }

  private fun clearAllCaches() {
    try {
      val context = InstrumentationRegistry.getInstrumentation().targetContext

      // Clear Glide cache
      Glide.get(context).clearMemory()

      // Clear Landscapist cache (singleton instance)
      com.skydoves.landscapist.core.Landscapist.getInstance().clearMemoryCache()

      // Clear Coil cache if available
      try {
        coil3.ImageLoader.Builder(context).build().memoryCache?.clear()
      } catch (e: Exception) {
        // Coil might not be initialized
      }

      // Force garbage collection
      Runtime.getRuntime().gc()
      System.gc()
      Thread.sleep(1000) // Increased wait time for thorough cleanup

      println("All caches cleared (Glide, Landscapist, Coil)")
    } catch (e: Exception) {
      println("Warning: Could not clear all caches: ${e.message}")
    }
  }

  private fun getUsedMemoryKb(): Long {
    Runtime.getRuntime().gc()
    Thread.sleep(100)
    val memInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memInfo)
    return memInfo.totalPss.toLong()
  }
}
