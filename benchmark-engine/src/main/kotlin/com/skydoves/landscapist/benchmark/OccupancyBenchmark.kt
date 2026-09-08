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
package com.skydoves.landscapist.benchmark

import coil3.request.SuccessResult
import com.skydoves.landscapist.core.cache.TwoTierMemoryCache
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * What each cache is still holding when the requests stop.
 *
 * Cumulative allocation says how much garbage a run made. It says nothing about what survives, and
 * survival is the number that decides whether an app is killed on a small device. The two libraries
 * differ here by design and the difference has never been measured: landscapist's memory key
 * carries the target size, so one image asked for at n sizes becomes n entries, while Coil leaves
 * the size out of the key entirely unless a transformation is attached and keeps overwriting one.
 * Coil pays for that with a cache that can hand back an image at the wrong resolution; landscapist
 * pays for it in bytes held.
 */
internal fun occupancyComparison() {
  println("what the caches hold afterwards")
  nearDuplicateSizes()
  mixedSizeApp()
  weakTierDisclosure()
  println()
}

/**
 * One image, fifteen sizes a pixel or two apart, which is a grid on a screen it does not divide.
 *
 * Nothing in this benchmark showed this before. The fetch count row shows a size sequence costing
 * extra decodes; it cannot show that every one of those decodes is still resident afterwards.
 */
private fun nearDuplicateSizes() {
  val sizes = List(15) { 352 + it }
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val cache = TwoTierMemoryCache(HEADROOM, weakReferencesEnabled = true)
  val landscapist = newLandscapist(landscapistCounter, memoryCache = cache)
  val coil = newCoil(coilCounter, memoryCacheBytes = HEADROOM)

  runBlocking {
    for (size in sizes) {
      landscapist.load(landscapistRequest(NEAR_MODEL, size)).first { it is ImageResult.Success }
      coil.execute(coilRequest(NEAR_MODEL, size))
    }
  }

  val coilCache = coil.memoryCache!!
  println("  one image at ${sizes.size} sizes from ${sizes.first()} to ${sizes.last()} px")
  compare("decodes", landscapistCounter.count.get().toLong(), coilCounter.count.get().toLong()) {
    "$it"
  }
  // Coil counts its weak tier in `keys`, so landscapist is counted the same way. Nothing is
  // evicted at this cache size, so both weak tiers are empty and the two are the same number
  // either way, but the shape has to match or the next person to change a cache size is misled.
  compare(
    "entries held",
    (cache.strongCacheCount + cache.weakCacheCount).toLong(),
    coilCache.keys.size.toLong(),
  ) { "$it" }
  compare("bytes held", cache.size, coilCache.size) { it.formatBytes() }
}

private const val NEAR_MODEL = "https://example.com/occupancy-grid.jpg"

/**
 * A cache large enough that nothing here is ever evicted.
 *
 * The question these rows ask is what each library chooses to keep. A cache that starts evicting
 * answers a different question, and answers it in whichever library's favour happens to have hit
 * the ceiling first.
 */
private const val HEADROOM: Long = 1024L * 1024 * 1024

/**
 * Forty images the way one screen of an app actually asks for them.
 *
 * A thumbnail in a list, the same image opened full width, and the same image again in a slightly
 * different grid. This is the shape that makes a size keyed cache expensive, and it is a shape a
 * user produces by tapping through a normal app, not a contrived one.
 */
