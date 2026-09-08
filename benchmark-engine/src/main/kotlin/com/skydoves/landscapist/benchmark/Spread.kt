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
  for ((key, values) in results) {
    val sorted = values.sorted()
    val median = sorted[sorted.size / 2]
    // The spread is what the machine contributed: a gap smaller than it is not a difference.
    val spread = if (median == 0.0) 0.0 else (sorted.last() - sorted.first()) / median
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
}

/** Metric keys carry their unit as a suffix, so the spread table can render them. */
private fun format(key: String, value: Double): String = when {
  key.endsWith(".bytes") -> value.toLong().formatBytes()
  key.endsWith(".ns") -> value.toLong().formatNanos()
  else -> String.format(Locale.ROOT, "%.2f", value)
}
