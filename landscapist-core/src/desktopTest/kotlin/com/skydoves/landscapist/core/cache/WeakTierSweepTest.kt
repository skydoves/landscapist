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
 * The weak tier has to forget an entry once its image is gone.
 *
 * Nothing pruned it before: an entry evicted from the strong cache kept its key and its index entry
 * for the life of the process, so an application that had loaded ten thousand distinct images
 * carried ten thousand dead references and walked all of them on every young collection.
 *
 * This is a JVM test rather than a common one, because only here can the referent actually be
 * cleared. The common test alongside it holds every image on purpose, and proves the other half:
 * that a sweep never drops an entry that is still reachable.
 */
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
      // The entry is unreachable the moment the next one is written, because nothing here keeps it.
      cache[key(index)] = CachedImage(ByteArray(64), DataSource.MEMORY, 100, 8, 8)
    }

    // A collection has to have happened for the referents to clear; ask for it and give it a chance.
    var swept = cache.weakCacheCount
    repeat(20) {
      if (swept <= 8) return@repeat
      System.gc()
      Thread.sleep(20)
      cache.cleanupWeakReferences()
      swept = cache.weakCacheCount
    }

    // Nothing holds any of these entries, so a tier that sweeps ends up near empty. Left unswept
    // it keeps one dead reference per write, which is what the leak was.
    assertTrue(
      swept <= 8,
      "the weak tier still holds $swept entries after 400 writes that nothing keeps alive",
    )
  }

  @Test
  fun `a sweep keeps every entry whose image is still held`() {
    val cache = TwoTierMemoryCache(200, weakReferencesEnabled = true)
    // The weak reference is to the entry, not to the pixels inside it, so it is the entries that
    // have to be held. Almost all of them are evicted into the weak tier by the writes that follow.
    val held = (0 until 200).map { CachedImage(ByteArray(64), DataSource.MEMORY, 100, 8, 8) }
    held.forEachIndexed { index, image -> cache[key(index)] = image }

    System.gc()
    cache.cleanupWeakReferences()

    val found = (0 until 200).count { cache[key(it)] != null }
    assertEquals(200, found, "the sweep dropped an entry that is still reachable")
    // Reads the list after the assertion, so the collector cannot make this pass by clearing early.
    assertEquals(200, held.size)
  }
}
