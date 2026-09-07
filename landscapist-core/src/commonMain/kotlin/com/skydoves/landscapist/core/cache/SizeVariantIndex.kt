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

/**
 * Groups the memory keys of one image by [CacheKey.baseKey], so [MemoryCache.getIgnoringSize] can
 * find an already decoded variant without knowing the target size.
 *
 * An image usually has a single variant, so the per image list stays tiny. The index holds keys
 * only, never images, and mirrors whatever the cache itself holds.
 *
 * Not thread safe: callers guard it with the cache's own lock.
 */
internal class SizeVariantIndex {

  private val variantsByBase = mutableMapOf<String, MutableList<String>>()
  private val baseByVariant = mutableMapOf<String, String>()

  /** Records [key], keeping the most recently added variant last. */
  fun add(key: CacheKey) {
    val memoryKey = key.memoryKey
    remove(memoryKey)
    baseByVariant[memoryKey] = key.baseKey
    variantsByBase.getOrPut(key.baseKey) { mutableListOf() }.add(memoryKey)
  }

  /** Forgets [memoryKey]. Called for every eviction and removal so the index cannot outlive it. */
  fun remove(memoryKey: String) {
    val baseKey = baseByVariant.remove(memoryKey) ?: return
    val variants = variantsByBase[baseKey] ?: return
    variants.remove(memoryKey)
    if (variants.isEmpty()) {
      variantsByBase.remove(baseKey)
    }
  }

  /** The memory keys cached for [baseKey], most recently added first. */
  fun variantsOf(baseKey: String): List<String> =
    variantsByBase[baseKey]?.asReversed() ?: emptyList()

  fun clear() {
    variantsByBase.clear()
    baseByVariant.clear()
  }
}
