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

/** Bytes allocated by the current thread while running [block], or -1 when unavailable. */
internal inline fun allocatedBytes(block: () -> Unit): Long {
  val before = threadAllocatedBytes()
  block()
  val after = threadAllocatedBytes()
  return if (before < 0 || after < 0) -1 else after - before
}

/**
 * Allocation summed over every live thread, not just this one.
 *
 * Both loaders push work onto their own dispatchers, so a current thread reading reports zero and
 * says nothing at all.
 */
private val allocationBean: com.sun.management.ThreadMXBean? by lazy {
  val bean = java.lang.management.ManagementFactory.getThreadMXBean()
  (bean as? com.sun.management.ThreadMXBean)?.takeIf { it.isThreadAllocatedMemorySupported }
    ?.apply { isThreadAllocatedMemoryEnabled = true }
}

private fun threadAllocatedBytes(): Long {
  val bean = allocationBean ?: return -1
  return bean.getThreadAllocatedBytes(bean.allThreadIds).filter { it > 0 }.sum()
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
