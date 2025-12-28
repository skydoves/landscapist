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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class QuickPerformanceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private val TEST_IMAGES = listOf(
      "https://user-images.githubusercontent.com/24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
      "https://user-images.githubusercontent.com/24237865/75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
      "https://user-images.githubusercontent.com/24237865/75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
    )
  }

  @Test
  fun compareImageLoadingPerformance() {
    println("\n" + "=".repeat(80))
    println("QUICK PERFORMANCE COMPARISON")
    println("=".repeat(80))
    println()

    val results = mutableListOf<TestResult>()

    // Test GlideImage
    println("[1/3] Testing GlideImage...")
    val glideTime = measureTimeMillis {
      composeTestRule.setContent {
        GlideImage(
          imageModel = { TEST_IMAGES[0] },
          modifier = Modifier.size(200.dp).testTag("glide"),
          imageOptions = ImageOptions(),
        )
      }
      composeTestRule.waitForIdle()
      Thread.sleep(2000)
    }
    results.add(TestResult("GlideImage", glideTime))
    println("  Time: ${glideTime}ms")

    // Clear and wait
    composeTestRule.setContent { }
    Thread.sleep(1000)

    // Test CoilImage
    println("[2/3] Testing CoilImage...")
    val coilTime = measureTimeMillis {
      composeTestRule.setContent {
        CoilImage(
          imageModel = { TEST_IMAGES[1] },
          modifier = Modifier.size(200.dp).testTag("coil"),
          imageOptions = ImageOptions(),
        )
      }
      composeTestRule.waitForIdle()
      Thread.sleep(2000)
    }
    results.add(TestResult("CoilImage", coilTime))
    println("  Time: ${coilTime}ms")

    // Clear and wait
    composeTestRule.setContent { }
    Thread.sleep(1000)

    // Test LandscapistImage
    println("[3/3] Testing LandscapistImage...")
    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    val landscapistTime = measureTimeMillis {
      composeTestRule.setContent {
        LandscapistImage(
          imageModel = { TEST_IMAGES[2] },
          modifier = Modifier.size(200.dp).testTag("landscapist"),
          imageOptions = ImageOptions(),
          onImageStateChanged = { imageState = it }
        )
      }
      composeTestRule.waitForIdle()
      composeTestRule.waitUntil(timeoutMillis = 10_000) {
        imageState is LandscapistImageState.Success || imageState is LandscapistImageState.Failure
      }
      Thread.sleep(500)
    }
    results.add(TestResult("LandscapistImage", landscapistTime))
    println("  Time: ${landscapistTime}ms, State: $imageState")

    // Print results
    println()
    println("=".repeat(80))
    println("RESULTS")
    println("=".repeat(80))
    println()
    println(String.format("%-20s | %s", "Library", "Load Time"))
    println("-".repeat(40))
    results.sortedBy { it.timeMs }.forEach {
      println(String.format("%-20s | %dms", it.library, it.timeMs))
    }

    val fastest = results.minByOrNull { it.timeMs }
    println()
    println("Fastest: ${fastest?.library} (${fastest?.timeMs}ms)")
    println("=".repeat(80))
  }

  private data class TestResult(
    val library: String,
    val timeMs: Long
  )
}
