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
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A device free head to head between the landscapist-core engine and the real Coil engine.
 *
 * Run with `./gradlew :benchmark-engine:run`.
 *
 * Both loaders are handed the same already decoded image by their fetcher, so what is timed is the
 * loader itself: key building, cache lookup, the coroutine machinery, and coalescing. Decode is
 * deliberately excluded, because on the JVM Coil decodes through Skia and landscapist-core through
 * ImageIO, and an end to end number would be comparing decoders instead.
 *
 * These are single process JVM numbers on one machine. They are useful for comparing the two
 * engines against each other, not as absolute figures for any device.
 */
fun main() {
  // A child forked to have its resident set watched from outside while it decodes exactly once.
  // Checked first, because a child inherits this process's environment: a run started with -Pjfr
  // sets LANDSCAPIST_PROFILE, and the child would have profiled a Compose list instead of decoding.
  System.getenv("LANDSCAPIST_DECODE_PATH")?.let {
    runDecodeChild(it, System.getenv("LANDSCAPIST_DECODE_PHOTO"))
    return
  }
  // A profiling mode that renders one list over and over, so an allocation profiler sees nothing
  // but the Compose path. Not part of the reported numbers.
  System.getenv("LANDSCAPIST_PROFILE")?.let {
    profileComposeOnly(it)
    return
  }
  // A child of a spread run narrates nothing and reports its recorded metrics instead. The rows it
  // skips are the ones dominated by sleeping or by decoding a twelve megapixel JPEG, and neither of
  // those is where the disputed numbers are.
  if (Metrics.collecting) {
    memoryCacheHit(quiet = true)
    allocations(quiet = true)
    composeComparison()
    scrollComparison()
    Metrics.emit()
    return
  }

  println("Engine benchmark: landscapist-core vs Coil ${coilVersion()}")
  println("JVM ${System.getProperty("java.version")} on ${System.getProperty("os.arch")}")
  println("Fetcher returns a pre-decoded image for both, so decoding is out of the measurement.")
  println()
  println("=".repeat(96))
  println()

  coldLoad()
  memoryCacheHit()
  allocations()
  coalescing()
  nearIdenticalSizes()
  occupancyComparison()
  diskKeyComparison()
  decodeComparison()
  peakMemoryComparison()
  composeComparison()
  scrollComparison()
  firstFrameComparison()

  System.getenv("LANDSCAPIST_SPREAD_RUNS")?.toIntOrNull()?.let { runSpread(it) }

  println("=".repeat(96))
  println("Percentiles over the reported iteration count. Lower is better.")
  println("The ratio on each second row is Coil measured against landscapist.")
  println(
    "Every allocation row counts Java heap only. Pixels live in Skia's native memory on both " +
      "sides, so no allocation row here includes a single byte of any bitmap. The peak memory " +
      "rows are the only ones that see native memory at all.",
  )
  if (AllocationSnapshot.threadsLost > 0) {
    println(
      "WARNING: ${AllocationSnapshot.threadsLost} threads exited part way through a measured " +
        "block. A thread takes its allocation counter with it, so whatever it had allocated " +
        "since that block started is missing: those rows are lower bounds.",
    )
  }
}

private fun coilVersion(): String = "3.6.2"

/**
 * Every load misses the cache, so this is the cost of driving one request end to end through the
 * engine with the fetch itself made free.
 */
private fun coldLoad() {
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapist = newLandscapist(landscapistCounter)
  val coil = newCoil(coilCounter)

  val iterations = 2_000
  val warmups = 500

  val landscapistSamples = measure("landscapist", warmups, iterations) { i ->
    runBlocking {
      landscapist.load(landscapistRequest("https://example.com/cold-$i.jpg"))
        .first { it is ImageResult.Success }
    }
  }
  settle()
  val coilSamples = measure("coil", warmups, iterations) { i ->
    runBlocking {
      val result = coil.execute(coilRequest("https://example.com/cold-$i.jpg"))
      check(result is SuccessResult) { "coil failed: $result" }
    }
  }

  report("cold load (unique model)", landscapistSamples, coilSamples)
}

/**
 * The same model over and over, which is what a scrolling list mostly does once it has warmed up.
 */
private fun memoryCacheHit(quiet: Boolean = false) {
  val landscapist = newLandscapist(FetchCounter())
  val coil = newCoil(FetchCounter())
  val model = "https://example.com/hot.jpg"

  runBlocking {
    landscapist.load(landscapistRequest(model)).first { it is ImageResult.Success }
    coil.execute(coilRequest(model))
  }

  val iterations = 20_000
  val warmups = 5_000

  val landscapistSamples = measure("landscapist", warmups, iterations) {
    runBlocking {
      val result = landscapist.load(landscapistRequest(model)).first { it is ImageResult.Success }
      check((result as ImageResult.Success).dataSource == DataSource.MEMORY)
    }
  }
  settle()
  val coilSamples = measure("coil", warmups, iterations) {
    runBlocking {
      val result = coil.execute(coilRequest(model))
      check(result is SuccessResult && result.dataSource == coil3.decode.DataSource.MEMORY_CACHE)
    }
  }

  Metrics.record("engine.memory-hit.landscapist.ns", landscapistSamples.p50)
  Metrics.record("engine.memory-hit.coil.ns", coilSamples.p50)

  // The synchronous probe landscapist-image uses on the first frame, with no coroutine at all.
  val peek = measure("landscapist peek", warmups, iterations) {
    check(landscapist.peekMemoryCache(landscapistRequest(model)) != null)
  }
  Metrics.record("engine.peek.landscapist.ns", peek.p50)
  if (quiet) return

  report("memory cache hit", landscapistSamples, coilSamples)
  println(
    String.format(
      java.util.Locale.ROOT,
      "%-34s %-14s p50=%-10s p90=%-10s p99=%-10s",
      "  (no suspend, peekMemoryCache)",
      peek.label,
      peek.p50.formatNanos(),
      peek.p90.formatNanos(),
      peek.p99.formatNanos(),
    ),
  )
  println()
}

