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
package com.skydoves.landscapist.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.gallery.internal.SwipeToDismissBox
import com.skydoves.landscapist.gallery.internal.ViewerPage
import com.skydoves.landscapist.zoomable.ZoomableConfig
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * A full-screen image viewer with horizontal paging, pinch-to-zoom, and swipe-to-dismiss.
 *
 * By default, images are loaded using [LandscapistImage][com.skydoves.landscapist.image.LandscapistImage].
 * Provide a [content] lambda to use your own image loading implementation (e.g., Glide, Coil).
 *
 * **Basic usage:**
 * ```kotlin
 * ImageViewer(
 *   images = listOf(url1, url2, url3),
 *   state = rememberImageViewerState(pageCount = { 3 }),
 *   onDismiss = { navController.popBackStack() },
 *   onImageTap = { page -> showOverlay = !showOverlay },
 * )
 * ```
 *
 * **With custom image loading:**
 * ```kotlin
 * ImageViewer(
 *   images = urls,
 *   state = rememberImageViewerState(pageCount = { urls.size }),
 *   onDismiss = { finish() },
 *   content = { page, imageModel ->
 *     GlideImage(
 *       imageModel = { imageModel },
 *       component = rememberImageComponent {
 *         +ShimmerPlugin(shimmer = Shimmer.Flash())
 *       },
 *     )
 *   },
 * )
 * ```
 *
 * @param images The list of image models (URL strings, Uris, Files, etc.) to display.
 * @param modifier The modifier to apply to the viewer container.
 * @param state The [ImageViewerState] to control and observe the viewer.
 * @param zoomableConfig Configuration for zoom behavior on each page.
 * @param pageSpacing Horizontal spacing between pages.
 * @param beyondViewportPageCount Number of pages to preload beyond the visible viewport.
 * @param onPageChanged Callback invoked when the current page changes (not on initial composition).
 * @param onImageTap Reserved for future use. Tap gestures currently conflict with
 *   ZoomablePlugin's double-tap detection and are not dispatched.
 * @param onImageLongPress Reserved for future use. Long-press gestures currently conflict with
 *   ZoomablePlugin's gesture detection and are not dispatched.
 * @param onDismiss Callback invoked when a swipe-to-dismiss gesture is triggered.
 * @param enableSwipeToDismiss Whether vertical swipe-to-dismiss is enabled.
 * @param dismissThreshold Fraction of screen height (0.0 ~ 1.0) to trigger dismiss.
 * @param backgroundColor Background color of the viewer.
 * @param component The [ImageComponent] with plugins for image loading.
 *   Ignored when [content] is provided.
 * @param imageOptions The [ImageOptions] for image display.
 *   Ignored when [content] is provided.
 * @param topBar Optional composable displayed at the top of the viewer.
 * @param bottomBar Optional composable displayed at the bottom of the viewer.
 * @param indicator Optional composable for a page indicator, displayed above [bottomBar].
 * @param content Optional custom content per page. When provided, the default
 *   [LandscapistImage][com.skydoves.landscapist.image.LandscapistImage] rendering is replaced
 *   and [component]/[imageOptions] are ignored.
 */
@Composable
public fun ImageViewer(
  images: List<Any>,
  modifier: Modifier = Modifier,
  state: ImageViewerState = rememberImageViewerState(pageCount = { images.size }),
  zoomableConfig: ZoomableConfig = ZoomableConfig(),
  pageSpacing: Dp = ImageViewerDefaults.PageSpacing,
  beyondViewportPageCount: Int = ImageViewerDefaults.BeyondViewportPageCount,
  onPageChanged: ((page: Int) -> Unit)? = null,
  onImageTap: ((page: Int) -> Unit)? = null,
  onImageLongPress: ((page: Int) -> Unit)? = null,
  onDismiss: (() -> Unit)? = null,
  enableSwipeToDismiss: Boolean = true,
  dismissThreshold: Float = ImageViewerDefaults.DismissThreshold,
  backgroundColor: Color = ImageViewerDefaults.BackgroundColor,
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  topBar: (@Composable (currentPage: Int, totalPages: Int) -> Unit)? = null,
  bottomBar: (@Composable (currentPage: Int, totalPages: Int) -> Unit)? = null,
  indicator: (@Composable (currentPage: Int, totalPages: Int) -> Unit)? = null,
  content: (@Composable (page: Int, imageModel: Any) -> Unit)? = null,
) {
  if (images.isEmpty()) return

  // Track dismiss progress for background alpha
  var dismissProgress by remember { mutableFloatStateOf(0f) }

  // Use rememberUpdatedState to avoid stale closures in LaunchedEffect
  val currentOnPageChanged by rememberUpdatedState(onPageChanged)

  // Notify page changes (drop initial emission to avoid firing on first composition)
  LaunchedEffect(state.pagerState) {
    snapshotFlow { state.pagerState.currentPage }
      .distinctUntilChanged()
      .drop(1)
      .collect { page -> currentOnPageChanged?.invoke(page) }
  }

  // Reset isZoomed when the settled page changes to prevent stuck zoom state
  LaunchedEffect(state.pagerState) {
    snapshotFlow { state.pagerState.settledPage }
      .distinctUntilChanged()
      .collect { state.isZoomed = false }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor.copy(alpha = 1f - dismissProgress)),
  ) {
    SwipeToDismissBox(
      enabled = enableSwipeToDismiss && !state.isZoomed,
      threshold = dismissThreshold,
      velocityThreshold = ImageViewerDefaults.DismissVelocityThreshold,
      onDismiss = {
        dismissProgress = 0f
        onDismiss?.invoke()
      },
      onProgressChanged = { dismissProgress = it },
      onDragging = { state.isDismissing = it },
    ) {
      HorizontalPager(
        state = state.pagerState,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = pageSpacing,
        beyondViewportPageCount = beyondViewportPageCount,
        userScrollEnabled = !state.isZoomed,
        key = { images.getOrNull(it) ?: it },
      ) { page ->
        val imageModel = images.getOrNull(page) ?: return@HorizontalPager
        ViewerPage(
          imageModel = imageModel,
          isCurrentPage = page == state.pagerState.settledPage,
          viewerState = state,
          zoomableConfig = zoomableConfig,
          component = component,
          imageOptions = imageOptions,
          onZoomChanged = { isZoomed ->
            if (page == state.currentPage) {
              state.isZoomed = isZoomed
            }
          },
          content = content?.let { customContent -> { model -> customContent(page, model) } },
        )
      }
    }

    // Top bar overlay
    topBar?.let { bar ->
      Box(modifier = Modifier.align(Alignment.TopCenter)) {
        bar(state.currentPage, images.size)
      }
    }

    // Bottom overlays: indicator above bottomBar, stacked in a Column
    if (indicator != null || bottomBar != null) {
      Column(
        modifier = Modifier.align(Alignment.BottomCenter),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        indicator?.invoke(state.currentPage, images.size)
        bottomBar?.invoke(state.currentPage, images.size)
      }
    }
  }
}
