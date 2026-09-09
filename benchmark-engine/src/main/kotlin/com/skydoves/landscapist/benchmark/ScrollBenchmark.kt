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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.crossfade
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.cache.TwoTierMemoryCache
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage

/**
 * A real scrolling list: one scene, node reuse, and a working set larger than the cache. A fresh
 * scene per iteration, as the other Compose rows use, denies Coil the painter reuse
 * `ContentPainterElement` exists for and never evicts anything.
 */
internal fun scrollComparison() {
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapist = newLandscapist(landscapistCounter)
  val coil = newCoil(coilCounter)
  // Its own counter, because it is a second loader with a second cache.
  val fadingCounter = FetchCounter()
  val fadingCoil = newCoil(fadingCounter) { crossfade(300) }
  val models = List(LIST_ITEMS) { "https://example.com/feed-$it.jpg" }

  val variants = listOf<Pair<String, @Composable (LazyListState) -> Unit>>(
    "empty list" to { state -> EmptyLazyList(models, state) },
    "landscapist" to { state -> LandscapistLazyList(landscapist, models, state) },
    "coil" to { state -> CoilLazyList(coil, models, state) },
    "coil painter sized" to { state -> CoilPainterLazyList(coil, models, state) },
    "landscapist crossfade" to { state ->
      LandscapistCrossfadeLazyList(landscapist, models, state)
    },
    "coil crossfade" to { state -> CoilLazyList(fadingCoil, models, state) },
  )

  // Warm every variant before any is measured, floor included, so none pays for warming Compose.
  for ((_, content) in variants) {
    warmScroll(content, frames = 120)
  }
  settle()

  println("share of a mid scroll frame covered by image pixels, in a LazyColumn")
  val painted = variants.associate { (name, content) -> name to scrolledPixelFraction(content) }
  for ((name, share) in painted) {
    println("  ${name.padEnd(22)}${share.asPercent()}")
  }
  // The list is taller than the viewport and the items are full width, so a warm frame is
  // essentially all image. Much under that means a variant is drawing empty boxes.
  val blank = painted.filterKeys { it != "empty list" }.filterValues { it <= 0.85 }.keys
  check(blank.isEmpty()) { "$blank drew nothing mid scroll, so the rows below are not comparable" }
  // A scroll is supposed to fetch, unlike the first frame rows. Checked rather than printed and
  // moved on from: the two plain loaders warm two variants each over the same frames, so a gap
  // between them is a cache not reusing what it has, and every row below would then be comparing
  // that rather than a Compose layer.
  val landscapistFetches = landscapistCounter.count.get()
  val coilFetches = coilCounter.count.get()
  val fadingFetches = fadingCounter.count.get()
  println(
    "  fetches so far, per loader: landscapist $landscapistFetches, coil $coilFetches, " +
      "coil crossfade $fadingFetches",
  )
  check(fadingFetches > 0) { "the crossfading loader never fetched, so its rows are empty frames" }
  val most = maxOf(landscapistFetches, coilFetches)
  val least = minOf(landscapistFetches, coilFetches)
  check(least > 0 && most <= least * FETCH_TOLERANCE) {
    "landscapist fetched $landscapistFetches times and coil $coilFetches over the same warm up, " +
      "so the rows below are not comparing the same work"
  }
  println()

  println(
    "allocation per frame while scrolling $LIST_ITEMS items at ${SCROLL_STEP.toInt()} px per frame",
  )
  // Interleaved, so every variant is at the same scroll position on the same round. A list
  // allocates differently at different offsets.
  val samples = interleavedScrollAllocation(variants.map { it.second }, SCROLL_FRAMES)
  // Median and mean, because a scroll answers them differently: the median frame composes
  // nothing, and the mean is the one a user feels, since composing frames are dropping frames.
  val floorMedian = samples[0].median()
  val floorMean = samples[0].mean()
  println(
    "  ${"empty list".padEnd(22)}median ${floorMedian.formatBytes().padEnd(12)}" +
      "mean ${floorMean.formatBytes()}  (the floor, subtracted below)",
  )
  for (index in 1 until variants.size) {
    val perFrame = samples[index].median().above(floorMedian)
    val meanFrame = samples[index].mean().above(floorMean)
    Metrics.record("scroll.frame.${variants[index].first.metricKey()}.bytes", meanFrame)
    println(
      "  ${variants[index].first.padEnd(22)}median ${perFrame.formatBytes().padEnd(12)}" +
        "mean ${meanFrame.formatBytes()}",
    )
  }
  println()

  println("time added to a scrolled frame, over the same list with no images")
  for (index in 1 until variants.size) {
    val deltas = LongArray(SCROLL_FRAMES / 2)
    val empty = scrollTimer(variants[0].second)
    val variant = scrollTimer(variants[index].second)
    try {
      repeat(120) {
        empty.frame()
        variant.frame()
      }
      repeat(deltas.size) { deltas[it] = variant.timedFrame() - empty.timedFrame() }
    } finally {
      empty.close()
      variant.close()
    }
    println("  ${variants[index].first.padEnd(22)}${deltas.median().formatNanos()}")
  }
  println()

  thrashComparison()
}

