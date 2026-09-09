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

import java.util.Locale

/** One timed scenario's results, in nanoseconds per operation. */
internal class Samples(val label: String, private val values: LongArray) {

  val p50: Long get() = percentile(50.0)
  val p90: Long get() = percentile(90.0)
  val p99: Long get() = percentile(99.0)
  val mean: Long get() = if (values.isEmpty()) 0 else values.sum() / values.size

  private val sorted: LongArray by lazy { values.sortedArray() }

  private fun percentile(p: Double): Long {
    if (sorted.isEmpty()) return 0
    val index = ((p / 100.0) * (sorted.size - 1)).toInt()
    return sorted[index]
  }
}

/**
 * Times [operation] after warming the JIT, reporting percentiles rather than a mean: a mean hides
 * the bimodality of loaders that occasionally take a slow path.
 */
internal inline fun measure(
  label: String,
  warmups: Int,
  iterations: Int,
  operation: (Int) -> Unit,
): Samples {
  repeat(warmups) { operation(it) }
  settle()

  val values = LongArray(iterations)
  for (i in 0 until iterations) {
    // Past the warmup's indices, so an index-derived input is not already cached.
    val index = warmups + i
    val start = System.nanoTime()
    operation(index)
    values[i] = System.nanoTime() - start
  }
  return Samples(label, values)
}

/**
 * Times two operations round by round, alternating which of the two goes first.
 *
 * Running one all the way through and then the other leaves whichever went second in a JVM the
 * first one warmed, and no amount of settling in between undoes that: the JIT, the allocator and
 * the collector are shared. Alternating puts any drift on both.
 */
internal inline fun measurePaired(
  firstLabel: String,
  secondLabel: String,
  warmups: Int,
  iterations: Int,
  first: (Int) -> Unit,
  second: (Int) -> Unit,
): Pair<Samples, Samples> {
  repeat(warmups) {
    first(it)
    second(it)
  }
  settle()

  val firstValues = LongArray(iterations)
  val secondValues = LongArray(iterations)
  for (i in 0 until iterations) {
    // Past the warmup's indices, so an index-derived input is not already cached.
    val index = warmups + i
    if (i % 2 == 0) {
      firstValues[i] = timeOf { first(index) }
      secondValues[i] = timeOf { second(index) }
    } else {
      secondValues[i] = timeOf { second(index) }
      firstValues[i] = timeOf { first(index) }
    }
  }
  return Samples(firstLabel, firstValues) to Samples(secondLabel, secondValues)
}

/** Nanoseconds [block] took. */
internal inline fun timeOf(block: () -> Unit): Long {
  val start = System.nanoTime()
  block()
  return System.nanoTime() - start
}

/** Gives the JIT and the collector a moment so a warmup's garbage is not billed to the run. */
internal fun settle() {
  System.gc()
  Thread.sleep(120)
}

/**
 * Bytes allocated while running [block], or -1 when unavailable. Summed over every thread because
 * both loaders push work onto their own dispatchers.
 */
internal inline fun allocatedBytes(block: () -> Unit): Long {
  val before = AllocationSnapshot.take() ?: return -1
  block()
  val after = AllocationSnapshot.take() ?: return -1
  return after.since(before)
}

/**
 * Per thread allocation counters. Diffing per thread, rather than summing totals, keeps a thread
 * born inside a measured block from billing its lifetime total; a thread that exits still takes
 * its counter with it, which [threadsLost] records.
 */
internal class AllocationSnapshot private constructor(
  private val ids: LongArray,
  private val bytes: LongArray,
) {

  /** Bytes allocated between [before] and this snapshot, over every thread alive in either. */
  fun since(before: AllocationSnapshot): Long {
    var total = 0L
    for (i in ids.indices) {
      if (bytes[i] < 0) continue
      total += bytes[i] - before.bytesFor(ids[i])
    }
    // A thread that has exited took its counter with it; count it, because the total is short.
    for (i in before.ids.indices) {
      if (before.bytes[i] >= 0 && indexOf(before.ids[i]) < 0) threadsLost++
    }
    return total
  }

  private fun bytesFor(id: Long): Long {
    val index = indexOf(id)
    return if (index < 0) 0L else bytes[index].coerceAtLeast(0L)
  }

  private fun indexOf(id: Long): Int {
    for (i in ids.indices) if (ids[i] == id) return i
    return -1
  }

  companion object {
    /** How many measurements lost a thread, and with it whatever that thread had allocated. */
    var threadsLost: Int = 0
      private set

    private val bean: com.sun.management.ThreadMXBean? by lazy {
      val bean = java.lang.management.ManagementFactory.getThreadMXBean()
      (bean as? com.sun.management.ThreadMXBean)?.takeIf { it.isThreadAllocatedMemorySupported }
        ?.apply { isThreadAllocatedMemoryEnabled = true }
    }

    fun take(): AllocationSnapshot? {
      val bean = bean ?: return null
      val ids = bean.allThreadIds
      return AllocationSnapshot(ids, bean.getThreadAllocatedBytes(ids))
    }
  }
}

