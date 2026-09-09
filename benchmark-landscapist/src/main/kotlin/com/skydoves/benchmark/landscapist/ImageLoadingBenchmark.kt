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
 * Frame timing while a list of images scrolls, one row per variant.
 *
 * [landscapistImageLoading] and [coilImageLoading] are the pair to quote: the first is
 * `landscapist-image` with no plugins, which is the only shape that takes the node path, and the
 * second is Coil's own `AsyncImage`. The Coil row used to render `landscapist-coil3`'s `CoilImage`,
 * so it compared this library to itself; that comparison is now [coilWrapperImageLoading], which is
 * a different question and labelled as one.
 *
 * [pluginImageLoading] keeps the eight plugin configuration as a row of its own. Plugins force the
 * composed path, so folding them into the landscapist row would have meant the node path was never
 * measured at all, which is what used to happen.
 *
 * Every navigation and every scroll is checked. A run that found no tab, or scrolled nothing, used
 * to report a low P50 and no error.
 *
 * Regenerate the baseline profile before quoting a row. [CompilationMode.Partial] compiles what the
 * profile covers, so a profile taken before a variant existed leaves that variant interpreted while
 * the others are compiled, which is a gap in the numbers and not in the libraries.
 */
@RequiresApi(Build.VERSION_CODES.P)
class ImageLoadingBenchmark {

  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  /** landscapist-image with no plugins: the node path this branch adds. */
  @Test
  fun landscapistImageLoading() = measureTab("Landscapist")

  /** Coil's own `AsyncImage`. */
  @Test
  fun coilImageLoading() = measureTab("Coil")

  /** landscapist-image with eight plugins, which is the composed path. */
  @Test
  fun pluginImageLoading() = measureTab("Plugins")

  /** landscapist-coil3, this library's Compose layer over the Coil engine. */
  @Test
  fun coilWrapperImageLoading() = measureTab("CoilWrapper")

  /** landscapist-glide, this library's Compose layer over the Glide engine. */
  @Test
  fun glideWrapperImageLoading() = measureTab("GlideWrapper")

  /** landscapist-fresco, this library's Compose layer over the Fresco pipeline. */
  @Test
  fun frescoWrapperImageLoading() = measureTab("FrescoWrapper")

  /** Every tab is measured the same way, from a screen on which no list has yet composed. */
  private fun measureTab(tab: String) = benchmarkRule.measureRepeated(
    packageName = PACKAGE_NAME,
    metrics = listOf(FrameTimingMetric()),
    iterations = 5,
    startupMode = StartupMode.WARM,
    compilationMode = CompilationMode.Partial(),
  ) {
    pressHome()
    startActivityAndWait()
    navigateToTab(tab)
    scrollAndLoadImages(tab)
  }

  private fun MacrobenchmarkScope.navigateToTab(tab: String) {
    val button = device.findObject(By.text(tab))
    checkNotNull(button) { "no tab labelled $tab is on screen, so nothing was measured" }
    button.click()
    device.waitForIdle()
    check(device.wait(Until.hasObject(firstItem(tab)), CONTENT_TIMEOUT_MS)) {
      "the $tab tab never put its first image on screen within $CONTENT_TIMEOUT_MS ms"
    }
  }

  /**
   * Scrolls down and back, checking both. Item zero leaving the screen and coming back is the same
   * shape the engine harness uses, and it is the only thing that separates a scrolled list from a
   * list that ignored every gesture.
   */
  private fun MacrobenchmarkScope.scrollAndLoadImages(tab: String) {
    device.waitForIdle()
    val list = device.findObject(By.scrollable(true))
    checkNotNull(list) { "the $tab tab has no scrollable list, so nothing was scrolled" }

    repeat(SCROLLS) {
      list.scroll(Direction.DOWN, SCROLL_FRACTION)
      device.waitForIdle(SETTLE_MS)
    }
    check(!device.hasObject(firstItem(tab))) {
      "$tab did not scroll: its first image is still on screen after $SCROLLS scrolls down"
    }

    // One more up than down, because a scroll can carry further than it was asked to and the top
    // clamps, while an undershoot would leave the list somewhere it was never measured from.
    repeat(SCROLLS + 1) {
      list.scroll(Direction.UP, SCROLL_FRACTION)
      device.waitForIdle(SETTLE_MS)
    }
    check(device.hasObject(firstItem(tab))) {
      "$tab did not scroll back: its first image is not on screen after ${SCROLLS + 1} scrolls up"
    }
  }

  private fun firstItem(tab: String) = By.res(PACKAGE_NAME, "${tab}First")

  companion object {
    private const val PACKAGE_NAME = "com.skydoves.benchmark.landscapist.app"
    private const val CONTENT_TIMEOUT_MS = 10_000L
    private const val SETTLE_MS = 1_000L
    private const val SCROLLS = 3
    private const val SCROLL_FRACTION = 0.8f
  }
}
