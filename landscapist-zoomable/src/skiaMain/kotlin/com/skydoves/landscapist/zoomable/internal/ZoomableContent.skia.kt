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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.skydoves.landscapist.LocalImageSourceBytes
import com.skydoves.landscapist.zoomable.LocalImageRegionDecoder
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState
import com.skydoves.landscapist.zoomable.subsampling.ImageRegionDecoder
import com.skydoves.landscapist.zoomable.subsampling.SubSamplingImage
import com.skydoves.landscapist.zoomable.subsampling.SubSamplingState
import com.skydoves.landscapist.zoomable.subsampling.rememberSubSamplingState

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

    // Show SubSamplingImage with the original content as placeholder
    // The original content is shown until the base tile is loaded
    SubSamplingImageWithPlaceholder(
      subSamplingState = subSamplingState,
      zoomableState = zoomableState,
      config = config,
      enabled = enabled,
      content = content,
    )
  } else {
    // Standard graphicsLayer approach
    StandardZoomableContent(
      zoomableState = zoomableState,
      config = config,
      enabled = enabled,
      content = content,
    )
  }
}

/**
 * SubSamplingImage with placeholder content shown underneath.
 *
 * The placeholder is rendered underneath the SubSamplingImage. Since the
 * SubSamplingImage Canvas is transparent until tiles are drawn, the placeholder
 * shows through initially. Once the base tile loads, it draws on top covering
 * the placeholder.
 */
@Composable
private fun SubSamplingImageWithPlaceholder(
  subSamplingState: SubSamplingState,
  zoomableState: ZoomableState,
  config: ZoomableConfig,
  enabled: Boolean,
  content: @Composable () -> Unit,
) {
  Box(modifier = Modifier.clipToBounds()) {
    // Show original content as placeholder UNDERNEATH
    // This is visible through the transparent SubSamplingImage canvas until tiles load
    StandardZoomableContent(
      zoomableState = zoomableState,
      config = config,
      enabled = false, // Disable gestures on placeholder
      content = content,
    )

    // SubSamplingImage on top - transparent until tiles are drawn
    SubSamplingImage(
      subSamplingState = subSamplingState,
      zoomableState = zoomableState,
      config = config,
      enabled = enabled,
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
