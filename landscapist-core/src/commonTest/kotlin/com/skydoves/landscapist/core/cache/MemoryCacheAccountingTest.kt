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
import kotlin.test.assertTrue

/**
 * The memory cache charges an entry by the size the decoder reports, so a decoder that reports the
 * source size of a downsampled image overcharges by the square of the sample size and evicts
 * everything else. This pins the arithmetic that made that a 64x error on Android.
 */
class MemoryCacheAccountingTest {

  private fun entryFor(width: Int, height: Int) = CachedImage(
    data = "decoded",
    dataSource = DataSource.DISK,
    sizeBytes = width.toLong() * height.toLong() * 4L,
    originalWidth = width,
    originalHeight = height,
  )

  @Test
  fun `an entry is charged by its decoded size rather than its source size`() {
    // A 4000x3000 source shown at a list thumbnail size, sampled 8x by the decoder.
    val decoded = entryFor(500, 375)
    val ifChargedBySourceSize = entryFor(4000, 3000)

    assertEquals(750_000L, decoded.sizeBytes)
    assertTrue(
      ifChargedBySourceSize.sizeBytes > decoded.sizeBytes * 60,
      "reporting the source size overcharges by the square of the sample size",
    )
  }

  @Test
  fun `a correctly charged cache holds many thumbnails rather than one`() {
    val budget = 64L * 1024 * 1024
    val cache = LruMemoryCache(budget)

    repeat(50) { index ->
      cache[CacheKey(url = "https://example.com/$index.jpg", width = 500, height = 375)] =
        entryFor(500, 375)
    }

    assertEquals(50, cache.count, "a 64 MiB budget should hold 50 thumbnails comfortably")
  }

  @Test
  fun `overcharging evicts everything after a single image`() {
    val budget = 64L * 1024 * 1024
    val cache = LruMemoryCache(budget)

    repeat(5) { index ->
      cache[CacheKey(url = "https://example.com/$index.jpg", width = 4000, height = 3000)] =
        entryFor(4000, 3000)
    }

    // Documents the failure mode: at 46 MiB charged per entry only one survives.
    assertEquals(1, cache.count)
  }
}
