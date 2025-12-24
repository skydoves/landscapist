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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.zoomable.ZoomableConfig
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
 * @param config The [ZoomableConfig] for zoom behavior.
 * @param enabled Whether zoom gestures are enabled.
 * @param modifier The modifier to apply to the composable.
 * @param contentDescription The content description for accessibility.
 */
@Composable
public fun SubSamplingImage(
  subSamplingState: SubSamplingState,
  zoomableState: ZoomableState,
  config: ZoomableConfig = ZoomableConfig(),
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
) {
  var viewportSize by remember { mutableStateOf(IntSize.Zero) }
  var currentFitScale by remember { mutableFloatStateOf(1f) }
  val imageSize = subSamplingState.imageSize

  // Initialize tile grid when viewport size is known
  LaunchedEffect(subSamplingState, viewportSize) {
    if (viewportSize != IntSize.Zero) {
      subSamplingState.initialize(viewportSize)
    }
  }

  // Update visible tiles when transformation changes
  LaunchedEffect(subSamplingState, zoomableState, currentFitScale) {
    snapshotFlow { zoomableState.transformation }
      .collectLatest { transformation ->
        if (viewportSize != IntSize.Zero && currentFitScale > 0f) {
          subSamplingState.updateVisibleTiles(transformation, viewportSize, currentFitScale)
        }
      }
  }

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .clipToBounds()
      .onSizeChanged { size ->
        viewportSize = size
        zoomableState.setLayoutSize(size)
        subSamplingState.initialize(size)
        // Calculate and update fitScale when size changes
        if (imageSize.width > 0 && imageSize.height > 0) {
          currentFitScale = minOf(
            size.width.toFloat() / imageSize.width,
            size.height.toFloat() / imageSize.height,
          )
        }
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
      ),
  ) {
    val transformation = zoomableState.transformation

    if (imageSize.width <= 0 || imageSize.height <= 0) return@Canvas

    // Calculate the scale to fit the image in the viewport
    val fitScale = minOf(
      size.width / imageSize.width,
      size.height / imageSize.height,
    )

    // Update currentFitScale if it changed
    if (fitScale != currentFitScale && fitScale > 0f) {
      currentFitScale = fitScale
    }

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
      // Draw base tile as fallback (scaled from sampled size to full image size)
      subSamplingState.baseTileBitmap?.let { baseBitmap ->
        drawImage(
          image = baseBitmap,
          srcOffset = IntOffset.Zero,
          srcSize = IntSize(baseBitmap.width, baseBitmap.height),
          dstOffset = IntOffset.Zero,
          dstSize = IntSize(imageSize.width, imageSize.height),
          filterQuality = FilterQuality.Low,
        )
      }

      // Draw visible foreground tiles (scaled from sampled size to tile bounds)
      // Add 1 pixel overlap to hide gaps caused by floating-point rounding
      subSamplingState.visibleTiles.forEach { tile ->
        tile.bitmap?.let { bitmap ->
          val tileWidth = tile.bounds.right - tile.bounds.left
          val tileHeight = tile.bounds.bottom - tile.bounds.top

          // Add overlap except at image edges to prevent gaps between tiles
          val overlapRight = if (tile.bounds.right < imageSize.width) 1 else 0
          val overlapBottom = if (tile.bounds.bottom < imageSize.height) 1 else 0

          drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(tile.bounds.left, tile.bounds.top),
            dstSize = IntSize(tileWidth + overlapRight, tileHeight + overlapBottom),
            filterQuality = FilterQuality.None,
          )
        }
      }
    }
  }
}
