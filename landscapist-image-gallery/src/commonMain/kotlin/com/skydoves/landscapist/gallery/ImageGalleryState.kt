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

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * State holder for [ImageGallery] that manages grid scroll state.
 *
 * Use [rememberImageGalleryState] to create and remember an instance.
 *
 * @property lazyGridState The underlying [LazyGridState] for the grid.
 */
@Stable
public class ImageGalleryState internal constructor(
  public val lazyGridState: LazyGridState,
)

/**
 * Creates and remembers an [ImageGalleryState].
 *
 * @param lazyGridState The [LazyGridState] to use for the grid.
 * @return A remembered [ImageGalleryState] instance.
 */
@Composable
public fun rememberImageGalleryState(
  lazyGridState: LazyGridState = rememberLazyGridState(),
): ImageGalleryState {
  return remember(lazyGridState) {
    ImageGalleryState(lazyGridState = lazyGridState)
  }
}
