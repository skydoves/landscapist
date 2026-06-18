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

      // Critical journey: visit each library tab and scroll its image list so the profile
      // covers the navigation, list, and image-loading code paths for every library.
      tabs.forEach { tab ->
        device.findObject(By.text(tab))?.click()
        device.waitForIdle()
        device.wait(Until.hasObject(By.res(packageName, "${tab}Image")), 3_000)

        device.findObject(By.scrollable(true))?.let { list ->
          repeat(2) {
            list.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
          }
        }
      }
    }
}

private val tabs = listOf("Landscapist", "Coil", "Glide", "Fresco")

private const val packageName = "com.skydoves.benchmark.landscapist.app"
