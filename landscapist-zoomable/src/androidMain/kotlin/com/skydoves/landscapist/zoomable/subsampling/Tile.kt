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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect

/**
 * Represents a tile of a sub-sampled image.
 *
 * @property bounds The region of the original image that this tile represents.
 * @property sampleSize The sample size used to decode this tile (power of 2).
 *   A value of 1 means full resolution, 2 means 1/2 resolution, etc.
 * @property bitmap The decoded bitmap for this tile, or null if not yet loaded.
 * @property isBase Whether this is the base (low-resolution fallback) tile.
 * @property isVisible Whether this tile is currently visible in the viewport.
 * @property isLoading Whether this tile is currently being loaded.
 */
@Immutable
public data class Tile(
  public val bounds: IntRect,
  public val sampleSize: Int,
  public val bitmap: ImageBitmap? = null,
  public val isBase: Boolean = false,
  public val isVisible: Boolean = false,
  public val isLoading: Boolean = false,
) {
  /**
   * The key for this tile, used for caching and identification.
   */
  public val key: TileKey
    get() = TileKey(bounds, sampleSize)

  /**
   * Whether this tile has been loaded with a bitmap.
   */
  public val isLoaded: Boolean
    get() = bitmap != null
}

/**
 * A unique key for identifying tiles.
 *
 * @property bounds The region bounds.
 * @property sampleSize The sample size.
 */
@Immutable
public data class TileKey(
  public val bounds: IntRect,
  public val sampleSize: Int,
)
