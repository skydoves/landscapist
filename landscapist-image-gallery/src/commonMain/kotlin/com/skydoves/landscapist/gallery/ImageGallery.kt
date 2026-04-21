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

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.image.LandscapistImage

/**
 * A grid-based image gallery that displays images in a [LazyVerticalGrid].
 *
 * By default, images are loaded using [LandscapistImage][com.skydoves.landscapist.image.LandscapistImage].
 * Provide a [content] lambda to use your own image loading implementation.
 *
 * **Basic usage:**
 * ```kotlin
 * ImageGallery(
 *   images = urls,
 *   onImageClick = { index, imageModel ->
 *     navController.navigate("viewer/$index")
 *   },
 * )
 * ```
 *
 * **With selection mode:**
 * ```kotlin
 * var selected by remember { mutableStateOf(emptySet<Int>()) }
 *
 * ImageGallery(
 *   images = urls,
 *   selectable = true,
 *   selectedIndices = selected,
 *   onSelectionChanged = { selected = it },
 * )
 * ```
 *
 * @param images The list of image models (URL strings, Uris, Files, etc.) to display.
 *   Each item should have a stable [equals]/[hashCode] implementation for proper grid keying.
 * @param modifier The modifier to apply to the grid.
 * @param state The [ImageGalleryState] to control and observe the gallery.
 * @param columns The grid column configuration.
 * @param contentPadding Padding around the entire grid content.
 * @param horizontalArrangement Horizontal arrangement of grid items.
 * @param verticalArrangement Vertical arrangement of grid items.
 * @param aspectRatio Aspect ratio (width / height) for each grid item. Default is 1f (square).
 *   With [GridCells.Adaptive], the actual item size depends on available width.
 * @param component The [ImageComponent] with plugins for image loading.
 *   Ignored when [content] is provided.
 * @param imageOptions The [ImageOptions] for image display.
 *   Ignored when [content] is provided.
 * @param onImageClick Callback invoked when an image is clicked.
 *   Not fired during active selection mode -- use [onSelectionChanged] instead.
 * @param onImageLongClick Callback invoked when an image is long-clicked.
 *   Not fired during active selection mode.
 * @param selectable Whether selection mode is enabled.
 * @param selectedIndices The set of currently selected item indices.
 * @param onSelectionChanged Callback invoked when the selection changes.
 * @param selectionOverlay Optional composable to render a selection overlay on each item.
 * @param content Optional custom content per item. When provided, the default
 *   [LandscapistImage][com.skydoves.landscapist.image.LandscapistImage] rendering is replaced.
 * @param header Optional composable displayed above the grid items.
 * @param footer Optional composable displayed below the grid items.
 * @param sharedTransition Optional [ImageSharedTransitionConfig] to enable shared element
 *   transitions between gallery items and [ImageViewer] pages. When `null` (default),
 *   no shared bounds are applied. Pass the same config (with matching [ImageSharedTransitionConfig.keyProvider])
 *   to both [ImageGallery] and [ImageViewer] to animate between them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
public fun ImageGallery(
  images: List<Any>,
  modifier: Modifier = Modifier,
  state: ImageGalleryState = rememberImageGalleryState(),
  columns: GridCells = ImageGalleryDefaults.Columns,
  contentPadding: PaddingValues = PaddingValues(),
  horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(
    ImageGalleryDefaults.HorizontalSpacing,
  ),
  verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(
    ImageGalleryDefaults.VerticalSpacing,
  ),
  aspectRatio: Float = ImageGalleryDefaults.AspectRatio,
  component: ImageComponent = rememberImageComponent {},
  imageOptions: ImageOptions = ImageOptions(),
  onImageClick: ((index: Int, imageModel: Any) -> Unit)? = null,
  onImageLongClick: ((index: Int, imageModel: Any) -> Unit)? = null,
  selectable: Boolean = false,
  selectedIndices: Set<Int> = emptySet(),
  onSelectionChanged: ((Set<Int>) -> Unit)? = null,
  selectionOverlay: (@Composable (index: Int, selected: Boolean) -> Unit)? = null,
  content: (@Composable (index: Int, imageModel: Any) -> Unit)? = null,
  header: (@Composable () -> Unit)? = null,
  footer: (@Composable () -> Unit)? = null,
  sharedTransition: ImageSharedTransitionConfig? = null,
) {
  val isSelectionActive = selectable && selectedIndices.isNotEmpty()

  LazyVerticalGrid(
    columns = columns,
    modifier = modifier,
    state = state.lazyGridState,
    contentPadding = contentPadding,
    horizontalArrangement = horizontalArrangement,
    verticalArrangement = verticalArrangement,
  ) {
    // Header
    if (header != null) {
      item(
        span = { GridItemSpan(maxLineSpan) },
        contentType = "header",
      ) {
        header()
      }
    }

    // Image items
    itemsIndexed(
      items = images,
      key = { _, imageModel -> imageModel },
      contentType = { _, _ -> "image" },
    ) { index, imageModel ->
      val isSelected = selectable && selectedIndices.contains(index)
      val sharedBoundsModifier = rememberSharedBoundsModifier(
        config = sharedTransition,
        index = index,
        imageModel = imageModel,
      )

      Box(
        modifier = Modifier
          .aspectRatio(aspectRatio)
          .then(sharedBoundsModifier)
          .combinedClickable(
            onClick = {
              if (isSelectionActive) {
                // In active selection mode: toggle selection, don't fire onImageClick
                val newSelection = if (isSelected) {
                  selectedIndices - index
                } else {
                  selectedIndices + index
                }
                onSelectionChanged?.invoke(newSelection)
              } else {
                onImageClick?.invoke(index, imageModel)
              }
            },
            onLongClick = {
              if (selectable) {
                // Long-click enters selection or toggles item
                val newSelection = if (isSelected) {
                  selectedIndices - index
                } else {
                  selectedIndices + index
                }
                onSelectionChanged?.invoke(newSelection)
              } else {
                onImageLongClick?.invoke(index, imageModel)
              }
            },
          ),
      ) {
        if (content != null) {
          content(index, imageModel)
        } else {
          LandscapistImage(
            imageModel = { imageModel },
            modifier = Modifier.fillMaxWidth(),
            component = component,
            imageOptions = imageOptions,
          )
        }

        // Selection overlay
        if (selectable) {
          selectionOverlay?.invoke(index, isSelected)
        }
      }
    }

    // Footer
    if (footer != null) {
      item(
        span = { GridItemSpan(maxLineSpan) },
        contentType = "footer",
      ) {
        footer()
      }
    }
  }
}
