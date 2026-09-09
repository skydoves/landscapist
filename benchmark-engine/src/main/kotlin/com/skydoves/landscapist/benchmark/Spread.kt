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
import kotlin.math.abs

/**
 * The headline numbers, recorded as data as well as printed, so [runSpread] can rerun the whole
 * benchmark in fresh JVMs and show how much of a difference is only the machine.
 */
internal object Metrics {

  private val recorded = LinkedHashMap<String, Double>()

  /** Whether this process is a child collecting numbers for a parent. */
  val collecting: Boolean = System.getenv("LANDSCAPIST_SPREAD") != null

  fun record(key: String, value: Double) {
    recorded[key] = value
  }

  fun record(key: String, value: Long) = record(key, value.toDouble())

  fun emit() {
    for ((key, value) in recorded) {
      println("#metric\t$key\t$value")
    }
  }
}

/** Runs this benchmark again in [runs] fresh JVMs and reports the spread of every metric. */
internal fun runSpread(runs: Int) {
  val java = "${System.getProperty("java.home")}/bin/java"
  val classpath = System.getProperty("java.class.path")
  val results = LinkedHashMap<String, MutableList<Double>>()
  println("running $runs more JVMs to see how much of this is the machine")
  for (run in 1..runs) {
    val process = ProcessBuilder(
      java,
      "-cp",
      classpath,
      "com.skydoves.landscapist.benchmark.EngineBenchmarkKt",
    ).redirectErrorStream(true).also { it.environment()["LANDSCAPIST_SPREAD"] = "1" }.start()
    process.inputStream.bufferedReader().forEachLine { line ->
      val parts = line.split('\t')
      if (parts.size == 3 && parts[0] == "#metric") {
        val value = parts[2].toDoubleOrNull() ?: return@forEachLine
        results.getOrPut(parts[1]) { mutableListOf() }.add(value)
      }
    }
    val code = process.waitFor()
    check(code == 0) { "spread run $run exited with $code" }
    println("  run $run of $runs done")
  }
  println()
  println("across $runs JVMs, each a separate process")
  println(
    String.format(
      Locale.ROOT,
      "  %-46s %-12s %-12s %-12s %s",
      "metric",
      "min",
      "median",
      "max",
      "spread",
    ),
  )
  val medians = LinkedHashMap<String, Double>()
  val spreads = LinkedHashMap<String, Double>()
  for ((key, values) in results) {
    val sorted = values.sorted()
    val median = sorted[sorted.size / 2]
    // What the machine contributed. The rule that follows from it is applied under the table.
    val spread = if (median == 0.0) 0.0 else (sorted.last() - sorted.first()) / median
    medians[key] = median
    spreads[key] = spread
    println(
      String.format(
        Locale.ROOT,
        "  %-46s %-12s %-12s %-12s %.1f%%",
        key,
        format(key, sorted.first()),
        format(key, median),
        format(key, sorted.last()),
        spread * 100,
      ),
    )
  }
  println()
  reportGaps(medians, spreads)
}

/**
 * Applies the rule the spread column implies: a gap between the two loaders that is smaller than
 * what the machine contributed is not a difference. The spread used to be computed and printed
 * with nothing consuming it, so every comparison could still be read as if it were not there.
 */
private fun reportGaps(medians: Map<String, Double>, spreads: Map<String, Double>) {
  val pairs = medians.keys
    .filter { it.contains("landscapist") }
    .mapNotNull { key ->
      val coilKey = key.replace("landscapist", "coil")
      if (medians.containsKey(coilKey)) key to coilKey else null
    }
  if (pairs.isEmpty()) return
  println("landscapist against coil, each gap measured against the machine's own spread")
  for ((key, coilKey) in pairs) {
    val mine = medians.getValue(key)
    val theirs = medians.getValue(coilKey)
    val smaller = minOf(mine, theirs)
    val gap = if (smaller == 0.0) 0.0 else abs(mine - theirs) / smaller
    val noise = maxOf(spreads.getValue(key), spreads.getValue(coilKey))
    println(
      String.format(
        Locale.ROOT,
        "  %-42s landscapist %-12s coil %-12s gap %5.1f%% spread %5.1f%%  %s",
        key.replace("landscapist", "*"),
        format(key, mine),
        format(coilKey, theirs),
        gap * 100,
        noise * 100,
        if (gap > noise) "a difference" else "inside the noise",
      ),
    )
  }
  println()
}

/** Metric keys carry their unit as a suffix, so the spread table can render them. */
private fun format(key: String, value: Double): String = when {
  key.endsWith(".bytes") -> value.toLong().formatBytes()
  key.endsWith(".ns") -> value.toLong().formatNanos()
  else -> String.format(Locale.ROOT, "%.2f", value)
}
