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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.skydoves.landscapist.LocalImageSourceFile
import com.skydoves.landscapist.zoomable.LocalImageRegionDecoder
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState
import com.skydoves.landscapist.zoomable.subsampling.ImageRegionDecoder
import com.skydoves.landscapist.zoomable.subsampling.MinZoomForTiles
import com.skydoves.landscapist.zoomable.subsampling.SubSamplingImage
import com.skydoves.landscapist.zoomable.subsampling.rememberSubSamplingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of [ZoomableContent].
 *
 * When sub-sampling is enabled and an [ImageRegionDecoder] is available via
 * [LocalImageRegionDecoder] or can be created from [LocalImageSourceFile],
 * this uses [SubSamplingImage] for efficient tiled rendering of large images.
 * Otherwise, it falls back to the standard graphicsLayer approach.
 */
@Composable
internal actual fun ZoomableContent(
  zoomableState: ZoomableState,
  config: ZoomableConfig,
  enabled: Boolean,
  onTap: ((Offset) -> Unit)?,
  content: @Composable () -> Unit,
) {
  // First try to get decoder from LocalImageRegionDecoder (explicitly provided)
  // Then try to create one from LocalImageSourceFile (from disk cache)
  val explicitDecoder = LocalImageRegionDecoder.current
  val sourceFile = LocalImageSourceFile.current

  // Use state to hold the decoder created asynchronously
  var createdDecoder by remember { mutableStateOf<ImageRegionDecoder?>(null) }

  // Create decoder on background thread to avoid blocking main thread
  // BitmapRegionDecoder.newInstance() does blocking I/O
  LaunchedEffect(sourceFile) {
    // Close any previously created decoder first
    createdDecoder?.close()
    createdDecoder = null

    // Only create decoder from source file if no explicit decoder is provided
    if (explicitDecoder == null && sourceFile != null) {
      val newDecoder = sourceFile.let { file ->
        if (file.exists() && file.canRead()) {
          withContext(Dispatchers.IO) {
            ImageRegionDecoder.create(file.absolutePath)
          }
        } else {
          null
        }
      }
      createdDecoder = newDecoder
    }
  }

  // Clean up decoder we created when composable leaves composition
  DisposableEffect(Unit) {
    onDispose {
      // Close the decoder we created (not explicitly provided ones)
      createdDecoder?.close()
    }
  }

  // Use explicit decoder if provided, otherwise use the one we created
  val decoder = explicitDecoder ?: createdDecoder

  // Use sub-sampling when enabled and decoder is available
  val currentDecoder = decoder
  if (config.enableSubSampling && currentDecoder != null) {
    val subSamplingState = rememberSubSamplingState(
      decoder = currentDecoder,
      config = config.subSamplingConfig,
    )

    // The caller's content and the tiles, with the tiles taking over once the image is zoomed
    SubSamplingImageWithPlaceholder(
      subSamplingState = subSamplingState,
      zoomableState = zoomableState,
      config = config,
      enabled = enabled,
      onTap = onTap,
      content = content,
    )
  } else {
    // Standard graphicsLayer approach
    StandardZoomableContent(
      zoomableState = zoomableState,
      config = config,
      enabled = enabled,
      onTap = onTap,
      content = content,
    )
  }
}

/**
 * The tiled image and the caller's own content, with one of the two on screen at a time.
 *
 * The tiles take over only once the image is zoomed past [MinZoomForTiles], which is the zoom the
 * state starts loading foreground tiles at. Below it the tiles are one RGB_565 sample of the whole
 * image while the content is a full decode of the same picture, drawn with whatever the caller's
 * plugins put on it, so handing the node to the tiles as soon as a base tile arrived lost quality
 * and cut every painter plugin short. A circular reveal ran its animation to the end against a
 * subtree that had already been taken off the screen, which is why it looked like it never ran.
 *
 * Both stay composed either way. Removing one and putting it back when the zoom crosses the
 * threshold would rebuild it, and a rebuilt reveal starts again from nothing.
 */
@Composable
private fun SubSamplingImageWithPlaceholder(
  subSamplingState: com.skydoves.landscapist.zoomable.subsampling.SubSamplingState,
  zoomableState: ZoomableState,
  config: ZoomableConfig,
  enabled: Boolean,
  onTap: ((Offset) -> Unit)?,
  content: @Composable () -> Unit,
) {
  val tilesInUse = subSamplingState.isBaseLoaded &&
    zoomableState.transformation.scaleValue >= MinZoomForTiles

  Box(modifier = Modifier.clipToBounds()) {
    // Composed at every zoom, so it sizes the grid, loads the base tile and owns the gestures.
    SubSamplingImage(
      modifier = if (tilesInUse) Modifier else Modifier.notDrawn(),
      subSamplingState = subSamplingState,
      zoomableState = zoomableState,
      config = config,
      enabled = enabled,
      onTap = onTap,
    )

    StandardZoomableContent(
      modifier = if (tilesInUse) Modifier.notDrawn() else Modifier,
      zoomableState = zoomableState,
      config = config,
      enabled = false, // The tiled image below owns the gestures.
      content = content,
    )
  }
}

/**
 * Standard zoomable content using graphicsLayer for transformations.
 */
@Composable
internal fun StandardZoomableContent(
  zoomableState: ZoomableState,
  config: ZoomableConfig,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onTap: ((Offset) -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val transformation = zoomableState.transformation

  Box(
    modifier = modifier
      .clipToBounds()
      .onSizeChanged { size ->
        zoomableState.setLayoutSize(size)
      }
      .then(
        if (enabled) {
          Modifier.zoomGestures(
            state = zoomableState,
            config = config,
            onTap = onTap,
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
