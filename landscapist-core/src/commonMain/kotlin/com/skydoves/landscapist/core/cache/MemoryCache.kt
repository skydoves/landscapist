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
package com.skydoves.landscapist.core.cache

import com.skydoves.landscapist.core.model.DataSource

/**
 * Interface for memory cache operations.
 */
public interface MemoryCache {
  /** Current size of the cache in bytes. */
  public val size: Long

  /** Maximum size of the cache in bytes. */
  public val maxSize: Long

  /** Number of entries in the cache. */
  public val count: Int
    get() = 0

  /**
   * Gets a cached image by its key.
   *
   * @param key The cache key.
   * @return The cached image, or null if not found.
   */
  public operator fun get(key: CacheKey): CachedImage?

  /**
   * Gets a cached image for the same model and transformations as [key], at any target size.
   *
   * A composable knows its model before its measured size, so an exact [get] misses on the first
   * frame even when the image is in memory.
   *
   * @param key The cache key. Only its [CacheKey.baseKey] is matched; the target size is ignored.
   * @return A cached image for any size of this key, or null if nothing is cached. Implementations
   * that cannot answer this cheaply return null.
   */
  public fun getIgnoringSize(key: CacheKey): CachedImage? = null

  /**
   * Gets the image cached for [key], or an already decoded variant of the same image and
   * transformations that [isAcceptable] approves.
   *
   * A lookup marks what it returns as recently used, which can promote it out of a weak tier and
   * evict others, so a variant [isAcceptable] turns down must not be marked.
   *
   * @param key The cache key. The exact size is preferred; other sizes are offered to
   * [isAcceptable] from the most recently cached to the least.
   * @param isAcceptable Whether the variant cached under the given key can serve this request.
   * The key carries the target size that variant was decoded for.
   * @return The exact entry, the first accepted variant, or null. An implementation that does not
   * override this falls back to [getIgnoringSize], so a cache written before this existed keeps
   * whatever variant lookup it had.
   */
  public fun getMatching(
    key: CacheKey,
    isAcceptable: (CacheKey, CachedImage) -> Boolean,
  ): CachedImage? = get(key) ?: getIgnoringSize(key)?.takeIf { isAcceptable(key, it) }

  /**
   * Stores an image in the cache.
   *
   * @param key The cache key.
   * @param image The image to cache.
   */
  public operator fun set(key: CacheKey, image: CachedImage)

  /**
   * Removes an image from the cache.
   *
   * @param key The cache key.
   * @return true if the image was removed, false if it wasn't in the cache.
   */
  public fun remove(key: CacheKey): Boolean

  /**
   * Clears all entries from the cache.
   */
  public fun clear()

  /**
   * Trims the cache to the specified size.
   *
   * @param size The target size in bytes.
   */
  public fun trimToSize(size: Long)

  /**
   * Resizes the cache to the new maximum size.
   * If the new size is smaller than current size, entries will be evicted.
   *
   * @param newMaxSize The new maximum size in bytes.
   */
  public fun resize(newMaxSize: Long) {
    if (newMaxSize < size) {
      trimToSize(newMaxSize)
    }
  }
}

/**
 * Represents a cached image with metadata.
 *
 * @property data The image data (platform-specific bitmap type).
 * @property dataSource The source from which the image was originally loaded.
 * @property sizeBytes The size of the image in bytes.
 * @property originalWidth The original width of the image before any transformations.
 * @property originalHeight The original height of the image before any transformations.
 * @property diskCachePath The disk cache file path, if available. Used by sub-sampling plugins
 *   to create a region decoder from the cached file on subsequent memory cache hits.
 */
public data class CachedImage(
  val data: Any,
  val dataSource: DataSource,
  val sizeBytes: Long,
  val originalWidth: Int = 0,
  val originalHeight: Int = 0,
  val diskCachePath: String? = null,
)
