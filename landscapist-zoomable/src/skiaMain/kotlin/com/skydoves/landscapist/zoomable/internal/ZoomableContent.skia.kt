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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.skydoves.landscapist.LocalImageSourceBytes
import com.skydoves.landscapist.zoomable.LocalImageRegionDecoder
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState
import com.skydoves.landscapist.zoomable.subsampling.ImageRegionDecoder
import com.skydoves.landscapist.zoomable.subsampling.MinZoomForTiles
import com.skydoves.landscapist.zoomable.subsampling.SubSamplingImage
import com.skydoves.landscapist.zoomable.subsampling.SubSamplingState
import com.skydoves.landscapist.zoomable.subsampling.rememberSubSamplingState
import kotlinx.coroutines.flow.first

/**
 * Skia implementation of [ZoomableContent].
 *
 * When sub-sampling is enabled and an [ImageRegionDecoder] is available via
 * [LocalImageRegionDecoder] or can be created from [LocalImageSourceBytes],
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
  // Then try to create one from LocalImageSourceBytes (from disk cache)
  val explicitDecoder = LocalImageRegionDecoder.current
  val sourceBytes = LocalImageSourceBytes.current

  val decoder = remember(explicitDecoder, sourceBytes) {
    explicitDecoder ?: sourceBytes?.let { bytes ->
      if (bytes.isNotEmpty()) {
        ImageRegionDecoder.create(bytes)
      } else {
        null
      }
    }
  }

  // Clean up decoder when it changes or composable leaves composition
  DisposableEffect(decoder) {
    onDispose {
      // Only close the decoder if we created it from source bytes
      // Don't close explicitly provided decoders as they're managed externally
      if (explicitDecoder == null && decoder != null) {
        decoder.close()
      }
    }
  }

  // Use sub-sampling when enabled and decoder is available
  if (config.enableSubSampling && decoder != null) {
    val subSamplingState = rememberSubSamplingState(
      decoder = decoder,
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
 * state starts loading foreground tiles at. Below it the tiles are one sample of the whole image
 * while the content is a full decode of the same picture, drawn with whatever the caller's plugins
 * put on it, so letting the tiles paint over it as soon as a base tile arrived lost quality and cut
 * every painter plugin short. A circular reveal ran its animation to the end underneath the tiles,
 * which is why it looked like it never ran.
 *
 * Both stay composed either way. Removing one and putting it back when the zoom crosses the
 * threshold would rebuild it, and a rebuilt reveal starts again from nothing.
 */
@Composable
private fun SubSamplingImageWithPlaceholder(
  subSamplingState: SubSamplingState,
  zoomableState: ZoomableState,
  config: ZoomableConfig,
  enabled: Boolean,
  onTap: ((Offset) -> Unit)?,
  content: @Composable () -> Unit,
) {
  // Latched, not tracked. The tiles are drawn fitted inside the box while the content fills it,
  // so the two do not frame the image the same way and every crossing of the threshold would move
  // the picture. Handing over once, on the first deliberate zoom, is one re-frame where the user
  // is already changing the framing, instead of one per pinch.
  val handedOver = remember(subSamplingState) { mutableStateOf(false) }
  LaunchedEffect(subSamplingState, zoomableState) {
    snapshotFlow {
      subSamplingState.isBaseLoaded &&
        zoomableState.transformation.scaleValue >= MinZoomForTiles
    }.first { it }
    handedOver.value = true
  }
  val tilesInUse = handedOver.value

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