/**
 * One fling down the list and back up, with a cache sized for a tenth of it, which is the only
 * row where an eviction policy is visible. A collection is forced at the turn: both libraries
 * keep evicted entries behind weak references, so without it the row reports collector timing.
 */
private fun thrashComparison() {
  val cacheBytes = LIST_ITEMS.toLong() * bytesPerItem() / 10
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapistCache = TwoTierMemoryCache(cacheBytes, weakReferencesEnabled = true)
  val landscapist = newLandscapist(landscapistCounter, memoryCache = landscapistCache)
  val coil = newCoil(coilCounter, memoryCacheBytes = cacheBytes)
  val models = List(LIST_ITEMS) { "https://example.com/thrash-$it.jpg" }

  scrollThereAndBack("landscapist") { state -> LandscapistLazyList(landscapist, models, state) }
  scrollThereAndBack("coil") { state -> CoilLazyList(coil, models, state) }

  println(
    "one fling down $LIST_ITEMS items and back up, with a cache sized for " +
      "${LIST_ITEMS / 10} of them (${cacheBytes.formatBytes()})",
  )
  compare(
    label = "decodes for the round trip",
    landscapist = landscapistCounter.count.get().toLong(),
    coil = coilCounter.count.get().toLong(),
  ) { "$it" }
  val coilCache = coil.memoryCache!!
  landscapistCache.cleanupWeakReferences()
  // Entries that still hold an image, not keys: a key whose referent has been collected outlives
  // the image on both sides, and the sweep above has already dropped landscapist's.
  val landscapistKeys = (landscapistCache.strongCacheCount + landscapistCache.weakCacheCount)
    .toLong()
  val coilKeys = coilCache.keys.count { coilCache[it] != null }.toLong()
  compare("entries still held, both tiers", landscapistKeys, coilKeys) { "$it" }
  compare("bytes the cache admits to", landscapistCache.size, coilCache.size) { it.formatBytes() }
  compare("bytes still reachable", landscapistKeys * bytesPerItem(), coilKeys * bytesPerItem()) {
    it.formatBytes()
  }
  println(
    "    The decode counts are equal because the collection above is forced on both. The rows " +
      "under it are not equal, and this harness does not establish why: Coil's evicted entries " +
      "still hold their image after a collection and landscapist's do not, so Coil answers the " +
      "next request for one without decoding and keeps ten times the bytes to do it. Which of " +
      "those a user wants depends on whether their device is short of memory or of time.",
  )
  println()
}

private fun scrollThereAndBack(label: String, content: @Composable (LazyListState) -> Unit) {
  val state = LazyListState(0, 0)
  val scene = benchmarkScene(VIEWPORT_WIDTH, VIEWPORT_HEIGHT) { content(state) }
  try {
    scene.render(0L).close()
    val frames = (LIST_ITEMS * ITEM_HEIGHT - VIEWPORT_HEIGHT) / FLING_STEP.toInt() + 1
    repeat(frames) { advance(scene, state, FLING_STEP, it) }
    // Before the way back, so both face the same question: what the cache still holds, rather
    // than what the collector has not got round to.
    System.gc()
    Thread.sleep(GC_SETTLE_MS)
    System.gc()
    val bottom = state.firstVisibleItemIndex
    repeat(frames) { advance(scene, state, -FLING_STEP, frames + it) }
    val top = state.firstVisibleItemIndex
    // A scroll that did not move would report one fetch per item and look like a perfect cache.
    check(bottom > LIST_ITEMS - 10 && top == 0) {
      "$label did not scroll: reached item $bottom and came back to $top"
    }
  } finally {
    scene.close()
  }
}

/** Bytes one decoded list item occupies, which is what both caches are budgeting in. */
private fun bytesPerItem(): Long = VIEWPORT_WIDTH.toLong() * ITEM_HEIGHT * 4

private const val LIST_ITEMS = 240
private const val VIEWPORT_WIDTH = 360
private const val VIEWPORT_HEIGHT = 720
private const val ITEM_HEIGHT = 180
private const val SCROLL_STEP = 16f
private const val FLING_STEP = 120f
private const val SCROLL_FRAMES = 400

/** How far apart the two loaders' fetch counts may drift before the rows stop being comparable. */
private const val FETCH_TOLERANCE = 2

