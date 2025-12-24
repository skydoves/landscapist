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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.zoomable.ContentTransformation
import com.skydoves.landscapist.zoomable.SubSamplingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Creates and remembers a [SubSamplingState] for managing tiled image loading.
 *
 * @param decoder The [ImageRegionDecoder] for loading image regions.
 * @param config The sub-sampling configuration.
 * @return A remembered [SubSamplingState] instance.
 */
@Composable
public fun rememberSubSamplingState(
  decoder: ImageRegionDecoder,
  config: SubSamplingConfig = SubSamplingConfig(),
): SubSamplingState {
  val scope = rememberCoroutineScope()
  return remember(decoder, config) {
    SubSamplingState(decoder, config, scope)
  }
}

/**
 * State holder for sub-sampled image loading and tile management.
 *
 * This class manages the loading of image tiles based on the current viewport
 * and zoom level, efficiently loading only the visible portions of large images.
 *
 * @property decoder The [ImageRegionDecoder] for loading image regions.
 * @property config The sub-sampling configuration.
 * @property scope The coroutine scope for loading operations.
 */
@Stable
public class SubSamplingState internal constructor(
  private val decoder: ImageRegionDecoder,
  private val config: SubSamplingConfig,
  private val scope: CoroutineScope,
) {
  /**
   * The original image size.
   */
  public val imageSize: IntSize
    get() = decoder.imageSize

  /**
   * The generated tile grid.
   */
  private var tileGrid: TileGridResult? = null

  /**
   * Cache of loaded tile bitmaps.
   */
  private val tileCache = mutableStateMapOf<TileKey, ImageBitmap>()

  /**
   * Currently loading jobs.
   */
  private val loadingJobs = mutableMapOf<TileKey, Job>()

  /**
   * The base tile bitmap.
   */
  public var baseTileBitmap: ImageBitmap? by mutableStateOf(null)
    private set

  /**
   * The current visible tiles with their bitmaps.
   */
  public var visibleTiles: List<Tile> by mutableStateOf(emptyList())
    private set

  /**
   * Whether the base tile has been loaded.
   */
  public val isBaseLoaded: Boolean
    get() = baseTileBitmap != null

  /**
   * Initializes the tile grid for the given viewport size.
   *
   * @param viewportSize The size of the viewport in pixels.
   */
  public fun initialize(viewportSize: IntSize) {
    if (tileGrid != null) return

    tileGrid = TileGrid.generate(
      imageSize = imageSize,
      viewportSize = viewportSize,
      tileSize = config.tileSize,
    )

    // Load the base tile immediately
    tileGrid?.base?.let { baseTile ->
      loadTile(baseTile) { bitmap ->
        baseTileBitmap = bitmap
      }
    }
  }

  /**
   * Updates the visible tiles based on the current transformation and viewport.
   *
   * @param transformation The current zoom/pan transformation.
   * @param viewportSize The size of the viewport in pixels.
   */
  public fun updateVisibleTiles(
    transformation: ContentTransformation,
    viewportSize: IntSize,
  ) {
    val grid = tileGrid ?: return

    val scale = transformation.scaleValue
    val sampleSize = TileGrid.calculateSampleSizeForZoom(scale)

    // Get tiles for the current sample size
    val tilesForSampleSize = grid.foreground[sampleSize] ?: return

    // Calculate the visible region in image coordinates
    val visibleRegion = calculateVisibleRegion(transformation, viewportSize)

    // Filter to visible tiles and update their bitmaps from cache
    val visible = TileGrid.filterVisibleTiles(tilesForSampleSize, visibleRegion)
      .filter { it.isVisible }
      .map { tile ->
        tile.copy(bitmap = tileCache[tile.key])
      }

    visibleTiles = visible

    // Load tiles that are visible but not yet cached
    visible.filter { it.bitmap == null && !loadingJobs.containsKey(it.key) }
      .forEach { tile ->
        loadTile(tile) { bitmap ->
          if (bitmap != null) {
            tileCache[tile.key] = bitmap
            // Trigger recomposition by updating visible tiles
            updateVisibleTiles(transformation, viewportSize)
          }
        }
      }

    // Cancel loading jobs for tiles that are no longer visible
    val visibleKeys = visible.map { it.key }.toSet()
    loadingJobs.keys.filter { it !in visibleKeys }.forEach { key ->
      loadingJobs[key]?.cancel()
      loadingJobs.remove(key)
    }
  }

  /**
   * Calculates the visible region in image coordinates.
   */
  private fun calculateVisibleRegion(
    transformation: ContentTransformation,
    viewportSize: IntSize,
  ): IntRect {
    val scale = transformation.scaleValue
    val offsetX = transformation.offset.x
    val offsetY = transformation.offset.y

    // Calculate the visible region in image coordinates
    val left = (-offsetX / scale).roundToInt().coerceAtLeast(0)
    val top = (-offsetY / scale).roundToInt().coerceAtLeast(0)
    val right = ((-offsetX + viewportSize.width) / scale).roundToInt()
      .coerceAtMost(imageSize.width)
    val bottom = ((-offsetY + viewportSize.height) / scale).roundToInt()
      .coerceAtMost(imageSize.height)

    return IntRect(left, top, right, bottom)
  }

  /**
   * Loads a tile asynchronously.
   */
  private fun loadTile(tile: Tile, onLoaded: (ImageBitmap?) -> Unit) {
    val job = scope.launch {
      val bitmap = decoder.decodeRegion(
        region = tile.bounds,
        sampleSize = tile.sampleSize,
      )
      onLoaded(bitmap)
    }
    loadingJobs[tile.key] = job
  }

  /**
   * Clears all cached tiles and cancels loading jobs.
   */
  public fun clear() {
    loadingJobs.values.forEach { it.cancel() }
    loadingJobs.clear()
    tileCache.clear()
    baseTileBitmap = null
    visibleTiles = emptyList()
  }
}
