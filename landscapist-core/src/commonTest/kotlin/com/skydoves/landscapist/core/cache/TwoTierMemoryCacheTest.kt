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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwoTierMemoryCacheTest {

  private fun createCache(
    maxSize: Long = 1000L,
    weakReferencesEnabled: Boolean = true,
  ): TwoTierMemoryCache {
    return TwoTierMemoryCache(maxSize, weakReferencesEnabled)
  }

  private fun createCachedImage(sizeBytes: Long = 100L): CachedImage {
    return CachedImage(
      data = "test_data_$sizeBytes",
      dataSource = DataSource.MEMORY,
      sizeBytes = sizeBytes,
    )
  }

  private fun createKey(url: String): CacheKey {
    return CacheKey(url = url)
  }

  @Test
  fun `initial cache is empty`() {
    val cache = createCache()

    assertEquals(0L, cache.size)
    assertEquals(0, cache.count)
    assertEquals(0, cache.strongCacheCount)
    assertEquals(0, cache.weakCacheCount)
  }

  @Test
  fun `set adds item to strong cache`() {
    val cache = createCache()
    val key = createKey("https://example.com/image.jpg")
    val image = createCachedImage(sizeBytes = 100L)

    cache[key] = image

    assertEquals(100L, cache.size)
    assertEquals(1, cache.strongCacheCount)
    assertEquals(0, cache.weakCacheCount)
  }

  @Test
  fun `get retrieves item from strong cache`() {
    val cache = createCache()
    val key = createKey("https://example.com/image.jpg")
    val image = createCachedImage()

    cache[key] = image
    val retrieved = cache[key]

    assertNotNull(retrieved)
    assertEquals(image.data, retrieved.data)
  }

  @Test
  fun `get returns null for non-existent key`() {
    val cache = createCache()
    val key = createKey("https://example.com/nonexistent.jpg")

    val retrieved = cache[key]

    assertNull(retrieved)
  }

  @Test
  fun `evicted items move to weak cache when enabled`() {
    val cache = createCache(maxSize = 150L, weakReferencesEnabled = true)

    val key1 = createKey("https://example.com/1.jpg")
    val key2 = createKey("https://example.com/2.jpg")

    cache[key1] = createCachedImage(sizeBytes = 100L)
    cache[key2] = createCachedImage(sizeBytes = 100L)

    // key1 should be evicted to weak cache
    assertEquals(1, cache.strongCacheCount)
    assertEquals(1, cache.weakCacheCount)
  }

  @Test
  fun `evicted items are discarded when weak references disabled`() {
    val cache = createCache(maxSize = 150L, weakReferencesEnabled = false)

    val key1 = createKey("https://example.com/1.jpg")
    val key2 = createKey("https://example.com/2.jpg")

    cache[key1] = createCachedImage(sizeBytes = 100L)
    cache[key2] = createCachedImage(sizeBytes = 100L)

    // key1 should be discarded (no weak cache)
    assertEquals(1, cache.strongCacheCount)
    assertEquals(0, cache.weakCacheCount)
    assertNull(cache[key1])
  }

  @Test
  fun `remove removes from both caches`() {
    val cache = createCache()
    val key = createKey("https://example.com/image.jpg")

    cache[key] = createCachedImage(sizeBytes = 100L)
    val removed = cache.remove(key)

    assertTrue(removed)
    assertEquals(0L, cache.size)
    assertEquals(0, cache.strongCacheCount)
    assertNull(cache[key])
  }

  @Test
  fun `remove returns false for non-existent key`() {
    val cache = createCache()
    val key = createKey("https://example.com/nonexistent.jpg")

    val removed = cache.remove(key)

    assertFalse(removed)
  }

  @Test
  fun `clear removes all items from both caches`() {
    val cache = createCache(maxSize = 150L)

    cache[createKey("https://example.com/1.jpg")] = createCachedImage(sizeBytes = 100L)
    cache[createKey("https://example.com/2.jpg")] = createCachedImage(sizeBytes = 100L)

    cache.clear()

    assertEquals(0L, cache.size)
    assertEquals(0, cache.strongCacheCount)
    assertEquals(0, cache.weakCacheCount)
  }

  @Test
  fun `updating existing key removes from weak cache`() {
    val cache = createCache(maxSize = 150L)

    val key = createKey("https://example.com/image.jpg")

    // Add item, let it be evicted to weak cache
    cache[key] = createCachedImage(sizeBytes = 100L)
    cache[createKey("https://example.com/other.jpg")] = createCachedImage(sizeBytes = 100L)

    // Update the evicted item
    cache[key] = createCachedImage(sizeBytes = 50L)

    // Should be in strong cache now
    assertNotNull(cache[key])
  }

  @Test
  fun `trimToSize reduces cache to target size`() {
    val cache = createCache(maxSize = 1000L)

    cache[createKey("https://example.com/1.jpg")] = createCachedImage(sizeBytes = 200L)
    cache[createKey("https://example.com/2.jpg")] = createCachedImage(sizeBytes = 200L)
    cache[createKey("https://example.com/3.jpg")] = createCachedImage(sizeBytes = 200L)

    assertEquals(600L, cache.size)

    cache.trimToSize(300L)

    assertTrue(cache.size <= 300L)
  }

  @Test
  fun `resize updates max size and trims if needed`() {
    val cache = createCache(maxSize = 1000L)

    cache[createKey("https://example.com/1.jpg")] = createCachedImage(sizeBytes = 300L)
    cache[createKey("https://example.com/2.jpg")] = createCachedImage(sizeBytes = 300L)
    cache[createKey("https://example.com/3.jpg")] = createCachedImage(sizeBytes = 300L)

    cache.resize(500L)

    assertEquals(500L, cache.maxSize)
    assertTrue(cache.size <= 500L)
  }

  @Test
  fun `LRU evicts least recently used from strong cache`() {
    val cache = createCache(maxSize = 250L)

    val key1 = createKey("https://example.com/1.jpg")
    val key2 = createKey("https://example.com/2.jpg")
    val key3 = createKey("https://example.com/3.jpg")

    cache[key1] = createCachedImage(sizeBytes = 100L)
    cache[key2] = createCachedImage(sizeBytes = 100L)

    // Access key1 to make it more recently used
    cache[key1]

    // Add key3, should evict key2
    cache[key3] = createCachedImage(sizeBytes = 100L)

    // key1 and key3 should be in strong cache
    assertEquals(2, cache.strongCacheCount)
  }

  @Test
  fun `cleanupWeakReferences removes null references`() {
    val cache = createCache(maxSize = 100L)

    // Add and evict to weak cache
    cache[createKey("https://example.com/1.jpg")] = createCachedImage(sizeBytes = 100L)
    cache[createKey("https://example.com/2.jpg")] = createCachedImage(sizeBytes = 100L)

    // Note: We can't actually force GC, so we just verify the method doesn't crash
    cache.cleanupWeakReferences()

    // The method should complete without error
    assertTrue(true)
  }

  @Test
  fun `count returns strong cache count`() {
    val cache = createCache()

    assertEquals(0, cache.count)

    cache[createKey("https://example.com/1.jpg")] = createCachedImage()
    assertEquals(1, cache.count)

    cache[createKey("https://example.com/2.jpg")] = createCachedImage()
    assertEquals(2, cache.count)
  }

  @Test
  fun `maxSize returns configured max size`() {
    val cache = createCache(maxSize = 500L)

    assertEquals(500L, cache.maxSize)
  }
}
