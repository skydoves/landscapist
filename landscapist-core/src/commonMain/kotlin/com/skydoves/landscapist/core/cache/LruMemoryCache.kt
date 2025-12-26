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
 * LRU (Least Recently Used) implementation of [MemoryCache].
 *
 * Thread-safe implementation using a synchronized LinkedHashMap with access-order.
 *
 * @property maxSize Maximum size of the cache in bytes.
 */
public class LruMemoryCache(
  private var _maxSize: Long,
) : MemoryCache {

  private val lock = SynchronizedObject()
  private val cache = linkedMapOf<String, CachedImage>()
  private val currentSize = atomic(0L)

  override val maxSize: Long
    get() = _maxSize

  override val size: Long
    get() = currentSize.value

  override val count: Int
    get() = synchronized(lock) { cache.size }

  override fun get(key: CacheKey): CachedImage? = synchronized(lock) {
    val memoryKey = key.memoryKey
    // Re-insert to update access order
    cache.remove(memoryKey)?.also { image ->
      cache[memoryKey] = image
    }
  }

  override fun set(key: CacheKey, image: CachedImage): Unit = synchronized(lock) {
    val memoryKey = key.memoryKey

    // Remove existing entry if present
    cache.remove(memoryKey)?.let { existing ->
      currentSize.addAndGet(-existing.sizeBytes)
    }

    // Evict entries if needed
    evictIfNeeded(image.sizeBytes)

    // Add new entry
    cache[memoryKey] = image
    currentSize.addAndGet(image.sizeBytes)
  }

  override fun remove(key: CacheKey): Boolean = synchronized(lock) {
    cache.remove(key.memoryKey)?.let { removed ->
      currentSize.addAndGet(-removed.sizeBytes)
      true
    } ?: false
  }

  override fun clear(): Unit = synchronized(lock) {
    cache.clear()
    currentSize.value = 0
  }

  override fun trimToSize(size: Long): Unit = synchronized(lock) {
    while (currentSize.value > size && cache.isNotEmpty()) {
      val iterator = cache.entries.iterator()
      if (iterator.hasNext()) {
        val eldest = iterator.next()
        iterator.remove()
        currentSize.addAndGet(-eldest.value.sizeBytes)
      }
    }
  }

  override fun resize(newMaxSize: Long): Unit = synchronized(lock) {
    _maxSize = newMaxSize
    trimToSize(newMaxSize)
  }

  private fun evictIfNeeded(requiredSpace: Long) {
    while (currentSize.value + requiredSpace > maxSize && cache.isNotEmpty()) {
      val iterator = cache.entries.iterator()
      if (iterator.hasNext()) {
        val eldest = iterator.next()
        iterator.remove()
        currentSize.addAndGet(-eldest.value.sizeBytes)
      }
    }
  }
}
