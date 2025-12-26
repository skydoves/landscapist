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

class LruMemoryCacheTest {

  private fun createCache(maxSize: Long = 1000L): LruMemoryCache {
    return LruMemoryCache(maxSize)
  }

  private fun createCachedImage(sizeBytes: Long = 100L): CachedImage {
    return CachedImage(
      data = "test_data",
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
  }

  @Test
  fun `maxSize returns configured max size`() {
    val cache = createCache(maxSize = 500L)

    assertEquals(500L, cache.maxSize)
  }

  @Test
  fun `set adds item to cache`() {
    val cache = createCache()
    val key = createKey("https://example.com/image.jpg")
    val image = createCachedImage(sizeBytes = 100L)

    cache[key] = image

    assertEquals(100L, cache.size)
    assertEquals(1, cache.count)
  }

  @Test
  fun `get retrieves cached item`() {
    val cache = createCache()
    val key = createKey("https://example.com/image.jpg")
    val image = createCachedImage()

    cache[key] = image
    val retrieved = cache[key]

    assertNotNull(retrieved)
    assertEquals(image.data, retrieved.data)
    assertEquals(image.dataSource, retrieved.dataSource)
    assertEquals(image.sizeBytes, retrieved.sizeBytes)
  }

  @Test
  fun `get returns null for non-existent key`() {
    val cache = createCache()
    val key = createKey("https://example.com/nonexistent.jpg")

    val retrieved = cache[key]

    assertNull(retrieved)
  }

  @Test
  fun `remove deletes item from cache`() {
    val cache = createCache()
    val key = createKey("https://example.com/image.jpg")
    val image = createCachedImage(sizeBytes = 100L)

    cache[key] = image
    val removed = cache.remove(key)

    assertTrue(removed)
    assertEquals(0L, cache.size)
    assertEquals(0, cache.count)
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
  fun `clear removes all items`() {
    val cache = createCache()

    cache[createKey("https://example.com/1.jpg")] = createCachedImage()
    cache[createKey("https://example.com/2.jpg")] = createCachedImage()
    cache[createKey("https://example.com/3.jpg")] = createCachedImage()

    assertEquals(3, cache.count)

    cache.clear()

    assertEquals(0L, cache.size)
    assertEquals(0, cache.count)
  }

  @Test
  fun `evicts oldest item when exceeding max size`() {
    val cache = createCache(maxSize = 250L)

    val key1 = createKey("https://example.com/1.jpg")
    val key2 = createKey("https://example.com/2.jpg")
    val key3 = createKey("https://example.com/3.jpg")

    cache[key1] = createCachedImage(sizeBytes = 100L)
    cache[key2] = createCachedImage(sizeBytes = 100L)
    cache[key3] = createCachedImage(sizeBytes = 100L)

    // key1 should be evicted because it was added first
    assertNull(cache[key1])
    assertNotNull(cache[key2])
    assertNotNull(cache[key3])
    assertTrue(cache.size <= 250L)
  }

  @Test
  fun `LRU evicts least recently used item`() {
    val cache = createCache(maxSize = 250L)

    val key1 = createKey("https://example.com/1.jpg")
    val key2 = createKey("https://example.com/2.jpg")
    val key3 = createKey("https://example.com/3.jpg")

    cache[key1] = createCachedImage(sizeBytes = 100L)
    cache[key2] = createCachedImage(sizeBytes = 100L)

    // Access key1 to make it more recently used
    cache[key1]

    // Add key3, which should evict key2 (least recently used)
    cache[key3] = createCachedImage(sizeBytes = 100L)

    assertNotNull(cache[key1])
    assertNull(cache[key2])
    assertNotNull(cache[key3])
  }

  @Test
  fun `updating existing key replaces value`() {
    val cache = createCache()
    val key = createKey("https://example.com/image.jpg")

    val image1 = CachedImage(data = "data1", dataSource = DataSource.DISK, sizeBytes = 100L)
    val image2 = CachedImage(data = "data2", dataSource = DataSource.NETWORK, sizeBytes = 150L)

    cache[key] = image1
    cache[key] = image2

    val retrieved = cache[key]
    assertNotNull(retrieved)
    assertEquals("data2", retrieved.data)
    assertEquals(DataSource.NETWORK, retrieved.dataSource)
    assertEquals(150L, cache.size)
    assertEquals(1, cache.count)
  }

  @Test
  fun `trimToSize reduces cache to target size`() {
    val cache = createCache(maxSize = 1000L)

    cache[createKey("https://example.com/1.jpg")] = createCachedImage(sizeBytes = 200L)
    cache[createKey("https://example.com/2.jpg")] = createCachedImage(sizeBytes = 200L)
    cache[createKey("https://example.com/3.jpg")] = createCachedImage(sizeBytes = 200L)
    cache[createKey("https://example.com/4.jpg")] = createCachedImage(sizeBytes = 200L)
    cache[createKey("https://example.com/5.jpg")] = createCachedImage(sizeBytes = 200L)

    assertEquals(1000L, cache.size)

    cache.trimToSize(400L)

    assertTrue(cache.size <= 400L)
  }

  @Test
  fun `resize updates max size and trims if needed`() {
    val cache = createCache(maxSize = 1000L)

    cache[createKey("https://example.com/1.jpg")] = createCachedImage(sizeBytes = 300L)
    cache[createKey("https://example.com/2.jpg")] = createCachedImage(sizeBytes = 300L)
    cache[createKey("https://example.com/3.jpg")] = createCachedImage(sizeBytes = 300L)

    assertEquals(900L, cache.size)

    cache.resize(500L)

    assertEquals(500L, cache.maxSize)
    assertTrue(cache.size <= 500L)
  }

  @Test
  fun `count returns correct number of entries`() {
    val cache = createCache()

    assertEquals(0, cache.count)

    cache[createKey("https://example.com/1.jpg")] = createCachedImage()
    assertEquals(1, cache.count)

    cache[createKey("https://example.com/2.jpg")] = createCachedImage()
    assertEquals(2, cache.count)

    cache.remove(createKey("https://example.com/1.jpg"))
    assertEquals(1, cache.count)
  }
}
