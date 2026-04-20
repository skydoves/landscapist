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

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.skydoves.landscapist.zoomable.ZoomableState

/**
 * State holder for [ImageViewer] that manages paging and zoom state.
 *
 * Use [rememberImageViewerState] to create and remember an instance.
 *
 * @property pagerState The underlying [PagerState] for the horizontal pager.
 */
@Stable
public class ImageViewerState internal constructor(
  public val pagerState: PagerState,
) {

  /** The currently displayed page index. */
  public val currentPage: Int
    get() = pagerState.currentPage

  /** Whether the current page is zoomed beyond the minimum zoom level. */
  public var isZoomed: Boolean by mutableStateOf(false)
    internal set

  /** Whether a swipe-to-dismiss gesture is in progress. */
  public var isDismissing: Boolean by mutableStateOf(false)
    internal set

  /** The [ZoomableState] for the currently visible page. */
  internal var currentZoomableState: ZoomableState? by mutableStateOf(null)

  /** Animates the pager to the given [page]. */
  public suspend fun animateToPage(page: Int) {
    pagerState.animateScrollToPage(page)
  }

  /** Resets the zoom of the current page to the initial state. */
  public suspend fun resetZoom() {
    currentZoomableState?.resetZoom()
  }
}

/**
 * Creates and remembers an [ImageViewerState].
 *
 * @param initialPage The initial page to display.
 * @param pageCount A lambda returning the total number of pages.
 * @return A remembered [ImageViewerState] instance.
 */
@Composable
public fun rememberImageViewerState(
  initialPage: Int = 0,
  pageCount: () -> Int = { 0 },
): ImageViewerState {
  val pagerState = rememberPagerState(
    initialPage = initialPage,
    pageCount = pageCount,
  )
  return remember(pagerState) {
    ImageViewerState(pagerState = pagerState)
  }
}
