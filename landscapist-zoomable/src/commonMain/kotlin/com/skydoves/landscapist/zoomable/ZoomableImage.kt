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
package com.skydoves.landscapist.zoomable

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.skydoves.landscapist.zoomable.internal.zoomGestures

/**
 * A composable that wraps content with zoomable functionality.
 *
 * This composable provides pan and pinch-to-zoom gestures for any content placed inside it.
 * It's ideal for displaying images that need to be zoomed and panned.
 *
 * @param modifier The modifier to be applied to the container.
 * @param state The [ZoomableState] for controlling zoom and pan transformations.
 * @param config The [ZoomableConfig] for customizing zoom behavior.
 * @param enabled Whether zoom gestures are enabled.
 * @param onTransformChanged Callback invoked when the transformation changes.
 * @param content The content to be made zoomable.
 */
@Composable
public fun ZoomableImage(
  modifier: Modifier = Modifier,
  state: ZoomableState = rememberZoomableState(),
  config: ZoomableConfig = ZoomableConfig(),
  enabled: Boolean = true,
  onTransformChanged: ((ContentTransformation) -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val transformation = state.transformation

  // Notify callback when transformation changes
  onTransformChanged?.invoke(transformation)

  Box(
    modifier = modifier
      .then(
        if (enabled) {
          Modifier.zoomGestures(
            state = state,
            config = config,
          )
        } else {
          Modifier
        },
      )
      .graphicsLayer {
        scaleX = transformation.scale.scaleX
        scaleY = transformation.scale.scaleY
        translationX = transformation.offset.x
        translationY = transformation.offset.y
        rotationZ = transformation.rotationZ
      },
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/**
 * Modifier extension to make any composable zoomable.
 *
 * @param state The [ZoomableState] for controlling zoom and pan transformations.
 * @param config The [ZoomableConfig] for customizing zoom behavior.
 * @param enabled Whether zoom gestures are enabled.
 * @return A modifier that applies zoomable behavior.
 */
public fun Modifier.zoomable(
  state: ZoomableState,
  config: ZoomableConfig = ZoomableConfig(),
  enabled: Boolean = true,
): Modifier = this.then(
  if (enabled) {
    Modifier
      .zoomGestures(state = state, config = config)
      .graphicsLayer {
        val transformation = state.transformation
        scaleX = transformation.scale.scaleX
        scaleY = transformation.scale.scaleY
        translationX = transformation.offset.x
        translationY = transformation.offset.y
        rotationZ = transformation.rotationZ
      }
  } else {
    Modifier
  },
)
