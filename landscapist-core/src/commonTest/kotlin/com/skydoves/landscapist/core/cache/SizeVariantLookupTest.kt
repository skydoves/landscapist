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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Covers [MemoryCache.getIgnoringSize], the lookup a composable uses before it has been measured
 * and therefore has no target size to ask for.
 */
class SizeVariantLookupTest {

  private val url = "https://example.com/image.jpg"

  private fun image(data: String) = CachedImage(
    data = data,
    dataSource = DataSource.MEMORY,
    sizeBytes = 100L,
  )

  private fun key(
    width: Int? = null,
    height: Int? = null,
    transformations: List<String> = emptyList(),
  ) = CacheKey(url = url, transformationKeys = transformations, width = width, height = height)

  private val otherKey = CacheKey(url = "https://example.com/other.jpg", width = 400, height = 400)

  private fun caches(maxSize: Long = 1000L): List<MemoryCache> =
    listOf(LruMemoryCache(maxSize), TwoTierMemoryCache(maxSize))

  @Test
  fun `baseKey drops the size while memoryKey keeps it`() {
    val sized = key(width = 100, height = 200)

    assertEquals(key().memoryKey, sized.baseKey)
    assertEquals("${sized.baseKey}_100x200", sized.memoryKey)
  }

  @Test
  fun `baseKey is shared across sizes and separated by transformations`() {
    assertEquals(key().baseKey, key(width = 10, height = 10).baseKey)
    assertNotEquals(key().baseKey, key(transformations = listOf("blur")).baseKey)
  }

  @Test
  fun `a sized entry is found without knowing the size`() {
    for (cache in caches()) {
      cache[key(width = 400, height = 400)] = image("sized")

      assertNull(cache[key()], "exact lookup must not match a differently sized entry")
      assertEquals("sized", cache.getIgnoringSize(key())?.data)
    }
  }

  @Test
  fun `the most recently cached size wins`() {
    for (cache in caches()) {
      cache[key(width = 100, height = 100)] = image("small")
      cache[key(width = 800, height = 800)] = image("large")

      assertEquals("large", cache.getIgnoringSize(key())?.data)
    }
  }

  @Test
  fun `transformations are not mixed together`() {
    for (cache in caches()) {
      cache[key(width = 400, height = 400, transformations = listOf("blur"))] = image("blurred")

      assertNull(cache.getIgnoringSize(key())?.data)
      assertEquals(
        "blurred",
        cache.getIgnoringSize(key(transformations = listOf("blur")))?.data,
      )
    }
  }

  @Test
  fun `a removed entry is no longer found`() {
    for (cache in caches()) {
      cache[key(width = 400, height = 400)] = image("sized")
      cache.remove(key(width = 400, height = 400))

      assertNull(cache.getIgnoringSize(key()))
    }
  }

  @Test
  fun `a peek refreshes access order so the entry is not evicted next`() {
    for (cache in caches(maxSize = 250L)) {
      cache[key(width = 400, height = 400)] = image("peeked")
      cache[CacheKey(url = "https://example.com/other.jpg")] = image("other")

      // Without the refresh, "peeked" is the eldest and goes first.
      cache.getIgnoringSize(key())
      cache[CacheKey(url = "https://example.com/third.jpg")] = image("third")

      assertEquals("peeked", cache.getIgnoringSize(key())?.data)
    }
  }

  @Test
  fun `a peek finds an entry the weak tier is holding and promotes it`() {
    val cache = TwoTierMemoryCache(_maxSize = 150L)
    cache[key(width = 400, height = 400)] = image("demoted")
    cache[otherKey] = image("other")
    assertEquals(1, cache.strongCacheCount, "the first entry should have been demoted")

    assertEquals("demoted", cache.getIgnoringSize(key())?.data)

    // Back in the strong tier, and counted once: promoting swaps it with whatever displaced it.
    assertEquals(1, cache.strongCacheCount)
    assertEquals(100L, cache.size)
  }

  @Test
  fun `a cleared cache finds nothing`() {
    for (cache in caches()) {
      cache[key(width = 400, height = 400)] = image("sized")
      cache.clear()

      assertNull(cache.getIgnoringSize(key()))
    }
  }

  @Test
  fun `an entry evicted out of the strong cache is no longer found`() {
    // Weak references are off, so eviction drops the entry entirely rather than demoting it.
    val cache = TwoTierMemoryCache(_maxSize = 150L, weakReferencesEnabled = false)
    cache[key(width = 400, height = 400)] = image("first")
    cache[otherKey] = image("second")

    assertNull(cache.getIgnoringSize(key()))
  }

  @Test
  fun `an evicted entry is no longer found in the lru cache`() {
    val cache = LruMemoryCache(150L)
    cache[key(width = 400, height = 400)] = image("first")
    cache[otherKey] = image("second")

    assertNull(cache.getIgnoringSize(key()))
  }
}
