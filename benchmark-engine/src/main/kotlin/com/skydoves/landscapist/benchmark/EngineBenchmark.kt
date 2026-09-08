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

  println("=".repeat(96))
  println("Percentiles over the reported iteration count. Lower is better.")
  println("The ratio on each second row is Coil measured against landscapist.")
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
private fun memoryCacheHit() {
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

  report("memory cache hit", landscapistSamples, coilSamples)

  // The synchronous probe landscapist-image uses on the first frame, with no coroutine at all.
  val peek = measure("landscapist peek", warmups, iterations) {
    check(landscapist.peekMemoryCache(landscapistRequest(model)) != null)
  }
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
private fun allocations() {
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
  val landscapistCounter = FetchCounter()
  val coilCounter = FetchCounter()
  val landscapist = newLandscapist(landscapistCounter)
  val coil = newCoil(coilCounter)
  val model = "https://example.com/grid-item.jpg"
  val widths = listOf(360, 359, 361, 360, 358, 360)

  runBlocking {
    for (width in widths) {
      landscapist.load(landscapistRequest(model, width)).first { it is ImageResult.Success }
      coil.execute(coilRequest(model, width))
    }
  }

  println("same image at ${widths.size} near identical sizes $widths")
  println("  landscapist    ${landscapistCounter.count.get()} fetch(es)")
  println("  coil           ${coilCounter.count.get()} fetch(es)")
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
