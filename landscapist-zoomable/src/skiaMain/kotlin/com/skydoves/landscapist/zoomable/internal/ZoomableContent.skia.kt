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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState

/**
 * Skia implementation of [ZoomableContent].
 *
 * Sub-sampling is not available on Skia platforms, so this always uses
 * the standard graphicsLayer approach for zoom/pan transformations.
 */
@Composable
internal actual fun ZoomableContent(
  zoomableState: ZoomableState,
  config: ZoomableConfig,
  enabled: Boolean,
  content: @Composable () -> Unit,
) {
  val transformation = zoomableState.transformation

  Box(
    modifier = Modifier
      .clipToBounds()
      .onSizeChanged { size ->
        zoomableState.setLayoutSize(size)
      }
      .then(
        if (enabled) {
          Modifier.zoomGestures(
            state = zoomableState,
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