/** Peak Java heap in use while [block] runs, above where it started. Sampled, so approximate. */
internal fun peakHeapBytes(sampleMicros: Long = 200, block: () -> Unit): Long {
  val memory = java.lang.management.ManagementFactory.getMemoryMXBean()
  System.gc()
  Thread.sleep(150)
  val baseline = memory.heapMemoryUsage.used
  val peak = java.util.concurrent.atomic.AtomicLong(baseline)
  val running = java.util.concurrent.atomic.AtomicBoolean(true)
  val sampler = Thread {
    while (running.get()) {
      val used = memory.heapMemoryUsage.used
      peak.updateAndGet { if (used > it) used else it }
      java.util.concurrent.locks.LockSupport.parkNanos(sampleMicros * 1_000)
    }
  }
  sampler.isDaemon = true
  sampler.priority = Thread.MAX_PRIORITY
  sampler.start()
  try {
    block()
  } finally {
    running.set(false)
    sampler.join()
  }
  return peak.get() - baseline
}

internal fun Long.formatNanos(): String = when {
  this >= 1_000_000 -> String.format(Locale.ROOT, "%.2f ms", this / 1_000_000.0)
  this >= 1_000 -> String.format(Locale.ROOT, "%.1f us", this / 1_000.0)
  else -> "$this ns"
}

/** Subtracts a floor, keeping an unavailable (-1) reading unavailable rather than negative. */
internal fun Long.above(floor: Long): Long = if (this < 0 || floor < 0) -1 else this - floor

internal fun Long.formatBytes(): String = when {
  this < 0 -> "n/a"
  this >= 1_048_576 -> String.format(Locale.ROOT, "%.2f MiB", this / 1_048_576.0)
  this >= 1024 -> String.format(Locale.ROOT, "%.1f KiB", this / 1024.0)
  else -> "$this B"
}

internal fun LongArray.median(): Long = copyOf().also { it.sort() }[size / 2]

internal fun LongArray.mean(): Long = if (isEmpty()) 0 else sum() / size

/** Prints one comparison row, with the ratio expressed against [baseline]. */
internal fun report(scenario: String, baseline: Samples, challenger: Samples) {
  val ratio = if (baseline.p50 == 0L) 0.0 else challenger.p50.toDouble() / baseline.p50.toDouble()
  println(
    String.format(
      Locale.ROOT,
      "%-34s %-14s p50=%-10s p90=%-10s p99=%-10s",
      scenario,
      baseline.label,
      baseline.p50.formatNanos(),
      baseline.p90.formatNanos(),
      baseline.p99.formatNanos(),
    ),
  )
  println(
    String.format(
      Locale.ROOT,
      "%-34s %-14s p50=%-10s p90=%-10s p99=%-10s  (%.2fx)",
      "",
      challenger.label,
      challenger.p50.formatNanos(),
      challenger.p90.formatNanos(),
      challenger.p99.formatNanos(),
      ratio,
    ),
  )
  println()
}

/** Prints a two sided row where lower is better, with the multiple Coil is off by. */
internal fun compare(label: String, landscapist: Long, coil: Long, render: (Long) -> String) {
  val ratio = if (landscapist == 0L) 0.0 else coil.toDouble() / landscapist.toDouble()
  println(
    String.format(
      Locale.ROOT,
      "  %-38s landscapist %-12s coil %-12s (%.2fx)",
      label,
      render(landscapist),
      render(coil),
      ratio,
    ),
  )
}