/** Long enough for the reference processor to clear what the collection above made unreachable. */
private const val GC_SETTLE_MS = 50L

/** Scrolls one list without measuring, so the classes it needs are loaded and compiled. */
private fun warmScroll(content: @Composable (LazyListState) -> Unit, frames: Int) {
  val state = LazyListState(0, 0)
  val scene = benchmarkScene(VIEWPORT_WIDTH, VIEWPORT_HEIGHT) { content(state) }
  try {
    // The first render is what gives the list a layout, and dispatchRawDelta needs one.
    scene.render(0L).close()
    repeat(frames) { advance(scene, state, SCROLL_STEP, it) }
  } finally {
    scene.close()
  }
}

/** Scrolls every variant a frame at a time, round by round, reporting what each frame cost. */
private fun interleavedScrollAllocation(
  contents: List<@Composable (LazyListState) -> Unit>,
  frames: Int,
): List<LongArray> {
  val runs = contents.map { scrollTimer(it) }
  try {
    repeat(120) { for (run in runs) run.frame() }
    settle()
    val samples = List(runs.size) { LongArray(frames) }
    repeat(frames) { round ->
      for ((index, run) in runs.withIndex()) {
        samples[index][round] = allocatedBytes { run.frame() }
      }
    }
    return samples
  } finally {
    runs.forEach { it.close() }
  }
}

/** A scene held open so lists can be compared frame by frame rather than median to median. */
private class ScrollTimer(
  private val scene: ImageComposeScene,
  private val state: LazyListState,
) {
  private var frameIndex = 0

  fun frame() = advance(scene, state, SCROLL_STEP, frameIndex++)

  fun timedFrame(): Long {
    val start = System.nanoTime()
    frame()
    return System.nanoTime() - start
  }

  fun close() = scene.close()
}

private fun scrollTimer(content: @Composable (LazyListState) -> Unit): ScrollTimer {
  val state = LazyListState(0, 0)
  val scene = benchmarkScene(VIEWPORT_WIDTH, VIEWPORT_HEIGHT) { content(state) }
  scene.render(0L).close()
  return ScrollTimer(scene, state)
}

/** Scrolls past the first screenful, then asks what the frame actually contains. */
private fun scrolledPixelFraction(content: @Composable (LazyListState) -> Unit): Double {
  val state = LazyListState(0, 0)
  val scene = benchmarkScene(VIEWPORT_WIDTH, VIEWPORT_HEIGHT) { content(state) }
  try {
    scene.render(0L).close()
    repeat(120) { advance(scene, state, SCROLL_STEP, it) }
    return scene.imagePixelFraction(120L * FRAME_NANOS)
  } finally {
    scene.close()
  }
}

private const val FRAME_NANOS = 16_666_667L

private fun advance(scene: ImageComposeScene, state: LazyListState, step: Float, frame: Int) {
  state.dispatchRawDelta(step)
  Snapshot.sendApplyNotifications()
  scene.render(frame * FRAME_NANOS).close()
}

@Composable
private fun EmptyLazyList(models: List<String>, state: LazyListState) {
  LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
    items(models, key = { it }) {
      Box(Modifier.fillMaxWidth().height(ITEM_HEIGHT.dp))
    }
  }
}

@Composable
private fun LandscapistLazyList(
  landscapist: Landscapist,
  models: List<String>,
  state: LazyListState,
) {
  LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
    items(models, key = { it }) { model ->
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT.dp),
      )
    }
  }
}

@Composable
private fun LandscapistCrossfadeLazyList(
  landscapist: Landscapist,
  models: List<String>,
  state: LazyListState,
) {
  val component = rememberImageComponent { +CrossfadePlugin(duration = 300) }
  LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
    items(models, key = { it }) { model ->
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        component = component,
        modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT.dp),
      )
    }
  }
}

@Composable
private fun CoilLazyList(imageLoader: ImageLoader, models: List<String>, state: LazyListState) {
  LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
    items(models, key = { it }) { model ->
      AsyncImage(
        model = model,
        contentDescription = null,
        imageLoader = imageLoader,
        modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT.dp),
      )
    }
  }
}

/**
 * The painter form Coil points a performance minded caller at, sized on the request. Without a
 * size it falls back to `SizeResolver.ORIGINAL`, which is a different comparison.
 */
@Composable
private fun CoilPainterLazyList(
  imageLoader: ImageLoader,
  models: List<String>,
  state: LazyListState,
) {
  LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
    items(models, key = { it }) { model ->
      val request = remember(model) { coilRequest(model, VIEWPORT_WIDTH, ITEM_HEIGHT) }
      Image(
        painter = rememberAsyncImagePainter(model = request, imageLoader = imageLoader),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT.dp),
      )
    }
  }
}
