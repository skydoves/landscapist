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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.crossfade
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.rememberLandscapistImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The Compose layer as a user experiences it: how long from entering composition to a frame that
 * has the image in it. Rendered offscreen through [ImageComposeScene]. Both libraries get a warm
 * memory cache first, which is the state a list is in once it has been scrolled through.
 */
internal fun composeComparison() {
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapist = newLandscapist(landscapistCounter)
  val coil = newCoil(coilCounter)
  // Crossfade is what a Coil user turns on for the effect landscapist's plugin gives. Its own
  // counter, because it is a second loader with a second cache.
  val fadingCoilCounter = FetchCounter()
  val fadingCoil = newCoil(fadingCoilCounter) { crossfade(300) }
  val models = List(ITEM_COUNT) { "https://example.com/list-item-$it.jpg" }

  // Warm both caches, which is what a second pass over a list sees.
  runBlocking {
    for (model in models) {
      landscapist.load(landscapistRequest(model, ITEM_SIZE)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model, ITEM_SIZE))
      fadingCoil.execute(coilRequest(model, ITEM_SIZE))
    }
  }

  // Every variant is warmed before any is measured, floor included: measuring the first one cold
  // makes it pay for warming Compose itself.
  val warmups = 200
  val iterations = 600
  val variants = listOf<Pair<String, @Composable (Int) -> Unit>>(
    "empty scene" to { size -> EmptyList(size) },
    "landscapist" to { size -> LandscapistList(landscapist, models, size) },
    "coil" to { size -> CoilList(coil, models, size) },
    "landscapist slot" to { size -> LandscapistComposedList(landscapist, models, size) },
    "landscapist painter" to { size -> LandscapistPainterList(landscapist, models, size) },
    "coil painter sized" to { size -> CoilPainterList(coil, models, size) },
    "coil subcompose slot" to { size -> CoilSubcomposeList(coil, models, size) },
    "landscapist crossfade" to { size -> LandscapistCrossfadeList(landscapist, models, size) },
    "coil crossfade" to { size -> CoilList(fadingCoil, models, size) },
  )
  for ((_, content) in variants) {
    repeat(warmups) { renderOnce { content(ITEM_SIZE) } }
  }
  settle()

  val painted = variants.associate { (name, content) ->
    name to paintedFraction { content(ITEM_SIZE) }
  }
  val warmed = listOf(landscapistCounter, coilCounter, fadingCoilCounter)
  check(warmed.all { it.count.get() == models.size }) {
    "the warm up pass reached the fetcher a different number of times than there are images: " +
      "${warmed.map { it.count.get() }} for ${models.size} images. Nothing below is a warm " +
      "cache measurement."
  }

  println("share of the first frame actually covered by image pixels")
  for ((name, share) in painted) {
    println("  ${name.padEnd(22)}${share.asPercent()}")
  }
  val blank = painted.filterKeys { it != "empty scene" }.filterValues { it <= 0.9 }.keys
  check(blank.isEmpty()) {
    "$blank drew nothing, so the allocation numbers below are not comparable"
  }
  println()

  // A cache hit and a fetch are not the same measurement, and a variant quietly doing the second
  // would look expensive for a reason nothing to do with its Compose layer.
  warmed.forEach { it.reset() }
  for ((_, content) in variants) renderOnce { content(ITEM_SIZE) }
  val fetched = warmed.map { it.count.get() }
  println("  fetches during one pass over every variant: ${fetched.joinToString()} (all zero)")
  // Every loader, not only the two plain ones: the crossfade variants load through their own.
  check(fetched.all { it == 0 }) {
    "a variant fetched during the measured pass, so it is not a warm cache measurement: $fetched"
  }
  println()

  // Interleaved, so drift lands on every variant rather than on whichever went first, and a
  // median rather than a mean, which the odd JIT recompilation frame would drag around.
  val samples = Array(variants.size) { LongArray(iterations) }
  repeat(iterations) { round ->
    for ((index, variant) in variants.withIndex()) {
      samples[index][round] = allocatedBytes { renderOnce { variant.second(ITEM_SIZE) } }
    }
  }
  val floor = samples[0].median()
  println("allocation per first frame of $ITEM_COUNT images")
  println("  ${"empty scene".padEnd(22)}${floor.formatBytes()}  (the floor, subtracted below)")
  for (index in 1 until variants.size) {
    val perFrame = samples[index].median().above(floor)
    Metrics.record("compose.first-frame.${variants[index].first.metricKey()}.bytes", perFrame)
    println("  ${variants[index].first.padEnd(22)}${perFrame.formatBytes()}")
  }
  println()

  // Paired with the empty scene inside each iteration rather than p50 to p50: the scene drifts by
  // more than the images cost, so two separately measured medians differ only by noise.
  println("time added to a first frame of $ITEM_COUNT images, over the same scene with no images")
  // Fewer iterations, because each renders the scene twice. The pairing is what steadies it.
  val timedIterations = iterations / 4
  for (index in 1 until variants.size) {
    val variant = variants[index]
    val deltas = LongArray(timedIterations)
    repeat(timedIterations) { round ->
      val emptyStart = System.nanoTime()
      renderOnce { variants[0].second(ITEM_SIZE) }
      val emptyTime = System.nanoTime() - emptyStart
      val start = System.nanoTime()
      renderOnce { variant.second(ITEM_SIZE) }
      deltas[round] = System.nanoTime() - start - emptyTime
    }
    println("  ${variant.first.padEnd(22)}${deltas.median().formatNanos()}")
  }
  println()

  println("allocation per frame while $ITEM_COUNT images on screen are being resized")
  val scrollFloor = resizeAllocation { size -> EmptyList(size) }
  val floorLabel = "empty scene".padEnd(22)
  println("  $floorLabel${scrollFloor.formatBytes()}  (the floor, subtracted below)")
  for (index in 1 until variants.size) {
    val perFrame = resizeAllocation(variants[index].second).above(scrollFloor)
    Metrics.record("compose.resize-frame.${variants[index].first.metricKey()}.bytes", perFrame)
    println("  ${variants[index].first.padEnd(22)}${perFrame.formatBytes()}")
  }
  // The sized painter row is not comparable here: a request built against a size that changes
  // every frame is rebuilt every frame, which restarts the load. `AsyncImage` resolves its size
  // inside one request and does not.
  println(
    "    coil painter sized rebuilds its request whenever the bound moves, which is every frame " +
      "here. That is the row's cost, not the painter's.",
  )
  println()
}

