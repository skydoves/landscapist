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

/** A JVM test rather than a common one, because only here can a weak referent be cleared. */
class WeakTierSweepTest {

  private fun key(index: Int) = CacheKey.create(
    "https://example.com/$index.jpg",
    width = 8,
    height = 8,
  )

  @Test
  fun `an entry whose image has been collected is swept out of the weak tier`() {
    // Small enough that every write evicts the one before it into the weak tier.
    val cache = TwoTierMemoryCache(200, weakReferencesEnabled = true)
    repeat(400) { index ->
      // Nothing keeps the entry, so it is unreachable once the next one is written.
      cache[key(index)] = CachedImage(ByteArray(64), DataSource.MEMORY, 100, 8, 8)
    }

    // The referents only clear once a collection has happened, so ask for one and wait.
    var swept = cache.weakCacheCount
    repeat(20) {
      if (swept <= 8) return@repeat
      System.gc()
      Thread.sleep(20)
      cache.cleanupWeakReferences()
      swept = cache.weakCacheCount
    }

    assertTrue(
      swept <= 8,
      "the weak tier still holds $swept entries after 400 writes that nothing keeps alive",
    )
  }

  @Test
  fun `a sweep keeps every entry whose image is still held`() {
    val cache = TwoTierMemoryCache(200, weakReferencesEnabled = true)
    // The weak reference is to the entry, not the pixels, so the entries are what must be held.
    val held = (0 until 200).map { CachedImage(ByteArray(64), DataSource.MEMORY, 100, 8, 8) }
    held.forEachIndexed { index, image -> cache[key(index)] = image }

    System.gc()
    cache.cleanupWeakReferences()

    val found = (0 until 200).count { cache[key(it)] != null }
    assertEquals(200, found, "the sweep dropped an entry that is still reachable")
    // Reads the list after the assertion, so the collector cannot make this pass by clearing early.
    assertEquals(200, held.size)
  }

  @Test
  fun `a cleared cache goes back to sweeping at its floor`() {
    val cache = TwoTierMemoryCache(200, weakReferencesEnabled = true)
    // Held on purpose, so the tier grows to full size and sets its next sweep point from that.
    val held = (0 until 600).map { CachedImage(ByteArray(64), DataSource.MEMORY, 100, 8, 8) }
    held.forEachIndexed { index, image -> cache[key(index)] = image }
    assertTrue(cache.weakCacheCount > 400, "the tier never grew, it holds ${cache.weakCacheCount}")

    cache.clear()
    assertEquals(0, cache.weakCacheCount, "the clear left entries behind")

    // Now entries nothing keeps: a tier that sweeps at its floor prunes them as it goes.
    repeat(20) { batch ->
      repeat(20) { index ->
        cache[key(10_000 + batch * 20 + index)] =
          CachedImage(ByteArray(64), DataSource.MEMORY, 100, 8, 8)
      }
      System.gc()
      Thread.sleep(10)
    }
    // One more write, so the check that decides to sweep runs after the last collection.
    cache[key(99_999)] = CachedImage(ByteArray(64), DataSource.MEMORY, 100, 8, 8)

    assertTrue(
      cache.weakCacheCount < 200,
      "the tier held ${cache.weakCacheCount} dead references after the clear, so it was still " +
        "waiting for the size it reached before it",
    )
    // Read after the assertion, so the collector cannot clear these early and make it pass.
    assertEquals(600, held.size)
  }
}
