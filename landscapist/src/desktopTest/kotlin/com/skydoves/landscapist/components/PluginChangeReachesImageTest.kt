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
package com.skydoves.landscapist.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Whether a plugin change survives the trip from [rememberImageComponent] to the image.
 *
 * The tests beside this one read the component where it is built, which is inside the composition
 * that just rebuilt it. An image reads it one call further down, as a parameter, and every image in
 * this library is skippable and takes a stable component, so the change has to alter something the
 * runtime compares before the image is recomposed at all.
 */
@OptIn(ExperimentalTestApi::class)
class PluginChangeReachesImageTest {

  /** Stands in for an image: skippable, and takes the component the way the real ones do. */
  @Composable
  private fun Downstream(component: ImagePluginComponent) {
    seenByImage += component.plugins.toList()
  }

  private val seenByImage = mutableListOf<List<ImagePlugin>>()

  @Test
  fun `an image sees a plugin added after it was first composed`() {
    runComposeUiTest {
      var enabled by mutableStateOf(false)
      setContent {
        val on = enabled
        Downstream(rememberImageComponent { if (on) +CrossfadePlugin(duration = 300) })
      }
      waitForIdle()
      enabled = true
      waitForIdle()
    }

    assertEquals(
      listOf(CrossfadePlugin(duration = 300)),
      seenByImage.last(),
      "the image was skipped and still draws with ${seenByImage.last()}",
    )
  }

  @Test
  fun `an image sees a plugin removed after it was first composed`() {
    runComposeUiTest {
      var enabled by mutableStateOf(true)
      setContent {
        val on = enabled
        Downstream(rememberImageComponent { if (on) +CrossfadePlugin(duration = 300) })
      }
      waitForIdle()
      enabled = false
      waitForIdle()
    }

    assertEquals(
      emptyList(),
      seenByImage.last(),
      "the removed plugin still runs on the image, which sees ${seenByImage.last()}",
    )
  }

  @Test
  fun `an image sees a plugin reconfigured after it was first composed`() {
    runComposeUiTest {
      var duration by mutableStateOf(100)
      setContent {
        val current = duration
        Downstream(rememberImageComponent { +CrossfadePlugin(duration = current) })
      }
      waitForIdle()
      duration = 900
      waitForIdle()
    }

    assertEquals(
      listOf(CrossfadePlugin(duration = 900)),
      seenByImage.last(),
      "the image still crossfades at the old duration, it sees ${seenByImage.last()}",
    )
  }

  @Test
  fun `an image is not recomposed while its plugins stay the same`() {
    var unrelated = 0
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        unrelated = tick
        Downstream(rememberImageComponent { +CrossfadePlugin(duration = 300) })
      }
      waitForIdle()
      tick = 1
      waitForIdle()
    }

    assertEquals(1, unrelated, "the surrounding composable never recomposed, so nothing was proven")
    assertEquals(
      1,
      seenByImage.size,
      "the image recomposed for a change that was not its own, ${seenByImage.size} times",
    )
  }
}