private fun mixedSizeApp() {
  val models = List(40) { "https://example.com/occupancy-$it.jpg" }
  val sizes = listOf(96, 96, 360, 361, 720)
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val cache = TwoTierMemoryCache(HEADROOM, weakReferencesEnabled = true)
  val landscapist = newLandscapist(landscapistCounter, memoryCache = cache)
  val coil = newCoil(coilCounter, memoryCacheBytes = HEADROOM)

  runBlocking {
    for (model in models) {
      for (size in sizes) {
        landscapist.load(landscapistRequest(model, size)).first { it is ImageResult.Success }
        coil.execute(coilRequest(model, size))
      }
    }
  }

  val coilCache = coil.memoryCache!!
  println(
    "  ${models.size} images, each asked for at $sizes " +
      "(a thumbnail, a list row, a jittered grid, a detail view)",
  )
  compare("decodes", landscapistCounter.count.get().toLong(), coilCounter.count.get().toLong()) {
    "$it"
  }
  // Coil counts its weak tier in `keys`, so landscapist is counted the same way. Nothing is
  // evicted at this cache size, so both weak tiers are empty and the two are the same number
  // either way, but the shape has to match or the next person to change a cache size is misled.
  compare(
    "entries held",
    (cache.strongCacheCount + cache.weakCacheCount).toLong(),
    coilCache.keys.size.toLong(),
  ) { "$it" }
  compare("bytes held", cache.size, coilCache.size) { it.formatBytes() }

  // What a cache holds and what it can serve are different questions, and the second one is where
  // the bytes are bought back. Going back to the thumbnail, both caches answer without decoding.
  // Only one of them answers with a thumbnail.
  val landscapistServed: Int
  val coilServed: Int
  runBlocking {
    val result = landscapist.load(landscapistRequest(models.first(), 96))
      .first { it is ImageResult.Success } as ImageResult.Success
    landscapistServed = result.originalWidth
    coilServed = (coil.execute(coilRequest(models.first(), 96)) as SuccessResult).image.width
  }
  compare("px served back for a 96 px request", landscapistServed.toLong(), coilServed.toLong()) {
    "$it"
  }
  val pixelRatio = (coilServed.toLong() * coilServed) /
    (landscapistServed.toLong() * landscapistServed)
  println(
    "    Coil leaves the size out of the memory key unless a transformation is attached, so it " +
      "keeps one entry per image and answers the thumbnail with the detail view's bitmap: no " +
      "decode, and $pixelRatio times the pixels to sample on every frame it is drawn.",
  )
}

/**
 * The bytes neither cache counts.
 *
 * `TwoTierMemoryCache` bounds only its strong tier: an evicted entry is moved to a weak reference,
 * stays reachable, and `size` stops counting it the moment it moves. Coil does exactly the same
 * thing. `MemoryCache.Builder` has `weakReferencesEnabled`, on by default, and its own
 * documentation says weak references "do not contribute to the current size of the memory cache".
 *
 * So this row corrects a claim rather than making one. Landscapist's own comparison table lists the
 * weak second tier as something it has and Coil does not. Coil has had it since 3.0, with the same
 * accounting hole, and configured at a megabyte either library can be holding ten.
 */
private fun weakTierDisclosure() {
  val budget = 1L * 1024 * 1024
  val perImage = 256L * 256 * 4
  val entries = 40
  val counter = FetchCounter()
  val cache = TwoTierMemoryCache(budget, weakReferencesEnabled = true)
  val landscapist = newLandscapist(counter, memoryCache = cache)
  val coil = newCoil(FetchCounter(), memoryCacheBytes = budget)
  val models = List(entries) { "https://example.com/weak-$it.jpg" }

  runBlocking {
    for (model in models) {
      landscapist.load(landscapistRequest(model, 256)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model, 256))
    }
  }
  cache.cleanupWeakReferences()
  val coilCache = coil.memoryCache!!
  // `keys` on Coil's cache is the union of its strong and weak tiers, so landscapist is counted the
  // same way. Comparing landscapist's two tiers against Coil's one would have been the same mistake
  // in the other direction, and it is the mistake this row was written with.
  val landscapistKeys = (cache.strongCacheCount + cache.weakCacheCount).toLong()
  val coilKeys = coilCache.keys.size.toLong()
  println("  $entries images of ${perImage.formatBytes()} into a ${budget.formatBytes()} cache")
  compare("bytes the cache reports", cache.size, coilCache.size) { it.formatBytes() }
  compare("entries still held", landscapistKeys, coilKeys) { "$it" }
  compare("bytes still reachable", landscapistKeys * perImage, coilKeys * perImage) {
    it.formatBytes()
  }
  println(
    "    Both keep every evicted entry alive behind a weak reference and neither counts it in " +
      "`size`. Whatever either one reports, that is what is in the heap until the collector runs.",
  )
}
