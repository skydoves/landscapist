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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
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
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * How many frames a user waits before the image is actually on screen.
 *
 * This is the claim landscapist makes that nothing else here checks: it reads its memory cache
 * during composition, so an image already decoded is in the very first frame, while Coil documents
 * its painter state as Empty for the first composition and resolves afterwards. A claim about the
 * first frame cannot be tested by a benchmark that only reports what a first frame costs.
 *
 * Three cases, because the answer is different in each and only one of them is flattering:
 * a warm cache, a cold cache with the instant fetcher every other row here uses, and a cold cache
 * with a fetcher slow enough that no loader can finish inside one frame.
 */
internal fun firstFrameComparison() {
  println("frames before the image is fully on screen (0 means the very first frame)")

  warmCase()
  coldCase("cold cache, instant fetcher", latencyMs = 0)
  coldCase("cold cache, 4 ms fetcher", latencyMs = 4)
  crossfadeCase()
  println(
    "  The warm row cannot separate the two and should not be quoted as if it did. The scene " +
      "runs on Dispatchers.Unconfined, so Coil's load resolves inline during the first " +
      "composition and lands in the same frame. On a device its work goes through a real " +
      "dispatcher and its own documentation says the painter is Empty for that first " +
      "composition. Only a harness with a real frame clock can measure that difference; this " +
      "one gives Coil the frame for free.",
  )
  println(
    "  The cold rows are the ones that carry information, and they do not favour landscapist. It " +
      "cannot build a sized request until a layout pass has told it the bounds, so its load " +
      "starts a frame later than Coil's, whose size resolver suspends inside a request that was " +
      "already issued.",
  )
  println()

  loadingAndFailure()
  placeholderCase()
}

private const val TRIALS = 8
private const val TILE = 200
private const val MAX_FRAMES = 90
private const val FRAME_MILLIS = 16L
private const val FRAME_NANOS = 16_666_667L

private fun warmCase() {
  val landscapist = newLandscapist(FetchCounter())
  val coil = newCoil(FetchCounter())
  val models = List(TRIALS) { "https://example.com/warm-frame-$it.jpg" }
  runBlocking {
    for (model in models) {
      landscapist.load(landscapistRequest(model, TILE)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model, TILE))
    }
  }
  reportFrames(
    "warm memory cache",
    models,
    listOf(
      FrameVariant("landscapist") { m -> { LandscapistTile(landscapist, m) } },
      FrameVariant("coil") { m -> { CoilTile(coil, m) } },
      FrameVariant("coil painter, sized") { m -> { CoilPainterTile(coil, m, sized = true) } },
      FrameVariant("coil painter, unsized") { m -> { CoilPainterTile(coil, m, sized = false) } },
      FrameVariant("coil subcompose") { m -> { CoilSubcomposeTile(coil, m) } },
    ),
  )
}

private fun coldCase(label: String, latencyMs: Long) {
  // A loader per trial, so nothing is ever warm. Sharing one would warm it on the first trial.
  reportFrames(
    label,
    List(TRIALS) { "https://example.com/cold-frame-$it.jpg" },
    listOf(
      FrameVariant("landscapist") { m ->
        val loader = newLandscapist(FetchCounter(), latencyMs)
        val content: @Composable () -> Unit = { LandscapistTile(loader, m) }
        content
      },
      FrameVariant("coil") { m ->
        val loader = newCoil(FetchCounter(), latencyMs)
        val content: @Composable () -> Unit = { CoilTile(loader, m) }
        content
      },
      FrameVariant("coil painter, sized") { m ->
        val loader = newCoil(FetchCounter(), latencyMs)
        val content: @Composable () -> Unit = { CoilPainterTile(loader, m, sized = true) }
        content
      },
      FrameVariant("coil painter, unsized") { m ->
        val loader = newCoil(FetchCounter(), latencyMs)
        val content: @Composable () -> Unit = { CoilPainterTile(loader, m, sized = false) }
        content
      },
      FrameVariant("coil subcompose") { m ->
        val loader = newCoil(FetchCounter(), latencyMs)
        val content: @Composable () -> Unit = { CoilSubcomposeTile(loader, m) }
        content
      },
    ),
  )
}

/**
 * The same question with a fade in, where a fully drawn image is by definition several frames away.
 *
 * Both sides are warm, so this is the animation and nothing else. It is the honest counterweight to
 * the first frame claim: landscapist's own crossfade plugin gives up the advantage its synchronous
 * cache read buys, and so does Coil's.
 */