/** Bytes allocated per operation, which is what drives GC pressure while a list is scrolling. */
private fun allocations(quiet: Boolean = false) {
  val landscapist = newLandscapist(FetchCounter())
  val coil = newCoil(FetchCounter())
  val model = "https://example.com/alloc.jpg"

  runBlocking {
    landscapist.load(landscapistRequest(model)).first { it is ImageResult.Success }
    coil.execute(coilRequest(model))
  }

  val rounds = 2_000
  repeat(500) {
    runBlocking {
      landscapist.load(landscapistRequest(model)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model))
    }
  }
  settle()

  val landscapistBytes = allocatedBytes {
    runBlocking {
      repeat(rounds) {
        landscapist.load(landscapistRequest(model)).first { it is ImageResult.Success }
      }
    }
  }
  val coilBytes = allocatedBytes {
    runBlocking {
      repeat(rounds) { coil.execute(coilRequest(model)) }
    }
  }

  Metrics.record("engine.memory-hit.landscapist.bytes", landscapistBytes / rounds)
  Metrics.record("engine.memory-hit.coil.bytes", coilBytes / rounds)
  if (quiet) return

  println("allocation per memory cache hit")
  println("  landscapist    ${(landscapistBytes / rounds).formatBytes()}")
  println("  coil           ${(coilBytes / rounds).formatBytes()}")
  println()
}

/**
 * The same image asked for at sizes that differ by a pixel or two, which is what a grid produces
 * when its columns do not divide evenly. Counts how many times each engine went back to the fetcher
 * rather than reusing the bitmap it already had.
 */
private fun nearIdenticalSizes() {
  // More than one sequence, because only some of them flatter either side. A grid that jitters by a
  // pixel reuses what it has; a sequence that keeps growing genuinely needs more pixels every time,
  // and neither library can serve it from what it has. A sequence that shrinks is where they part:
  // Coil reuses the large entry however far down it has to scale, and landscapist refuses anything
  // more than twice the size it is drawing into, so it decodes again and Coil does not.
  //
  // These counts moved when the stub stopped claiming `isSampled = false`. That flag makes Coil's
  // isCacheValueValidForSize return true before it compares any sizes at all, so Coil used to
  // report one fetch for every sequence here regardless of what was asked for.
  val sequences = listOf(
    "a grid jittering by a pixel" to listOf(360, 359, 361, 360, 358, 360),
    "the same grid, ascending" to listOf(358, 359, 360, 361, 362, 363),
    "an image growing into a transition" to listOf(120, 200, 320, 480, 640, 800),
    "the same transition, shrinking" to listOf(800, 640, 480, 320, 200, 120),
  )
  println("fetches for one image asked for at six sizes in a row")
  for ((name, widths) in sequences) {
    val landscapistCounter = FetchCounter()
    val coilCounter = FetchCounter()
    val landscapist = newLandscapist(landscapistCounter)
    val coil = newCoil(coilCounter)
    val model = "https://example.com/grid-item.jpg"
    runBlocking {
      for (width in widths) {
        landscapist.load(landscapistRequest(model, width)).first { it is ImageResult.Success }
        coil.execute(coilRequest(model, width))
      }
    }
    println(
      "  ${name.padEnd(36)}landscapist ${landscapistCounter.count.get()}, " +
        "coil ${coilCounter.count.get()}   $widths",
    )
  }
  println()
}

/**
 * Many callers asking for the same image at once, which is what a list does when several items show
 * the same avatar. Counts how many times each engine actually reached the fetcher.
 */
private fun coalescing() {
  val concurrency = 32
  val latencyMs = 20L

  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapist = newLandscapist(landscapistCounter, latencyMs)
  val coil = newCoil(coilCounter, latencyMs)

  val rounds = 20
  var landscapistFetches = 0
  var coilFetches = 0
  val landscapistTimes = LongArray(rounds)
  val coilTimes = LongArray(rounds)

  for (round in 0 until rounds) {
    val model = "https://example.com/shared-$round.jpg"

    landscapistCounter.reset()
    var start = System.nanoTime()
    runBlocking {
      coroutineScope {
        List(concurrency) {
          async {
            landscapist.load(landscapistRequest(model)).first { it is ImageResult.Success }
          }
        }.awaitAll()
      }
    }
    landscapistTimes[round] = System.nanoTime() - start
    landscapistFetches += landscapistCounter.count.get()

    coilCounter.reset()
    start = System.nanoTime()
    runBlocking {
      coroutineScope {
        List(concurrency) { async { coil.execute(coilRequest(model)) } }.awaitAll()
      }
    }
    coilTimes[round] = System.nanoTime() - start
    coilFetches += coilCounter.count.get()
  }

  val landscapistWall = (landscapistTimes.sum() / rounds).formatNanos()
  val coilWall = (coilTimes.sum() / rounds).formatNanos()
  println("$concurrency concurrent loads of the same model, fetcher latency ${latencyMs}ms")
  println("  landscapist    ${landscapistFetches / rounds} fetch(es) per round, $landscapistWall")
  println("  coil           ${coilFetches / rounds} fetch(es) per round, $coilWall")
  println()
}
