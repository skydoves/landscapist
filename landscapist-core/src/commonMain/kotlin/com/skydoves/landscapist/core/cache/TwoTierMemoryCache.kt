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
  private val variantIndex = SizeVariantIndex()
  private val currentSize = atomic(0L)

  // The size the weak tier has to reach before the dead references in it are swept out. See
  // [sweepWeakReferences].
  private var weakSweepThreshold = MIN_WEAK_SWEEP

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
      variantIndex.remove(memoryKey)
    }

    null
  }

  override fun getIgnoringSize(key: CacheKey): CachedImage? = synchronized(lock) {
    val memoryKey = liveVariantOf(key.baseKey) ?: return@synchronized null
    val image = strongCache[memoryKey]
      ?: weakCache[memoryKey]?.get()
      ?: return@synchronized null
    // An entry reached this way is about to be drawn, so give it what get() gives it: a refreshed
    // position in the strong cache, and a promotion out of the weak tier.
    touch(memoryKey, image)
    image
  }

  override fun getMatching(
    key: CacheKey,
    isAcceptable: (CacheKey, CachedImage) -> Boolean,
  ): CachedImage? = synchronized(lock) {
    val exact = key.memoryKey
    // Taken out and put back rather than read and then touched: the entry is going to be moved to
    // the most recent position either way, and this is the path every hit takes.
    strongCache.remove(exact)?.let { image ->
      strongCache[exact] = image
      return@synchronized image
    }
    weakCache[exact]?.get()?.let { image ->
      touch(exact, image)
      return@synchronized image
    }
    // touch() promotes out of the weak tier and evicts other live entries to make room for what it
    // promotes, so it is only ever applied to the variant that is actually returned. Offering every
    // variant rather than the newest one also stops a thumbnail from hiding the full sized entry.
    var collected: MutableList<String>? = null
    var found: CachedImage? = null
    for (variant in variantIndex.variantsOf(key.baseKey)) {
      val memoryKey = variant.memoryKey
      val image = strongCache[memoryKey] ?: weakCache[memoryKey]?.get()
      if (image == null) {
        (collected ?: mutableListOf<String>().also { collected = it }).add(memoryKey)
        continue
      }
      if (!isAcceptable(variant, image)) continue
      touch(memoryKey, image)
      found = image
      break
    }
    collected?.forEach { memoryKey ->
      weakCache.remove(memoryKey)
      variantIndex.remove(memoryKey)
    }
    found
  }

  /**
   * The most recently cached key under [baseKey] that still holds an image.
   *
   * Keys whose weak referent has been collected are dropped on the way. Nothing else prunes them:
   * eviction to the weak tier deliberately keeps the key, and [get] only ever sees one key.
   */
  private fun liveVariantOf(baseKey: String): String? {
    var live: String? = null
    var collected: MutableList<String>? = null
    for (variant in variantIndex.variantsOf(baseKey)) {
      val memoryKey = variant.memoryKey
      if (strongCache.containsKey(memoryKey) || weakCache[memoryKey]?.get() != null) {
        live = memoryKey
        break
      }
      (collected ?: mutableListOf<String>().also { collected = it }).add(memoryKey)
    }
    collected?.forEach { memoryKey ->
      weakCache.remove(memoryKey)
      variantIndex.remove(memoryKey)
    }
    return live
  }

  /** Moves [image] to the most recent position of the strong cache, promoting it if it was weak. */
  private fun touch(memoryKey: String, image: CachedImage) {
    if (strongCache.remove(memoryKey) == null) {
      weakCache.remove(memoryKey)
      evictIfNeeded(image.sizeBytes)
      currentSize.addAndGet(image.sizeBytes)
    }
    strongCache[memoryKey] = image
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
    variantIndex.add(key)
    currentSize.addAndGet(image.sizeBytes)
  }

  override fun remove(key: CacheKey): Boolean = synchronized(lock) {
    val memoryKey = key.memoryKey

    // Remove from weak cache
    weakCache.remove(memoryKey)
    variantIndex.remove(memoryKey)

    // Remove from strong cache
    strongCache.remove(memoryKey)?.let { removed ->
      currentSize.addAndGet(-removed.sizeBytes)
      true
    } ?: false
  }

  override fun clear(): Unit = synchronized(lock) {
    strongCache.clear()
    weakCache.clear()
    variantIndex.clear()
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
  public fun cleanupWeakReferences(): Unit = synchronized(lock) { sweepWeakReferences() }

  /**
   * Drops the weak entries whose image the collector has already taken.
   *
   * Nothing else does this. An entry demoted to the weak tier keeps its key there for good, so a
   * screen that scrolls past a thousand images leaves a thousand dead references behind, and every
   * young collection has to walk all of them: the cost of demoting one entry grows with every
   * image the process has ever loaded.
   *
   * Sweeping once the tier has doubled since the last sweep is amortised constant per demotion, and
   * leaves the tier no more than twice the size of what is genuinely still reachable. The floor
   * keeps small caches from sweeping at all.
   *
   * Must be called while holding [lock].
   */
  private fun sweepWeakReferences() {
    val entries = weakCache.entries.iterator()
    while (entries.hasNext()) {
      val entry = entries.next()
      if (entry.value.get() == null) {
        entries.remove()
        variantIndex.remove(entry.key)
      }
    }
    weakSweepThreshold = maxOf(MIN_WEAK_SWEEP, weakCache.size * 2)
  }

  private fun evictIfNeeded(requiredSpace: Long) {
    while (currentSize.value + requiredSpace > maxSize && strongCache.isNotEmpty()) {
      evictOldest()
    }
  }

  private fun evictOldest() {
    val eldestKey = strongCache.keys.firstOrNull() ?: return
    val eldestValue = strongCache.remove(eldestKey) ?: return
    currentSize.addAndGet(-eldestValue.sizeBytes)

    // Move to weak cache if enabled. The entry is still reachable, so it stays in the variant
    // index; without the weak layer it is gone for good and the index must forget it.
    if (weakReferencesEnabled) {
      weakCache[eldestKey] = WeakRef(eldestValue)
      if (weakCache.size >= weakSweepThreshold) sweepWeakReferences()
    } else {
      variantIndex.remove(eldestKey)
    }
  }
}

/** The weak tier is left alone below this, so a small cache never pays for a sweep. */
private const val MIN_WEAK_SWEEP = 64
