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
package com.skydoves.landscapist.core.decoder

/**
 * A rectangular region within an image.
 *
 * @property left Left edge in pixels.
 * @property top Top edge in pixels.
 * @property right Right edge in pixels.
 * @property bottom Bottom edge in pixels.
 */
public data class ImageRegion(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  /** Width of the region in pixels. */
  val width: Int get() = right - left

  /** Height of the region in pixels. */
  val height: Int get() = bottom - top

  /** Returns true if this region has positive dimensions. */
  val isValid: Boolean get() = width > 0 && height > 0

  public companion object {
    /** Creates a region from position and size. */
    public fun fromSize(x: Int, y: Int, width: Int, height: Int): ImageRegion =
      ImageRegion(x, y, x + width, y + height)
  }
}

/**
 * Result of decoding an image region.
 */
public sealed class RegionDecodeResult {
  /**
   * Successfully decoded region.
   *
   * @property bitmap The decoded bitmap for the region.
   * @property region The region that was decoded.
   * @property sampleSize The sample size used for decoding.
   */
  public data class Success(
    val bitmap: Any,
    val region: ImageRegion,
    val sampleSize: Int,
  ) : RegionDecodeResult()

  /**
   * Decoding failed.
   *
   * @property throwable The error that occurred.
   * @property region The region that failed to decode.
   */
  public data class Error(
    val throwable: Throwable,
    val region: ImageRegion,
  ) : RegionDecodeResult()
}

/**
 * Decoder for loading specific regions of large images.
 *
 * Region decoding is essential for handling very large images (50MP+)
 * that would cause OOM errors if loaded entirely. Instead, only the
 * visible portion of the image is decoded and displayed.
 *
 * Common use cases:
 * - Zoomable image viewers
 * - Large maps and floor plans
 * - High-resolution artwork
 * - Medical imaging
 * - Satellite/aerial photography
 *
 * Usage:
 * ```kotlin
 * val decoder = createRegionDecoder(imageData)
 * if (decoder != null) {
 *   // Decode only the visible region
 *   val result = decoder.decodeRegion(
 *     region = ImageRegion(0, 0, 1080, 1920),
 *     sampleSize = 2
 *   )
 *   when (result) {
 *     is RegionDecodeResult.Success -> displayBitmap(result.bitmap)
 *     is RegionDecodeResult.Error -> showError(result.throwable)
 *   }
 *
 *   // Don't forget to recycle when done
 *   decoder.recycle()
 * }
 * ```
 */
public interface RegionDecoder {
  /**
   * The full width of the image in pixels.
   */
  public val imageWidth: Int

  /**
   * The full height of the image in pixels.
   */
  public val imageHeight: Int

  /**
   * Whether this decoder is still valid (not recycled).
   */
  public val isRecycled: Boolean

  /**
   * Decodes a specific region of the image.
   *
   * @param region The region to decode. Must be within image bounds.
   * @param sampleSize Sample size for downsampling (1 = full size, 2 = half, etc.)
   * @return The decode result containing the bitmap or error.
   */
  public fun decodeRegion(region: ImageRegion, sampleSize: Int = 1): RegionDecodeResult

  /**
   * Releases resources associated with this decoder.
   * The decoder cannot be used after calling this method.
   */
  public fun recycle()
}

/**
 * Creates a platform-specific region decoder.
 *
 * @param data The image data as a byte array.
 * @return A [RegionDecoder] if the format is supported, null otherwise.
 */
public expect fun createRegionDecoder(data: ByteArray): RegionDecoder?

/**
 * Checks if the image data is suitable for region decoding.
 *
 * Region decoding is only supported for certain formats:
 * - JPEG
 * - PNG
 * - WebP (on supported platforms)
 *
 * @param data The image data to check.
 * @return true if the image can be region-decoded.
 */
