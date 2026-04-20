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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.ImagePluginComponent
import com.skydoves.landscapist.components.imagePlugins
import com.skydoves.landscapist.gallery.ImageViewerState
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import com.skydoves.landscapist.zoomable.rememberZoomableState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Internal composable that renders a single page in the [ImageViewer][com.skydoves.landscapist.gallery.ImageViewer].
 *
 * Each page has its own [ZoomableState][com.skydoves.landscapist.zoomable.ZoomableState]
 * and supports pinch-to-zoom, double-tap zoom, and pan gestures.
 *
 * Tap and long-press gestures are intentionally NOT handled at this level to avoid
 * conflicting with ZoomablePlugin's double-tap gesture detection. These callbacks
 * are reserved for future use when integrated into the zoom gesture detector.
 *
 * @param imageModel The image model (URL, Uri, etc.) to load.
 * @param isCurrentPage Whether this page is the currently settled page.
 * @param viewerState The parent [ImageViewerState] to wire ZoomableState back.
 * @param zoomableConfig The zoom behavior configuration.
 * @param component The image component with plugins.
 * @param imageOptions The image display options.
 * @param onZoomChanged Callback invoked when the zoom state changes.
 * @param content Optional custom content. If null, [LandscapistImage] is used.
 *   Note: when custom content is provided, ZoomablePlugin is not applied and the caller
 *   is responsible for zoom handling.
 */
@Composable
internal fun ViewerPage(
  imageModel: Any,
  isCurrentPage: Boolean,
  viewerState: ImageViewerState,
  zoomableConfig: ZoomableConfig,
  component: ImageComponent,
  imageOptions: ImageOptions,
  onZoomChanged: (isZoomed: Boolean) -> Unit,
  content: (@Composable (imageModel: Any) -> Unit)?,
) {
  val zoomableState = rememberZoomableState(
    config = zoomableConfig,
    resetKey = imageModel,
  )

  // Wire ZoomableState back to the parent ImageViewerState for resetZoom() support
  LaunchedEffect(isCurrentPage, zoomableState) {
    if (isCurrentPage) {
      viewerState.currentZoomableState = zoomableState
    }
  }

  // Clean up when this page leaves composition
  DisposableEffect(zoomableState) {
    onDispose {
      if (viewerState.currentZoomableState === zoomableState) {
        viewerState.currentZoomableState = null
      }
    }
  }

  // Report zoom state changes (use rememberUpdatedState to avoid stale closure)
  val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
  LaunchedEffect(zoomableState) {
    snapshotFlow { zoomableState.isZoomed }
      .distinctUntilChanged()
      .collect { currentOnZoomChanged(it) }
  }

  // Build component with ZoomablePlugin, remembered to avoid recreation on recomposition
  val zoomableComponent = remember(component, zoomableState) {
    ImagePluginComponent(
      component.imagePlugins.toMutableList(),
    ).apply {
      add(
        ZoomablePlugin(
          state = zoomableState,
          enabled = true,
        ),
      )
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    if (content != null) {
      content(imageModel)
    } else {
      LandscapistImage(
        imageModel = { imageModel },
        modifier = Modifier.fillMaxSize(),
        component = zoomableComponent,
        imageOptions = imageOptions,
      )
    }
  }
}