/** A row label turned into something a metric table can key on. */
internal fun String.metricKey(): String = replace(' ', '-')

/**
 * What one frame costs while the images are on screen and their bounds animate, as a shared
 * element transition does. Nothing composes for the first time, so the first frame rows miss it.
 */
private fun resizeAllocation(content: @Composable (Int) -> Unit): Long {
  var offset by mutableIntStateOf(0)
  val scene = ImageComposeScene(
    width = ITEM_SIZE,
    height = ITEM_SIZE * ITEM_COUNT,
    density = Density(1f),
    coroutineContext = Dispatchers.Unconfined,
    content = { content(ITEM_SIZE - offset) },
  )
  try {
    val frames = 400
    repeat(200) {
      offset = it % 8
      Snapshot.sendApplyNotifications()
      scene.render(0L).close()
    }
    settle()
    val samples = LongArray(frames)
    repeat(frames) {
      offset = it % 8
      Snapshot.sendApplyNotifications()
      samples[it] = allocatedBytes { scene.render(0L).close() }
    }
    return samples.median()
  } finally {
    scene.close()
  }
}

/**
 * The share of the frame each list actually filled with image pixels. A loader that misses its
 * cache renders nothing, allocates less for it, and would otherwise look like the faster one.
 */
private fun paintedFraction(content: @Composable () -> Unit): Double {
  val scene = benchmarkScene(ITEM_SIZE, ITEM_SIZE * ITEM_COUNT) { content() }
  try {
    return scene.imagePixelFraction()
  } finally {
    scene.close()
  }
}

private const val ITEM_COUNT = 20
private const val ITEM_SIZE = 128

/**
 * Composes, lays out and rasterizes one frame. The scene is created and closed per measurement,
 * because resolving the image on first composition is what is being measured.
 */
private inline fun renderOnce(crossinline content: @Composable () -> Unit) {
  val scene = ImageComposeScene(
    width = ITEM_SIZE,
    height = ITEM_SIZE * ITEM_COUNT,
    density = Density(1f),
    coroutineContext = Dispatchers.Unconfined,
    content = { content() },
  )
  try {
    scene.render(0L).close()
  } finally {
    scene.close()
  }
}

@Composable
private fun EmptyList(itemSize: Int = ITEM_SIZE) {
  Column {
    repeat(ITEM_COUNT) {
      Box(Modifier.size(itemSize.dp))
    }
  }
}

@Composable
private fun LandscapistList(
  landscapist: Landscapist,
  models: List<String>,
  itemSize: Int = ITEM_SIZE,
) {
  Column {
    for (model in models) {
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        modifier = Modifier.size(itemSize.dp),
      )
    }
  }
}

