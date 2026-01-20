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
package com.skydoves.landscapist.crossfade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private class FadeOutEffectNode(
  var key: State<Any>,
  var durationMs: Int,
) : Modifier.Node(), DrawModifierNode {

  // Initial values are set to the "fully visible" state
  private val alpha = Animatable(1f)
  private val brightness = Animatable(1f)
  private val saturation = Animatable(1f)

  private val colorMatrix = ColorMatrix()
  private val paint = Paint()

  override fun onAttach() {
    coroutineScope.launch {
      snapshotFlow { key.value }
        .collectLatest {
          // Reset to the starting (visible) state before animating out
          alpha.snapTo(1f)
          brightness.snapTo(1f)
          saturation.snapTo(1f)

          // Launch animations to fade out
          coroutineScope {
            launch { alpha.animateTo(0f, tween(durationMillis = durationMs / 2)) }
            launch { brightness.animateTo(0.8f, tween(durationMillis = durationMs * 3 / 4)) }
            launch { saturation.animateTo(0f, tween(durationMillis = durationMs)) }
          }
        }
    }
  }

  override fun ContentDrawScope.draw() {
    val alphaValue = alpha.value
    val brightnessValue = brightness.value
    val saturationValue = saturation.value

    // Apply effects as long as the content is not fully transparent
    if (alphaValue > 0f) {
      colorMatrix.apply {
        updateBrightness(brightnessValue)
        updateSaturation(saturationValue)
      }
      paint.colorFilter = ColorFilter.colorMatrix(colorMatrix)
      paint.alpha = alphaValue

      drawContext.canvas.saveLayer(size.toRect(), paint)
      drawContent()
      drawContext.canvas.restore()
    }
    // If alpha is 0, we don't need to draw anything.
  }
}

private data class FadeOutEffectElement(
  val key: State<Any>,
  val durationMs: Int,
) : ModifierNodeElement<FadeOutEffectNode>() {

  override fun create(): FadeOutEffectNode {
    return FadeOutEffectNode(key, durationMs)
  }

  override fun update(node: FadeOutEffectNode) {
    node.key = key
    node.durationMs = durationMs
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "fadeOutWithEffect"
    properties["key"] = key.value
    properties["durationMs"] = durationMs
  }
}

@Composable
internal fun Modifier.fadeOutWithEffect(key: Any, durationMs: Int): Modifier {
  val state: State<Any> = rememberUpdatedState(key)
  return this.then(FadeOutEffectElement(state, durationMs))
}
