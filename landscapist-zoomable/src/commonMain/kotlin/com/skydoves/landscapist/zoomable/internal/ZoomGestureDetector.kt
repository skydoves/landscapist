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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState
import kotlinx.coroutines.launch

/**
 * Modifier that detects zoom and pan gestures for zoomable content.
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
    // Handle pinch-to-zoom and pan gestures
    .pointerInput(state, config) {
      detectTransformGestures { centroid, pan, zoom, _ ->
        // Apply zoom if there's a significant change
        if (zoom != 1f) {
          state.onGestureZoom(zoom, centroid)
        }

        // Apply pan - allow panning when zoomed
        if (pan != Offset.Zero && state.isZoomed) {
          state.onGesturePan(pan)
        }
      }
    }
    // Handle double-tap to zoom
    .then(
      if (config.enableDoubleTapZoom) {
        Modifier.pointerInput(state, config) {
          detectTapGestures(
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
}
