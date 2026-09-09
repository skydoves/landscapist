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
package com.skydoves.landscapist.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How often the reveal says it has finished.
 *
 * It used to say so from inside the lambda that supplies the radius for a state, which Compose
 * calls for every state it needs a value for, on every recomposition and on every frame. One reveal
 * reported finishing twenty three times on a device, and a listener that touches state would have
 * turned each of those into another pass through composition.
 */
@OptIn(ExperimentalTestApi::class)
class CircularRevealFinishTest {

  @Test
  fun `one reveal reports finishing once`() {
    var finishes = 0
    val plugin = CircularRevealPlugin(duration = 100, onFinishListener = { finishes++ })

    runComposeUiTest {
      setContent {
        val painter = with(plugin) {
          compose(ImageBitmap(4, 4), ColorPainter(Color.Red))
        }
        Box(Modifier.size(20.dp).drawBehind { with(painter) { draw(size) } })
      }
      waitForIdle()
      mainClock.advanceTimeBy(1_000)
      waitForIdle()
    }

    assertEquals(1, finishes, "the reveal reported finishing $finishes times")
  }
}
