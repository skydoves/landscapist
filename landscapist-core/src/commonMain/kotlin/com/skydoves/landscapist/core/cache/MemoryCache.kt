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
 */
public data class CachedImage(
  val data: Any,
  val dataSource: DataSource,
  val sizeBytes: Long,
)
