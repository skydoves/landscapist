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
 * Times [operation] after warming the JIT, and reports percentiles rather than a mean.
 *
 * A mean hides the bimodality that shows up when a loader occasionally takes a slow path, and these
 * loaders are full of slow paths, so the percentiles are the honest summary.
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
    // Past the warmup's indices, so a scenario that builds its input from the index is measured on
    // inputs it has not already seen. A cold load that reused them would be timing cache hits.
    val index = warmups + i
    val start = System.nanoTime()
    operation(index)
    values[i] = System.nanoTime() - start
  }
  return Samples(label, values)
}

/** Gives the JIT and the collector a moment so a warmup's garbage is not billed to the run. */
internal fun settle() {
  System.gc()
  Thread.sleep(120)
}

/**
 * Bytes allocated while running [block], summed over every thread, or -1 when unavailable.
 *
 * The sum has to cover every thread because both loaders push work onto their own dispatchers, and
 * a current thread reading would report zero. See [AllocationSnapshot] for what that costs.
 */
internal inline fun allocatedBytes(block: () -> Unit): Long {
  val before = AllocationSnapshot.take() ?: return -1
  block()
  val after = AllocationSnapshot.take() ?: return -1
  return after.since(before)
}

/**
 * Per thread allocation counters, keyed by thread id rather than summed on the spot.
 *
 * Summing first and subtracting the totals, which is the obvious way to write this, is wrong in
 * both directions and silently so. A thread that starts during the measured block arrives with a
 * lifetime total that is billed to the block, and a thread that exits during it takes its
 * allocation out of the second sum, so the block is credited with freeing memory. Diffing per
 * thread fixes the first: a thread the block created is counted from zero, which is right, because
 * everything it allocated it allocated during the block.
 *
 * The second cannot be fixed after the fact, only detected. A thread that exits has already taken
 * its counter with it. [threadsLost] counts every time that happened, and the benchmark prints it,
 * because an allocation table built out of measurements that lost threads is not a table anyone
 * should read.
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
    // Threads that were alive before and are gone now took their counters with them. Nothing here
    // can recover the bytes; the count is so the reader knows the number is short.
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