public fun supportsRegionDecoding(data: ByteArray): Boolean {
  if (data.size < 4) return false

  // Check for JPEG (FFD8FF)
  if (data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte()) {
    return true
  }

  // Check for PNG (89504E47)
  if (data[0] == 0x89.toByte() &&
    data[1] == 'P'.code.toByte() &&
    data[2] == 'N'.code.toByte() &&
    data[3] == 'G'.code.toByte()
  ) {
    return true
  }

  // Check for WebP (RIFF....WEBP)
  if (data.size >= 12 &&
    data[0] == 'R'.code.toByte() &&
    data[1] == 'I'.code.toByte() &&
    data[2] == 'F'.code.toByte() &&
    data[3] == 'F'.code.toByte() &&
    data[8] == 'W'.code.toByte() &&
    data[9] == 'E'.code.toByte() &&
    data[10] == 'B'.code.toByte() &&
    data[11] == 'P'.code.toByte()
  ) {
    return true
  }

  return false
}

/**
 * Configuration for tile-based image loading.
 *
 * When displaying very large images, the image is divided into tiles
 * and only visible tiles are decoded. This configuration controls
 * tile sizing and caching behavior.
 */
public data class TileConfig(
  /**
   * Size of each tile in pixels (tiles are square).
   * Smaller tiles = more granular loading, more overhead.
   * Larger tiles = less overhead, more memory per tile.
   * Default is 512x512 which is a good balance.
   */
  val tileSize: Int = DEFAULT_TILE_SIZE,

  /**
   * Maximum number of tiles to keep in memory.
   * Old tiles are evicted when this limit is reached.
   */
  val maxTilesInMemory: Int = DEFAULT_MAX_TILES,

  /**
   * Whether to prefetch tiles adjacent to visible ones.
   */
  val prefetchEnabled: Boolean = true,

  /**
   * Number of tile rows/columns to prefetch around visible area.
   */
  val prefetchDistance: Int = 1,
) {
  public companion object {
    /** Default tile size in pixels. */
    public const val DEFAULT_TILE_SIZE: Int = 512

    /** Default maximum tiles in memory. */
    public const val DEFAULT_MAX_TILES: Int = 16
  }
}

/**
 * Represents a tile in a large image.
 *
 * @property column Column index (0-based from left).
 * @property row Row index (0-based from top).
 * @property region The pixel region this tile covers.
 * @property sampleSize The sample size used for this tile.
 */
public data class Tile(
  val column: Int,
  val row: Int,
  val region: ImageRegion,
  val sampleSize: Int,
) {
  /** Unique key for this tile at this sample size. */
  val key: String get() = "$column:$row:$sampleSize"
}

/**
 * Calculates which tiles are needed for a visible region.
 *
 * @param visibleRegion The currently visible region in image coordinates.
 * @param imageWidth Full image width.
 * @param imageHeight Full image height.
 * @param config Tile configuration.
 * @param sampleSize Current sample size for display.
 * @return List of tiles that intersect the visible region.
 */
public fun calculateVisibleTiles(
  visibleRegion: ImageRegion,
  imageWidth: Int,
  imageHeight: Int,
  config: TileConfig,
  sampleSize: Int,
): List<Tile> {
  val tileSize = config.tileSize * sampleSize
  val tiles = mutableListOf<Tile>()

  val startCol = (visibleRegion.left / tileSize).coerceAtLeast(0)
  val endCol = ((visibleRegion.right + tileSize - 1) / tileSize)
    .coerceAtMost((imageWidth + tileSize - 1) / tileSize)

  val startRow = (visibleRegion.top / tileSize).coerceAtLeast(0)
  val endRow = ((visibleRegion.bottom + tileSize - 1) / tileSize)
    .coerceAtMost((imageHeight + tileSize - 1) / tileSize)

  for (row in startRow until endRow) {
    for (col in startCol until endCol) {
      val left = col * tileSize
      val top = row * tileSize
      val right = minOf(left + tileSize, imageWidth)
      val bottom = minOf(top + tileSize, imageHeight)

      tiles.add(
        Tile(
          column = col,
          row = row,
          region = ImageRegion(left, top, right, bottom),
          sampleSize = sampleSize,
        ),
      )
    }
  }

  return tiles
}