private fun crossfadeCase() {
  val landscapist = newLandscapist(FetchCounter())
  val coil = newCoil(FetchCounter()) { crossfade(300) }
  val models = List(TRIALS) { "https://example.com/fade-frame-$it.jpg" }
  runBlocking {
    for (model in models) {
      landscapist.load(landscapistRequest(model, TILE)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model, TILE))
    }
  }
  reportFrames(
    "warm cache, 300 ms crossfade",
    models,
    listOf(
      FrameVariant("landscapist") { m -> { LandscapistTile(landscapist, m, crossfade = true) } },
      FrameVariant("coil") { m -> { CoilTile(coil, m) } },
    ),
  )
  println(
    "    Both are zero, and that is the result: neither fade runs. Landscapist reads the cache " +
      "during composition so there is no loading state to fade out of, and Coil skips its " +
      "crossfade on a memory cache hit by design. Every crossfade row in this benchmark is " +
      "therefore the cost of setting an animation up, not of running one.",
  )
}

/** One thing to render, built fresh per model so a cold case cannot warm itself on trial one. */
private class FrameVariant(
  val name: String,
  val build: (String) -> (@Composable () -> Unit),
)

private fun reportFrames(label: String, models: List<String>, variants: List<FrameVariant>) {
  println("  $label")
  for (variant in variants) {
    val frames = models.map { model -> framesToImage(variant.build(model)) }
    val worst = frames.maxOrNull() ?: 0
    val median = frames.sorted()[frames.size / 2]
    val never = frames.count { it == MAX_FRAMES }
    val suffix = if (never > 0) "  ($never of ${frames.size} never drew)" else ""
    Metrics.record("first-frame.${label.metricKey()}.${variant.name.metricKey()}", median.toLong())
    println("    ${variant.name.padEnd(22)}median $median, worst $worst$suffix")
  }
}

/**
 * Renders frame by frame in real time until the tile is the image, and reports which frame.
 *
 * Real time, not a virtual clock, because a fetcher that suspends and an animation that
 * interpolates both resolve against the wall clock. The frame the loop is on is passed to the
 * renderer so an animation sees a plausible frame time rather than the same one every render.
 */
private fun framesToImage(content: @Composable () -> Unit): Int {
  val scene = benchmarkScene(TILE, TILE, content)
  try {
    for (frame in 0 until MAX_FRAMES) {
      Snapshot.sendApplyNotifications()
      if (scene.imagePixelFraction(frame * FRAME_NANOS) > 0.95) return frame
      Thread.sleep(FRAME_MILLIS)
    }
    return MAX_FRAMES
  } finally {
    scene.close()
  }
}

/**
 * The two paths a benchmark that only ever succeeds never touches.
 *
 * A list on a slow connection spends its time in the loading state, and a list with a dead URL in
 * it spends the rest in the failure state. Both are frames a user sees, and both are frames these
 * libraries recompose and redraw.
 */
private fun loadingAndFailure() {
  val slowLandscapist = newLandscapist(FetchCounter(), latencyMs = 5_000)
  val slowCoil = newCoil(FetchCounter(), latencyMs = 5_000)
  val failingLandscapist = newLandscapist(
    FetchCounter(),
    fetcher = LandscapistFailingFetcher(),
  )
  val failingCoil = newCoil(FetchCounter(), failing = true)
  val models = List(TILE_COUNT) { "https://example.com/state-$it.jpg" }

  println("allocation per frame with $TILE_COUNT images stuck in each state, over an empty column")
  val cases = listOf<Pair<String, @Composable () -> Unit>>(
    "empty" to { EmptyColumn() },
    "landscapist loading" to { LandscapistColumn(slowLandscapist, models) },
    "coil loading" to { CoilColumn(slowCoil, models) },
    "landscapist failure" to { LandscapistColumn(failingLandscapist, models) },
    "coil failure" to { CoilColumn(failingCoil, models) },
  )
  val samples = cases.map { (_, content) -> stateFrames(content) }
  val floor = samples[0].median()
  for (index in 1 until cases.size) {
    println("  ${cases[index].first.padEnd(22)}${(samples[index].median() - floor).formatBytes()}")
  }
  println()
}

/**
 * A placeholder, which is what both libraries put on screen while the image is still coming.
 *
 * Coil takes a painter on `AsyncImage`; landscapist takes a plugin. The plugin is a composable of
 * its own, so this is the row where the plugin architecture is paid for rather than assumed free.
 */
private fun placeholderCase() {
  val landscapist = newLandscapist(FetchCounter(), latencyMs = 5_000)
  val coil = newCoil(FetchCounter(), latencyMs = 5_000)
  val models = List(TILE_COUNT) { "https://example.com/placeholder-$it.jpg" }

  println("allocation per frame with $TILE_COUNT placeholders on screen, over an empty column")
  val cases = listOf<Pair<String, @Composable () -> Unit>>(
    "empty" to { EmptyColumn() },
    "landscapist plugin" to { LandscapistPlaceholderColumn(landscapist, models) },
    "coil placeholder" to { CoilPlaceholderColumn(coil, models) },
  )
  val samples = cases.map { (_, content) -> stateFrames(content) }
  val floor = samples[0].median()
  for (index in 1 until cases.size) {
    println("  ${cases[index].first.padEnd(22)}${(samples[index].median() - floor).formatBytes()}")
  }
  // Both are meant to be showing something. A placeholder that never drew would allocate less for
  // doing nothing, and would read as the cheaper one.
  for ((name, content) in cases.drop(1)) {
    val scene = benchmarkScene(TILE_SIZE, TILE_SIZE * TILE_COUNT, content)
    try {
      repeat(3) { scene.render(it * FRAME_NANOS).close() }
      val covered = scene.imagePixelFraction(3 * FRAME_NANOS)
      println("    ${name.padEnd(20)}is covering ${covered.asPercent()} of the frame")
    } finally {
      scene.close()
    }
  }
  println()
}

