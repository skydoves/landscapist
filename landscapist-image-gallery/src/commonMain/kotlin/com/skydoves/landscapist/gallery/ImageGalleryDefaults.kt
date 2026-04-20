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

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default values for [ImageGallery].
 */
public object ImageGalleryDefaults {

  /** Default grid columns configuration. */
  public val Columns: GridCells = GridCells.Fixed(3)

  /** Default aspect ratio for each grid item (1:1 square). */
  public const val AspectRatio: Float = 1f

  /** Default horizontal spacing between grid items. */
  public val HorizontalSpacing: Dp = 2.dp

  /** Default vertical spacing between grid items. */
  public val VerticalSpacing: Dp = 2.dp
}
