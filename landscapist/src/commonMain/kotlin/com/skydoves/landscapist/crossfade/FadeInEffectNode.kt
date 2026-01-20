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

private class FadeInEffectNode(
  var key: State<Any>,
  var durationMs: Int,
) : Modifier.Node(), DrawModifierNode {

  private val alpha = Animatable(0f)
  private val brightness = Animatable(0.8f)
  private val saturation = Animatable(0f)

  private val colorMatrix = ColorMatrix()
  private val paint = Paint()

  // This gets called when the node is first attached or when the coroutineScope is available
  override fun onAttach() {
    coroutineScope.launch {
      // snapshotFlow observes changes to our `key` state.
      // collectLatest ensures that if the key changes mid-animation,
      // the old animation is cancelled and the new one starts.
      snapshotFlow { key }
        .collectLatest {
          alpha.snapTo(0f)
          brightness.snapTo(0.8f)
          saturation.snapTo(0f)

          coroutineScope {
            launch { alpha.animateTo(1f, tween(durationMillis = durationMs / 2)) }
            launch { brightness.animateTo(1f, tween(durationMillis = durationMs * 3 / 4)) }
            launch { saturation.animateTo(1f, tween(durationMillis = durationMs)) }
          }
        }
    }
  }

  // This is the draw implementation from DrawModifierNode
  override fun ContentDrawScope.draw() {
    val alphaValue = alpha.value
    val brightnessValue = brightness.value
    val saturationValue = saturation.value

    // If animation is running, apply effects. Otherwise, just draw content.
    if (alphaValue < 1f || brightnessValue < 1f || saturationValue < 1f) {
      colorMatrix.apply {
        updateBrightness(brightnessValue)
        updateSaturation(saturationValue)
      }
      paint.colorFilter = ColorFilter.colorMatrix(colorMatrix)
      paint.alpha = alphaValue

      drawContext.canvas.saveLayer(size.toRect(), paint)
      drawContent()
      drawContext.canvas.restore()
    } else {
      drawContent()
    }
  }
}

private data class FadeInEffectElement(
  val key: State<Any>,
  val durationMs: Int,
) : ModifierNodeElement<FadeInEffectNode>() {

  override fun create(): FadeInEffectNode {
    return FadeInEffectNode(key, durationMs)
  }

  override fun update(node: FadeInEffectNode) {
    node.key = key
    node.durationMs = durationMs
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "fadeInWithEffect"
    properties["key"] = key.value
    properties["durationMs"] = durationMs
  }
}

@Composable
internal fun Modifier.fadeInWithEffect(key: Any, durationMs: Int): Modifier {
  val state: State<Any> = rememberUpdatedState(key)
  return this.then(FadeInEffectElement(state, durationMs))
}
