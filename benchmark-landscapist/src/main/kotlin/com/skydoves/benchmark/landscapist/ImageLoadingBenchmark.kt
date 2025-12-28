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
package com.skydoves.benchmark.landscapist

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Benchmark for comparing image loading performance across different image libraries.
 *
 * This benchmark measures:
 * - Frame timing during image loading
 * - Scrolling performance with multiple images
 * - Memory pressure during bulk image loads
 *
 * Run this benchmark to compare GlideImage, CoilImage, LandscapistImage, and FrescoImage.
 */
@RequiresApi(Build.VERSION_CODES.P)
class ImageLoadingBenchmark {

  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  /**
   * Benchmarks GlideImage loading performance.
   * Measures frame timing and jank during image loads.
   */
  @Test
  fun glideImageLoading() = benchmarkRule.measureRepeated(
    packageName = PACKAGE_NAME,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
    compilationMode = CompilationMode.Partial(),
  ) {
    pressHome()
    startActivityAndWait()

    // Navigate to Glide tab and load images
    navigateToTab("Glide")
    scrollAndLoadImages()
  }

  /**
   * Benchmarks CoilImage loading performance.
   * Measures frame timing and jank during image loads.
   */
  @Test
  fun coilImageLoading() = benchmarkRule.measureRepeated(
    packageName = PACKAGE_NAME,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
    compilationMode = CompilationMode.Partial(),
  ) {
    pressHome()
    startActivityAndWait()

    // Navigate to Coil tab and load images
    navigateToTab("Coil")
    scrollAndLoadImages()
  }

  /**
   * Benchmarks LandscapistImage loading performance.
   * Measures frame timing and jank during image loads.
   */
  @Test
  fun landscapistImageLoading() = benchmarkRule.measureRepeated(
    packageName = PACKAGE_NAME,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
    compilationMode = CompilationMode.Partial(),
  ) {
    pressHome()
    startActivityAndWait()

    // Navigate to Landscapist tab and load images
    navigateToTab("Landscapist")
    scrollAndLoadImages()
  }

  /**
   * Benchmarks FrescoImage loading performance.
   * Measures frame timing and jank during image loads.
   */
  @Test
  fun frescoImageLoading() = benchmarkRule.measureRepeated(
    packageName = PACKAGE_NAME,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
    compilationMode = CompilationMode.Partial(),
  ) {
    pressHome()
    startActivityAndWait()

    // Navigate to Fresco tab and load images
    navigateToTab("Fresco")
    scrollAndLoadImages()
  }

  /**
   * Benchmarks scrolling performance with multiple images loaded.
   * Compares all libraries during intensive scrolling.
   */
  @Test
  fun scrollPerformanceComparison() = benchmarkRule.measureRepeated(
    packageName = PACKAGE_NAME,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
    compilationMode = CompilationMode.Partial(),
  ) {
    pressHome()
    startActivityAndWait()

    // Test each library
    listOf("Glide", "Coil", "Landscapist", "Fresco").forEach { library ->
      navigateToTab(library)

      // Perform intensive scrolling
      val scrollable = device.findObject(By.scrollable(true))
      scrollable?.let {
        repeat(5) {
          scrollable.scroll(Direction.DOWN, 1.0f)
          device.waitForIdle()
        }
        repeat(5) {
          scrollable.scroll(Direction.UP, 1.0f)
          device.waitForIdle()
        }
      }
    }
  }

  private fun MacrobenchmarkScope.navigateToTab(tabName: String) {
    val tab = device.findObject(By.text(tabName))
    tab?.click()
    device.waitForIdle()

    // Wait for content to load
    device.wait(Until.hasObject(By.res(PACKAGE_NAME, "${tabName}Image")), 2_000)
  }

  private fun MacrobenchmarkScope.scrollAndLoadImages() {
    device.waitForIdle()

    // Find scrollable content
    val scrollable = device.findObject(By.scrollable(true))
    scrollable?.let {
      // Scroll down to trigger image loading
      repeat(3) {
        scrollable.scroll(Direction.DOWN, 0.8f)
        device.waitForIdle(1000)
      }

      // Scroll back up
      repeat(3) {
        scrollable.scroll(Direction.UP, 0.8f)
        device.waitForIdle(1000)
      }
    }
  }

  companion object {
    private const val PACKAGE_NAME = "com.skydoves.benchmark.landscapist.app"
  }
}
