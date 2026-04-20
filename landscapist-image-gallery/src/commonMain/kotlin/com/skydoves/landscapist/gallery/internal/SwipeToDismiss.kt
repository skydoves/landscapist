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
package com.skydoves.landscapist.gallery.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A container composable that enables vertical swipe-to-dismiss behavior on its [content].
 *
 * When the user drags the content vertically, it translates along the Y axis and scales down
 * slightly. The [onProgressChanged] callback reports the dismiss progress
 * (0.0 = no drag, 1.0 = fully dismissed).
 * If the drag exceeds [threshold] or the fling velocity exceeds [velocityThreshold],
 * [onDismiss] is invoked. Otherwise the content animates back to the original position.
 *
 * **Important:** The content is always rendered inside the same [Box] regardless of the
 * [enabled] flag. This preserves the composition tree structure and avoids destroying
 * internal remembered state (e.g., ZoomableState, PagerState) when [enabled] toggles.
 *
 * @param enabled Whether the swipe-to-dismiss gesture is enabled.
 * @param threshold Fraction of the container height (0.0 ~ 1.0) required to trigger dismiss.
 * @param velocityThreshold Velocity in pixels/second required to trigger a fling dismiss.
 * @param onDismiss Callback invoked when the dismiss gesture is triggered.
 * @param onProgressChanged Callback invoked with the current dismiss progress (0.0 ~ 1.0).
 * @param onDragging Callback invoked when the drag state changes.
 * @param content The content to be wrapped with swipe-to-dismiss behavior.
 */
@Composable
internal fun SwipeToDismissBox(
  enabled: Boolean,
  threshold: Float,
  velocityThreshold: Float,
  onDismiss: () -> Unit,
  onProgressChanged: (Float) -> Unit,
  onDragging: (Boolean) -> Unit,
  content: @Composable () -> Unit,
) {
  val offsetY = remember { Animatable(0f) }
  var containerHeight by remember { mutableFloatStateOf(1f) }
  val scope = rememberCoroutineScope()

  val draggableState = rememberDraggableState { delta ->
    scope.launch {
      offsetY.snapTo(offsetY.value + delta)
      val progress = (abs(offsetY.value) / containerHeight).coerceIn(0f, 1f)
      onProgressChanged(progress)
    }
  }

  // Always use the same Box structure to preserve composition tree stability.
  // When disabled, apply identity modifiers so the content renders normally
  // without tearing down and recreating the composition subtree.
  Box(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { containerHeight = it.height.toFloat().coerceAtLeast(1f) }
      .then(
        if (enabled) {
          Modifier.draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStarted = {
              onDragging(true)
            },
            onDragStopped = { velocity ->
              val fraction = abs(offsetY.value) / containerHeight
              if (fraction >= threshold || abs(velocity) >= velocityThreshold) {
                onDragging(false)
                onDismiss()
              } else {
                scope.launch {
                  offsetY.animateTo(0f, spring())
                  onProgressChanged(0f)
                  onDragging(false)
                }
              }
            },
          )
        } else {
          Modifier
        },
      )
      .graphicsLayer {
        if (enabled) {
          val progress = (abs(offsetY.value) / containerHeight).coerceIn(0f, 1f)
          translationY = offsetY.value
          val scale = 1f - (progress * 0.2f)
          scaleX = scale
          scaleY = scale
        }
      },
  ) {
    content()
  }
}
