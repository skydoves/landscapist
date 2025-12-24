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
package com.skydoves.landscapist.zoomable.subsampling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.zoomable.ZoomableState
import com.skydoves.landscapist.zoomable.internal.zoomGestures
import kotlinx.coroutines.flow.collectLatest

/**
 * A composable that displays a sub-sampled image with zoom and pan support.
 *
 * This composable efficiently loads and displays large images by breaking them
 * into tiles that are loaded on-demand based on the current viewport and zoom level.
 *
 * @param subSamplingState The [SubSamplingState] managing tile loading.
 * @param zoomableState The [ZoomableState] managing zoom transformations.
 * @param modifier The modifier to apply to the composable.
 * @param contentDescription The content description for accessibility.
 */
@Composable
public fun SubSamplingImage(
  subSamplingState: SubSamplingState,
  zoomableState: ZoomableState,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
) {
  var viewportSize = IntSize.Zero

  // Initialize tile grid when viewport size is known
  LaunchedEffect(subSamplingState, viewportSize) {
    if (viewportSize != IntSize.Zero) {
      subSamplingState.initialize(viewportSize)
    }
  }

  // Update visible tiles when transformation changes
  LaunchedEffect(subSamplingState, zoomableState) {
    snapshotFlow { zoomableState.transformation }
      .collectLatest { transformation ->
        if (viewportSize != IntSize.Zero) {
          subSamplingState.updateVisibleTiles(transformation, viewportSize)
        }
      }
  }

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .onSizeChanged { size ->
        viewportSize = size
        subSamplingState.initialize(size)
      }
      .zoomGestures(
        state = zoomableState,
        config = zoomableState.config,
      ),
  ) {
    val transformation = zoomableState.transformation
    val imageSize = subSamplingState.imageSize

    if (imageSize.width <= 0 || imageSize.height <= 0) return@Canvas

    // Calculate the scale to fit the image in the viewport
    val fitScale = minOf(
      size.width / imageSize.width,
      size.height / imageSize.height,
    )

    withTransform({
      // Apply user transformation
      translate(
        left = transformation.offset.x + size.width / 2,
        top = transformation.offset.y + size.height / 2,
      )
      scale(
        scaleX = transformation.scale.scaleX * fitScale,
        scaleY = transformation.scale.scaleY * fitScale,
        pivot = Offset.Zero,
      )
      translate(
        left = -imageSize.width / 2f,
        top = -imageSize.height / 2f,
      )
    }) {
      // Draw base tile as fallback
      subSamplingState.baseTileBitmap?.let { baseBitmap ->
        drawImage(
          image = baseBitmap,
          dstSize = IntSize(imageSize.width, imageSize.height),
        )
      }

      // Draw visible foreground tiles
      subSamplingState.visibleTiles.forEach { tile ->
        tile.bitmap?.let { bitmap ->
          val tileSize = Size(
            width = (tile.bounds.right - tile.bounds.left).toFloat(),
            height = (tile.bounds.bottom - tile.bounds.top).toFloat(),
          )

          drawImage(
            image = bitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(
              tile.bounds.left,
              tile.bounds.top,
            ),
            dstSize = IntSize(
              tileSize.width.toInt(),
              tileSize.height.toInt(),
            ),
          )
        }
      }
    }
  }
}
