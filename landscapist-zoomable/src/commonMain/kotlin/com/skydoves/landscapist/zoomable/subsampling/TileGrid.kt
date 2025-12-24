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

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Generates a grid of tiles for sub-sampling a large image.
 */
public object TileGrid {

  /**
   * Default tile size in pixels.
   */
  private const val DEFAULT_TILE_SIZE_PX = 256

  /**
   * Generates tiles for a given image and viewport size.
   *
   * @param imageSize The original image size in pixels.
   * @param viewportSize The viewport size in pixels.
   * @param tileSizePx The target tile size in pixels.
   * @return A map of sample sizes to their corresponding tile lists.
   */
  public fun generate(
    imageSize: IntSize,
    viewportSize: IntSize,
    tileSizePx: Int = DEFAULT_TILE_SIZE_PX,
  ): TileGridResult {
    if (imageSize.width <= 0 || imageSize.height <= 0) {
      return TileGridResult(emptyMap(), null)
    }

    // Calculate the base sample size (for the lowest resolution layer)
    val baseSampleSize = calculateBaseSampleSize(imageSize, viewportSize)

    // Create the base tile (covers the entire image at low resolution)
    val baseTile = Tile(
      bounds = IntRect(0, 0, imageSize.width, imageSize.height),
      sampleSize = baseSampleSize,
      isBase = true,
    )

    // Generate foreground tiles for each sample size level
    val foregroundTiles = mutableMapOf<Int, List<Tile>>()

    var sampleSize = baseSampleSize
    while (sampleSize >= 1) {
      val tiles = generateTilesForSampleSize(
        imageSize = imageSize,
        tileSizePx = tileSizePx,
        sampleSize = sampleSize,
      )
      foregroundTiles[sampleSize] = tiles
      sampleSize /= 2
    }

    return TileGridResult(foregroundTiles, baseTile)
  }

  /**
   * Calculates the base sample size for the lowest resolution layer.
   */
  private fun calculateBaseSampleSize(imageSize: IntSize, viewportSize: IntSize): Int {
    val scaleX = imageSize.width.toFloat() / viewportSize.width
    val scaleY = imageSize.height.toFloat() / viewportSize.height
    val scale = max(scaleX, scaleY)

    var sampleSize = 1
    while (sampleSize * 2 <= scale) {
      sampleSize *= 2
    }
    return max(1, sampleSize)
  }

  /**
   * Generates tiles for a specific sample size.
   */
  private fun generateTilesForSampleSize(
    imageSize: IntSize,
    tileSizePx: Int,
    sampleSize: Int,
  ): List<Tile> {
    val tiles = mutableListOf<Tile>()

    // Calculate effective tile size at this sample level
    val effectiveTileSize = tileSizePx * sampleSize

    // Calculate number of tiles in each dimension
    val tilesX = ceil(imageSize.width.toFloat() / effectiveTileSize).toInt()
    val tilesY = ceil(imageSize.height.toFloat() / effectiveTileSize).toInt()

    for (y in 0 until tilesY) {
      for (x in 0 until tilesX) {
        val left = x * effectiveTileSize
        val top = y * effectiveTileSize
        val right = min(left + effectiveTileSize, imageSize.width)
        val bottom = min(top + effectiveTileSize, imageSize.height)

        tiles.add(
          Tile(
            bounds = IntRect(left, top, right, bottom),
            sampleSize = sampleSize,
            isBase = false,
          ),
        )
      }
    }

    return tiles
  }

  /**
   * Calculates the appropriate sample size for a given zoom level.
   *
   * @param zoom The current zoom level (1.0 = no zoom).
   * @return The sample size to use (power of 2).
   */
  public fun calculateSampleSizeForZoom(zoom: Float): Int {
    var sampleSize = 1
    while (sampleSize * 2 <= (1 / zoom)) {
      sampleSize *= 2
    }
    return max(1, sampleSize)
  }

  /**
   * Filters tiles to only those visible in the current viewport.
   *
   * @param tiles The list of tiles to filter.
   * @param viewportBounds The current viewport bounds in image coordinates.
   * @return A list of tiles that intersect with the viewport.
   */
  public fun filterVisibleTiles(
    tiles: List<Tile>,
    viewportBounds: IntRect,
  ): List<Tile> {
    return tiles.map { tile ->
      tile.copy(isVisible = tile.bounds.overlaps(viewportBounds))
    }
  }
}

/**
 * Result of tile grid generation.
 *
 * @property foreground A map of sample sizes to their corresponding tile lists.
 * @property base The base (low-resolution fallback) tile.
 */
public data class TileGridResult(
  public val foreground: Map<Int, List<Tile>>,
  public val base: Tile?,
)
