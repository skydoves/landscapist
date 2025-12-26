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

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A two-tier memory cache that combines a strong LRU cache with a weak reference layer.
 *
 * When items are evicted from the strong LRU cache, they are moved to a weak reference layer.
 * This allows recently evicted items to still be accessible if they haven't been garbage collected.
 *
 * The weak reference layer provides a "second chance" for cached items, improving cache hit rates
 * without consuming additional memory budget - items in the weak layer can be reclaimed by GC
 * when memory is needed.
 *
 * @property maxSize Maximum size of the strong cache in bytes.
 * @property weakReferencesEnabled Whether to enable the weak reference layer.
 */
public class TwoTierMemoryCache(
  private var _maxSize: Long,
  private val weakReferencesEnabled: Boolean = true,
) : MemoryCache {

  private val lock = SynchronizedObject()
  private val strongCache = linkedMapOf<String, CachedImage>()
  private val weakCache = mutableMapOf<String, WeakRef<CachedImage>>()
  private val currentSize = atomic(0L)

  override val maxSize: Long
    get() = _maxSize

  override val size: Long
    get() = currentSize.value

  override val count: Int
    get() = synchronized(lock) { strongCache.size }

  override fun get(key: CacheKey): CachedImage? = synchronized(lock) {
    val memoryKey = key.memoryKey

    // First check strong cache
    strongCache.remove(memoryKey)?.let { image ->
      // Re-insert to update access order
      strongCache[memoryKey] = image
      return@synchronized image
    }

    // Check weak cache if enabled
    if (weakReferencesEnabled) {
      weakCache[memoryKey]?.get()?.let { image ->
        // Promote back to strong cache
        weakCache.remove(memoryKey)
        evictIfNeeded(image.sizeBytes)
        strongCache[memoryKey] = image
        currentSize.addAndGet(image.sizeBytes)
        return@synchronized image
      }

      // Clean up null weak reference
      weakCache.remove(memoryKey)
    }

    null
  }

  override fun set(key: CacheKey, image: CachedImage): Unit = synchronized(lock) {
    val memoryKey = key.memoryKey

    // Remove from weak cache if present
    weakCache.remove(memoryKey)

    // Remove existing entry from strong cache if present
    strongCache.remove(memoryKey)?.let { existing ->
      currentSize.addAndGet(-existing.sizeBytes)
    }

    // Evict entries if needed
    evictIfNeeded(image.sizeBytes)

    // Add new entry to strong cache
    strongCache[memoryKey] = image
    currentSize.addAndGet(image.sizeBytes)
  }

  override fun remove(key: CacheKey): Boolean = synchronized(lock) {
    val memoryKey = key.memoryKey

    // Remove from weak cache
    weakCache.remove(memoryKey)

    // Remove from strong cache
    strongCache.remove(memoryKey)?.let { removed ->
      currentSize.addAndGet(-removed.sizeBytes)
      true
    } ?: false
  }

  override fun clear(): Unit = synchronized(lock) {
    strongCache.clear()
    weakCache.clear()
    currentSize.value = 0
  }

  override fun trimToSize(size: Long): Unit = synchronized(lock) {
    while (currentSize.value > size && strongCache.isNotEmpty()) {
      evictOldest()
    }
  }

  override fun resize(newMaxSize: Long): Unit = synchronized(lock) {
    _maxSize = newMaxSize
    trimToSize(newMaxSize)
  }

  /**
   * Returns the number of items in the strong cache.
   */
  public val strongCacheCount: Int
    get() = synchronized(lock) { strongCache.size }

  /**
   * Returns the number of items in the weak cache.
   * Note: This includes entries whose referents may have been garbage collected.
   */
  public val weakCacheCount: Int
    get() = synchronized(lock) { weakCache.size }

  /**
   * Cleans up weak references that have been garbage collected.
   */
  public fun cleanupWeakReferences(): Unit = synchronized(lock) {
    val iterator = weakCache.iterator()
    while (iterator.hasNext()) {
      if (iterator.next().value.get() == null) {
        iterator.remove()
      }
    }
  }

  private fun evictIfNeeded(requiredSpace: Long) {
    while (currentSize.value + requiredSpace > maxSize && strongCache.isNotEmpty()) {
      evictOldest()
    }
  }

  private fun evictOldest() {
    val iterator = strongCache.entries.iterator()
    if (iterator.hasNext()) {
      val eldest = iterator.next()
      iterator.remove()
      currentSize.addAndGet(-eldest.value.sizeBytes)

      // Move to weak cache if enabled
      if (weakReferencesEnabled) {
        weakCache[eldest.key] = WeakRef(eldest.value)
      }
    }
  }
}
