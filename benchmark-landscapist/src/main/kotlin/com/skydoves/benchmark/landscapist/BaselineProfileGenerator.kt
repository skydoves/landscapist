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
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

@RequiresApi(Build.VERSION_CODES.P)
class BaselineProfileGenerator {
  @get:Rule
  val baselineProfileRule = BaselineProfileRule()

  @Test
  fun startup() =
    baselineProfileRule.collect(
      packageName = packageName,
      stableIterations = 2,
      maxIterations = 8,
    ) {
      pressHome()
      startActivityAndWait()
      device.waitForIdle()

      // Critical journey: visit every tab and scroll its image list, so the profile covers the
      // navigation, the list and the image loading code paths of each variant. A tab that never
      // opened would silently leave its classes out of the profile, so every step is checked.
      tabs.forEach { tab ->
        val button = device.findObject(By.text(tab))
        checkNotNull(button) { "no tab labelled $tab is on screen" }
        button.click()
        device.waitForIdle()
        check(device.wait(Until.hasObject(By.res("${tab}First")), contentTimeoutMs)) {
          "the $tab tab never put its first image on screen"
        }
        // The decode path belongs in the profile too, and a composed row does not prove it ran.
        check(device.wait(Until.hasObject(By.res("${tab}Loaded")), contentTimeoutMs)) {
          "the $tab tab composed its rows but loaded no image"
        }

        val list = device.findObject(By.scrollable(true))
        checkNotNull(list) { "the $tab tab has no scrollable list" }
        repeat(scrolls) {
          list.scroll(Direction.DOWN, scrollFraction)
          device.waitForIdle()
        }
        check(!device.hasObject(By.res("${tab}First"))) {
          "$tab did not scroll, so its scrolling code path is not in the profile"
        }
      }
    }
}

private val tabs = listOf(
  "Landscapist",
  "Coil",
  "Plugins",
  "CoilWrapper",
  "GlideWrapper",
  "FrescoWrapper",
)

private const val packageName = "com.skydoves.benchmark.landscapist.app"

private const val contentTimeoutMs = 10_000L

private const val scrolls = 2

private const val scrollFraction = 0.8f
