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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.zoomable.ContentTransformation
import com.skydoves.landscapist.zoomable.SubSamplingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
  val density = LocalDensity.current
  val tileSizePx = with(density) { config.tileSize.roundToPx() }
  return remember(decoder, config, tileSizePx) {
    SubSamplingState(decoder, tileSizePx, scope)
  }
}

/**
 * State holder for sub-sampled image loading and tile management.
 *
 * This class manages the loading of image tiles based on the current viewport
 * and zoom level, efficiently loading only the visible portions of large images.
 *
 * @property decoder The [ImageRegionDecoder] for loading image regions.
 * @property tileSizePx The tile size in pixels.
 * @property scope The coroutine scope for loading operations.
 */
@Stable
public class SubSamplingState internal constructor(
  private val decoder: ImageRegionDecoder,
  private val tileSizePx: Int,
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
      tileSizePx = tileSizePx,
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
   * @param fitScale The scale factor used to fit the image in the viewport.
   */
  public fun updateVisibleTiles(
    transformation: ContentTransformation,
    viewportSize: IntSize,
    fitScale: Float = 1f,
  ) {
    val grid = tileGrid ?: return

    // The effective scale includes both the user's zoom and the fit scale
    val effectiveScale = transformation.scaleValue * fitScale

    // Only load foreground tiles when zoomed beyond 1.5x
    // At lower zoom levels, the base tile is sufficient
    if (transformation.scaleValue < 1.5f) {
      visibleTiles = emptyList()
      // Cancel any pending loads
      loadingJobs.values.forEach { it.cancel() }
      loadingJobs.clear()
      return
    }

    val sampleSize = TileGrid.calculateSampleSizeForZoom(effectiveScale)

    // Get tiles for the current sample size
    val tilesForSampleSize = grid.foreground[sampleSize] ?: return

    // Calculate the visible region in image coordinates
    val visibleRegion = calculateVisibleRegion(transformation, viewportSize, fitScale)

    // Filter to visible tiles and update their bitmaps from cache
    val visible = TileGrid.filterVisibleTiles(tilesForSampleSize, visibleRegion)
      .filter { it.isVisible }
      .map { tile ->
        tile.copy(bitmap = tileCache[tile.key])
      }

    visibleTiles = visible

    // Load tiles that are visible but not yet cached
    // Limit concurrent loads to prevent overwhelming the system
    val tilesToLoad = visible
      .filter { it.bitmap == null && !loadingJobs.containsKey(it.key) }
      .take(2) // Only load up to 2 tiles at a time

    tilesToLoad.forEach { tile ->
      loadTile(tile) { bitmap ->
        if (bitmap != null) {
          tileCache[tile.key] = bitmap
          // Only update if this tile is still in the visible list
          val currentVisible = visibleTiles
          if (currentVisible.any { it.key == tile.key }) {
            visibleTiles = currentVisible.map { t ->
              if (t.key == tile.key) t.copy(bitmap = bitmap) else t
            }
          }
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
   *
   * @param transformation The current zoom/pan transformation.
   * @param viewportSize The size of the viewport in pixels.
   * @param fitScale The scale factor used to fit the image in the viewport.
   */
  private fun calculateVisibleRegion(
    transformation: ContentTransformation,
    viewportSize: IntSize,
    fitScale: Float,
  ): IntRect {
    // The effective scale combines user zoom and fit scale
    val effectiveScale = transformation.scaleValue * fitScale
    val offsetX = transformation.offset.x
    val offsetY = transformation.offset.y

    // The image is centered in the viewport, so we need to account for centering
    val scaledImageWidth = imageSize.width * effectiveScale
    val scaledImageHeight = imageSize.height * effectiveScale

    // Calculate the offset from the image's top-left corner
    // The image is centered, so at offset (0,0), the image center is at viewport center
    val imageOffsetX = (viewportSize.width - scaledImageWidth) / 2 + offsetX
    val imageOffsetY = (viewportSize.height - scaledImageHeight) / 2 + offsetY

    // Calculate visible region in image coordinates
    val left = ((-imageOffsetX) / effectiveScale).roundToInt().coerceIn(0, imageSize.width)
    val top = ((-imageOffsetY) / effectiveScale).roundToInt().coerceIn(0, imageSize.height)
    val right = ((viewportSize.width - imageOffsetX) / effectiveScale).roundToInt()
      .coerceIn(0, imageSize.width)
    val bottom = ((viewportSize.height - imageOffsetY) / effectiveScale).roundToInt()
      .coerceIn(0, imageSize.height)

    return IntRect(left, top, right, bottom)
  }

  /**
   * Loads a tile asynchronously on IO dispatcher.
   */
  private fun loadTile(tile: Tile, onLoaded: (ImageBitmap?) -> Unit) {
    val job = scope.launch(Dispatchers.IO) {
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
