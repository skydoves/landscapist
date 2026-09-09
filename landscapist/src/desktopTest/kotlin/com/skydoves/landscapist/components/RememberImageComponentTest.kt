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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RememberImageComponentTest {

  @Test
  fun `a plugin added later reaches the component`() {
    val seen = mutableListOf<List<ImagePlugin>>()

    runComposeUiTest {
      var enabled by mutableStateOf(false)
      setContent {
        // Read here as well as in the block, which is what any screen with a control for it does.
        // A state read only inside the block restarts the block alone and never this call.
        val on = enabled
        val component = rememberImageComponent {
          if (on) +CrossfadePlugin(duration = 300)
        }
        seen += component.plugins.toList()
      }
      waitForIdle()
      enabled = true
      waitForIdle()
    }

    assertTrue(seen.first().isEmpty(), "the component started with a plugin, saw ${seen.first()}")
    assertEquals(
      1,
      seen.last().size,
      "the plugin never reached the component, it stayed at ${seen.last()}",
    )
  }

  @Test
  fun `a reconfigured plugin replaces the one before it`() {
    val seen = mutableListOf<ImagePlugin>()

    runComposeUiTest {
      var duration by mutableStateOf(100)
      setContent {
        val current = duration
        val component = rememberImageComponent { +CrossfadePlugin(duration = current) }
        component.plugins.firstOrNull()?.let { seen += it }
      }
      waitForIdle()
      duration = 900
      waitForIdle()
    }

    assertEquals(
      CrossfadePlugin(duration = 900),
      seen.last(),
      "the component kept the duration it was first built with, saw ${seen.last()}",
    )
  }

  @Test
  fun `the component keeps its identity while its plugins stay the same`() {
    // What lets an image skip. A caller may also key state on the component, and rebuilding that
    // for a recomposition the plugins had no part in would be wasted work.
    val seen = mutableListOf<ImageComponent>()

    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        val unrelated = tick
        seen += rememberImageComponent { +CrossfadePlugin(duration = 300 + unrelated * 0) }
      }
      waitForIdle()
      tick = 1
      waitForIdle()
    }

    assertTrue(seen.size > 1, "the composable only ran once, so nothing was proven")
    for (component in seen) assertSame(seen.first(), component, "the component was replaced")
  }
}