private const val TILE_COUNT = 20
private const val TILE_SIZE = 128

/** Allocation per rendered frame for a column parked in one state. */
private fun stateFrames(content: @Composable () -> Unit): LongArray {
  val scene = benchmarkScene(TILE_SIZE, TILE_SIZE * TILE_COUNT, content)
  try {
    repeat(60) { scene.render(it * FRAME_NANOS).close() }
    settle()
    val samples = LongArray(200)
    repeat(samples.size) { frame ->
      samples[frame] = allocatedBytes {
        Snapshot.sendApplyNotifications()
        scene.render((60 + frame) * FRAME_NANOS).close()
      }
    }
    return samples
  } finally {
    scene.close()
  }
}

@Composable
private fun LandscapistTile(landscapist: Landscapist, model: String, crossfade: Boolean = false) {
  val component = if (crossfade) {
    rememberImageComponent { +CrossfadePlugin(duration = 300) }
  } else {
    rememberImageComponent {}
  }
  LandscapistImage(
    imageModel = { model },
    landscapist = landscapist,
    component = component,
    modifier = Modifier.size(TILE.dp),
  )
}

@Composable
private fun CoilTile(imageLoader: ImageLoader, model: String) {
  AsyncImage(
    model = model,
    contentDescription = null,
    imageLoader = imageLoader,
    modifier = Modifier.size(TILE.dp),
  )
}

/**
 * Coil's fast painter, written both ways.
 *
 * `AsyncImage` attaches a `ConstraintsSizeResolver`, so it asks for the layout size and finds what
 * is in memory. `rememberAsyncImagePainter(model)` does not: `AsyncImagePainter.updateRequest`
 * falls back to `SizeResolver.ORIGINAL` when no size resolver was set, so the obvious spelling asks
 * for the full sized image, cannot use the entry `AsyncImage` cached at the layout size, and goes
 * back out to the network for it. That is the API Coil's own documentation points a performance
 * minded caller at, so both spellings are here.
 */
@Composable
private fun CoilPainterTile(imageLoader: ImageLoader, model: String, sized: Boolean) {
  val request = if (sized) remember(model) { coilRequest(model, TILE) } else null
  Image(
    painter = rememberAsyncImagePainter(model = request ?: model, imageLoader = imageLoader),
    contentDescription = null,
    modifier = Modifier.size(TILE.dp),
  )
}

@Composable
private fun CoilSubcomposeTile(imageLoader: ImageLoader, model: String) {
  SubcomposeAsyncImage(
    model = model,
    contentDescription = null,
    imageLoader = imageLoader,
    modifier = Modifier.size(TILE.dp),
    success = { state ->
      Image(painter = state.painter, contentDescription = null, modifier = Modifier.fillMaxSize())
    },
  )
}

@Composable
private fun EmptyColumn() {
  Column {
    repeat(TILE_COUNT) { Box(Modifier.size(TILE_SIZE.dp)) }
  }
}

@Composable
private fun LandscapistColumn(landscapist: Landscapist, models: List<String>) {
  Column {
    for (model in models) {
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        modifier = Modifier.size(TILE_SIZE.dp),
      )
    }
  }
}

@Composable
private fun CoilColumn(imageLoader: ImageLoader, models: List<String>) {
  Column {
    for (model in models) {
      AsyncImage(
        model = model,
        contentDescription = null,
        imageLoader = imageLoader,
        modifier = Modifier.size(TILE_SIZE.dp),
      )
    }
  }
}

@Composable
private fun LandscapistPlaceholderColumn(landscapist: Landscapist, models: List<String>) {
  val component = rememberImageComponent {
    +PlaceholderPlugin.Loading(ColorPainter(Color(STUB_COLOR)))
  }
  Column {
    for (model in models) {
      LandscapistImage(
        imageModel = { model },
        landscapist = landscapist,
        component = component,
        modifier = Modifier.size(TILE_SIZE.dp),
      )
    }
  }
}

@Composable
private fun CoilPlaceholderColumn(imageLoader: ImageLoader, models: List<String>) {
  val placeholder = ColorPainter(Color(STUB_COLOR))
  Column {
    for (model in models) {
      AsyncImage(
        model = model,
        contentDescription = null,
        imageLoader = imageLoader,
        placeholder = placeholder,
        modifier = Modifier.size(TILE_SIZE.dp),
      )
    }
  }
}