/**
 * The composed path: a caller success slot, drawing the same painter as the container path so the
 * two are comparable.
 */
@Composable
private fun LandscapistComposedList(
  landscapist: Landscapist,
  models: List<String>,
  itemSize: Int = ITEM_SIZE,
) {
  Column {
    for (model in models) {
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        modifier = Modifier.size(itemSize.dp),
        success = { _, painter ->
          Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
          )
        },
      )
    }
  }
}

/**
 * The model is handed over as a plain string, so both sides resolve their size from the modifier
 * and build their own request. Building a request in composition is an allocation.
 */
@Composable
private fun CoilList(
  imageLoader: ImageLoader,
  models: List<String>,
  itemSize: Int = ITEM_SIZE,
) {
  Column {
    for (model in models) {
      AsyncImage(
        model = model,
        contentDescription = null,
        imageLoader = imageLoader,
        modifier = Modifier.size(itemSize.dp),
      )
    }
  }
}

/** Renders one list repeatedly under whatever profiler is attached. */
internal fun profileComposeOnly(which: String) {
  val landscapist = newLandscapist(FetchCounter())
  val coil = newCoil(FetchCounter())
  val models = List(ITEM_COUNT) { "https://example.com/list-item-$it.jpg" }
  runBlocking {
    for (model in models) {
      landscapist.load(landscapistRequest(model, ITEM_SIZE)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model, ITEM_SIZE))
    }
  }
  val content: @Composable (Int) -> Unit = when {
    which.startsWith("coil") -> ({ size -> CoilList(coil, models, size) })
    which.startsWith("composed") -> ({ size -> LandscapistComposedList(landscapist, models, size) })
    else -> ({ size -> LandscapistList(landscapist, models, size) })
  }
  if (which.endsWith("-resize")) {
    repeat(8) { println("profile $which: ${resizeAllocation(content).formatBytes()} per frame") }
  } else {
    repeat(50) { renderOnce { content(ITEM_SIZE) } }
    val bytes = allocatedBytes { repeat(3_000) { renderOnce { content(ITEM_SIZE) } } }
    println("profile $which: ${(bytes / 3_000).formatBytes()} per frame")
  }
}

/**
 * Coil's answer to a caller supplied success slot, which is the comparison for landscapist's
 * composed path rather than plain [AsyncImage].
 */
@Composable
private fun CoilSubcomposeList(
  imageLoader: ImageLoader,
  models: List<String>,
  itemSize: Int = ITEM_SIZE,
) {
  Column {
    for (model in models) {
      SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        imageLoader = imageLoader,
        modifier = Modifier.size(itemSize.dp),
        success = { state ->
          Image(
            painter = state.painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
          )
        },
      )
    }
  }
}

/**
 * The painter spelling Coil's documentation points a performance minded caller at. The request
 * carries a size on purpose: `rememberAsyncImagePainter(model)` falls back to
 * `SizeResolver.ORIGINAL` and cannot reuse what `AsyncImage` cached at the layout size.
 */
@Composable
private fun CoilPainterList(
  imageLoader: ImageLoader,
  models: List<String>,
  itemSize: Int = ITEM_SIZE,
) {
  Column {
    for (model in models) {
      val request = remember(model, itemSize) { coilRequest(model, itemSize) }
      Image(
        painter = rememberAsyncImagePainter(model = request, imageLoader = imageLoader),
        contentDescription = null,
        modifier = Modifier.size(itemSize.dp),
      )
    }
  }
}

/** Landscapist with the crossfade plugin installed, which forces the composed path. */
@Composable
private fun LandscapistCrossfadeList(
  landscapist: Landscapist,
  models: List<String>,
  itemSize: Int = ITEM_SIZE,
) {
  val component = rememberImageComponent { +CrossfadePlugin(duration = 300) }
  Column {
    for (model in models) {
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        component = component,
        modifier = Modifier.size(itemSize.dp),
      )
    }
  }
}

/**
 * The painter landscapist hands a caller, drawn in the caller's own [Image]. The same node shape
 * as the Coil painter row, so what is left between the two is the loader.
 */
@Composable
private fun LandscapistPainterList(
  landscapist: Landscapist,
  models: List<String>,
  itemSize: Int = ITEM_SIZE,
) {
  Column {
    for (model in models) {
      Image(
        painter = rememberLandscapistImagePainter(model = model, landscapist = landscapist),
        contentDescription = null,
        modifier = Modifier.size(itemSize.dp),
      )
    }
  }
}
