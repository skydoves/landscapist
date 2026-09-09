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
package com.github.skydoves.landscapistdemo.measure

import android.os.Debug
import android.util.Log

/** ART's allocation counter is process wide, so nothing else may run while it counts. */
object DeviceMeasure {

  private const val TAG = "MEASURE"

  /** The memory cache every compared loader is given, so neither is measured with more room. */
  const val MEMORY_CACHE_BYTES: Long = 32L * 1024 * 1024

  /** What this process has already measured, if anything. */
  @Volatile
  private var measured: String? = null

  /**
   * Refuses to measure anything in a process that has already measured something.
   *
   * Whichever loader ran second read as faster by more than the difference being measured, and
   * warming both stacks first did not fix it: the sockets, the thread pools and the JIT of
   * everything under Compose are shared too. The label is per measurement rather than per library,
   * because two measurements of the same library contaminate each other in the same way, and
   * resident memory is exactly the number that notices. Held here rather than on a test class, so
   * the scroll comparison cannot run both loaders in a process the load comparison already used.
   *
   * Run one measurement per instrumentation invocation, so each gets its own process:
   *
   * `-Pandroid.testInstrumentationRunnerArguments.class=...LibraryComparisonTest#coldLoadCoil`
   */
  fun claimTheProcess(label: String) {
    val previous = measured
    check(previous == null) {
      "\"$previous\" was already measured in this process, so \"$label\" cannot be compared " +
        "against it. Run one measurement per instrumentation invocation."
    }
    measured = label
  }

  /** Bytes ART has allocated in this process since it started. */
  fun allocatedBytes(): Long =
    Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: -1L

  /** A negative reading means the device does not expose the counter. */
  inline fun allocatedDuring(block: () -> Unit): Long {
    val before = allocatedBytes()
    block()
    val after = allocatedBytes()
    return if (before < 0 || after < 0) -1L else after - before
  }

  inline fun timed(
    warmups: Int = 3,
    iterations: Int = 10,
    block: () -> Unit,
  ): LongArray {
    repeat(warmups) { block() }
    settle()
    return LongArray(iterations) {
      val start = System.nanoTime()
      block()
      System.nanoTime() - start
    }
  }

  /** Gives the collector a chance to run, so one measurement does not pay for the last. */
  fun settle() {
    Runtime.getRuntime().gc()
    Thread.sleep(80)
    Runtime.getRuntime().gc()
    Thread.sleep(80)
  }

  /** The resident set of this process, which is the only number that sees native bitmaps. */
  fun residentKb(): Long {
    val info = Debug.MemoryInfo()
    Debug.getMemoryInfo(info)
    return info.totalPss.toLong()
  }

  fun report(label: String, value: String) {
    Log.e(TAG, "$label | $value")
  }

  fun LongArray.percentile(fraction: Double): Long {
    if (isEmpty()) return 0
    val sorted = sortedArray()
    val index = ((sorted.size - 1) * fraction).toInt()
    return sorted[index]
  }

  fun LongArray.median(): Long = percentile(0.5)

  fun Long.formatNanos(): String = when {
    this >= 1_000_000_000 -> "%.2f s".format(this / 1e9)
    this >= 1_000_000 -> "%.2f ms".format(this / 1e6)
    this >= 1_000 -> "%.1f us".format(this / 1e3)
    else -> "$this ns"
  }

  fun Long.formatBytes(): String = when {
    this < 0 -> "unavailable"
    this >= 1024L * 1024 -> "%.2f MiB".format(this / (1024.0 * 1024))
    this >= 1024 -> "%.1f KiB".format(this / 1024.0)
    else -> "$this B"
  }
}
