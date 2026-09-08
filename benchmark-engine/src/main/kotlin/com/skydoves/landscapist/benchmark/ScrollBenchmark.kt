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
 * A real scrolling list: one scene, node reuse, and a working set that does not fit in the cache.
 *
 * Every other Compose row in this benchmark builds a fresh scene per iteration and drops twenty
 * images into composition at once. That is a screen appearing, not a list scrolling, and it denies
 * Coil the painter reuse `ContentPainterElement` exists for: a reused node is handed a new model
 * and updates in place instead of composing from nothing. A list also evicts, which is the only
 * place a cache policy can be seen at all. Both matter more than the first frame, because a user
 * spends one frame entering a screen and hundreds scrolling it.
 */
internal fun scrollComparison() {
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapist = newLandscapist(landscapistCounter)
  val coil = newCoil(coilCounter)
  // Its own counter, because it is a second loader with a second cache. Sharing one would report
  // Coil fetching twice as often as landscapist for no reason but how the benchmark is wired.
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

  // Warm every variant before any of them is measured, and warm the floor too, so no variant pays
  // for warming Compose on behalf of the ones that follow it.
  for ((_, content) in variants) {
    warmScroll(content, frames = 120)
  }
  settle()

  println("share of a mid scroll frame covered by image pixels, in a LazyColumn")
  val painted = variants.associate { (name, content) -> name to scrolledPixelFraction(content) }
  for ((name, share) in painted) {
    println("  ${name.padEnd(22)}${share.asPercent()}")
  }
  // The list is taller than the viewport and the items are square and full width, so a frame is
  // essentially all image once the loaders are warm. Anything much under that means a variant is
  // showing empty boxes, and its allocation number is just the cost of drawing nothing.
  val blank = painted.filterKeys { it != "empty list" }.filterValues { it <= 0.85 }.keys
  check(blank.isEmpty()) { "$blank drew nothing mid scroll, so the rows below are not comparable" }
  // Unlike the first frame rows, a scroll is supposed to fetch: the list is longer than anything
  // that was warmed, and items scrolling in are new. Both sides should be fetching the same amount,
  // and a gap here is a cache that is not reusing what it has rather than a Compose layer that is
  // cheaper. The measured window is the same distance for both, so the counts are comparable.
  println(
    "  fetches so far, per loader: landscapist ${landscapistCounter.count.get()}, " +
      "coil ${coilCounter.count.get()}, coil crossfade ${fadingCounter.count.get()}",
  )
  println()

  println(
    "allocation per frame while scrolling $LIST_ITEMS items at ${SCROLL_STEP.toInt()} px per frame",
  )
  // Interleaved, so drift over the run lands on every variant rather than on whichever went last,
  // and so every variant is at the same scroll position on the same round. A list allocates
  // differently at different offsets, and running one variant to completion before starting the
  // next would compare frame 1 of one against frame 401 of another.
  val samples = interleavedScrollAllocation(variants.map { it.second }, SCROLL_FRAMES)
  // Median and mean, because for a scroll they answer different questions and only one of them is
  // flattering. At 16 px a frame a 180 px item enters every eleventh frame, so the median frame
  // composes nothing at all and reports only the cost of laying out and drawing what is already
  // there. The mean is the one a user feels, because the frames that do compose are the frames that
  // drop. Reporting the median alone would hide the entire composition cost of a scrolling list.
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
 * One fling down the list and back up, with a cache far smaller than the list.
 *
 * This is the only row where an eviction policy is visible. Both caches are sized to hold about a
 * tenth of the images, so the way back up finds most of what it needs already gone, and every miss
 * is a decode a user would have paid for.
 *
 * A collection is forced at the turn, and that is what makes the row mean anything. Both libraries
 * keep evicted entries behind weak references, so without it the number says whether the collector
 * happened to run during the fling rather than what either cache does. It moved between 240 and 440
 * on the same code for that reason alone, and read as a policy difference that is not there: with
 * the collection forced, both sides re-decode the same 456 images.
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
  // Entries that still hold an image, not keys. Both libraries keep evicted entries behind weak
  // references, and a key whose referent has been collected outlives the image on both sides:
  // landscapist's until the tier is swept, Coil's for as long as the key is in `keys`. Counting
  // keys therefore counted images that are gone, and did it for one side only, since the sweep
  // above has already dropped landscapist's. Each key is asked for its image instead.
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
    // Before the way back, so both libraries face the same question: what does the cache still
    // hold, rather than what has the collector not got round to yet.
    System.gc()
    Thread.sleep(GC_SETTLE_MS)
    System.gc()
    val bottom = state.firstVisibleItemIndex
    repeat(frames) { advance(scene, state, -FLING_STEP, frames + it) }
    val top = state.firstVisibleItemIndex
    // A scroll that silently did not move would report one fetch per item and look like a perfect
    // cache. The trip has to be shown to have happened before its fetch count means anything.
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
 * The painter form Coil points a performance minded caller at, with a size on the request.
 *
 * Without one it falls back to `SizeResolver.ORIGINAL` and cannot reuse anything `AsyncImage`
 * cached at the layout size, which is a different comparison. [firstFrameComparison] measures that.
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
