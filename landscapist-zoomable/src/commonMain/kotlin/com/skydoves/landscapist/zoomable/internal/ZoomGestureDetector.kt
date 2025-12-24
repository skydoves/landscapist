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
package com.skydoves.landscapist.zoomable.internal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState
import kotlinx.coroutines.launch

/**
 * Modifier that detects zoom and pan gestures for zoomable content.
 * Supports nested scrolling - when not zoomed, single-finger drag events pass to parent.
 *
 * @param state The [ZoomableState] to update based on gestures.
 * @param config The [ZoomableConfig] for gesture behavior configuration.
 */
internal fun Modifier.zoomGestures(
  state: ZoomableState,
  config: ZoomableConfig,
): Modifier = composed {
  val scope = rememberCoroutineScope()

  this
    // Handle double-tap to zoom FIRST
    .then(
      if (config.enableDoubleTapZoom) {
        Modifier.pointerInput(state, config) {
          detectTapGestures(
            onTap = { /* Allow single tap to pass through */ },
            onDoubleTap = { offset ->
              scope.launch {
                if (state.isZoomed) {
                  state.resetZoom()
                } else {
                  state.zoomTo(config.doubleTapZoom, offset)
                }
              }
            },
          )
        }
      } else {
        Modifier
      },
    )
    // Handle pinch-to-zoom and pan gestures
    .pointerInput(state, config) {
      awaitEachGesture {
        var wasMultiTouch = false
        var totalPanDistance = 0f
        val panThreshold = 10f // Threshold to distinguish pan from tap

        awaitFirstDown(requireUnconsumed = false)
        do {
          val event = awaitPointerEvent()
          val pointers = event.changes.filter { it.pressed }

          if (pointers.isNotEmpty()) {
            val isMultiTouch = pointers.size > 1
            if (isMultiTouch) {
              wasMultiTouch = true
            }

            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            val centroid = event.calculateCentroid(useCurrent = true)

            // Handle pinch zoom (multi-touch)
            if (isMultiTouch || wasMultiTouch) {
              if (zoom != 1f) {
                state.onGestureZoom(zoom, centroid)
              }
              if (pan != Offset.Zero) {
                state.onGesturePan(pan)
              }
              // Always consume during multi-touch to prevent parent scroll
              event.changes.forEach { it.consume() }
            } else if (state.isZoomed && pan != Offset.Zero) {
              // Single finger pan when zoomed
              state.onGesturePan(pan)
              totalPanDistance += pan.getDistance()

              // Only consume after exceeding threshold to distinguish from double-tap
              // This allows quick taps through but prevents parent scroll during pan
              if (totalPanDistance > panThreshold) {
                event.changes.forEach { if (it.positionChanged()) it.consume() }
              }
            }
            // When not zoomed and single finger, don't do anything - let parent scroll
          }
        } while (event.changes.any { it.pressed })
      }
    }
}
