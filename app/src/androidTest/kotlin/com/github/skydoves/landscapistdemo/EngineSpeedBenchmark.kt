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
package com.github.skydoves.landscapistdemo

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.allowHardware
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.skydoves.landscapist.core.BitmapConfig
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.builder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

/**
 * Engine-level loader speed benchmark, bypassing Compose so the measurement is the raw
 * fetch + decode time of each loader (not the UI). Caches are disabled and every iteration uses a
 * distinct URL, so each measurement is a cold load. Point it at a fast local server with
 * `-e baseUrl http://127.0.0.1:PORT` (via `adb reverse`) to remove network noise and resolve the
 * loader overhead with nanosecond precision.
 *
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.EngineSpeedBenchmark \
 *   -Pandroid.testInstrumentationRunnerArguments.baseUrl=http://127.0.0.1:8099
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class EngineSpeedBenchmark {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val baseUrl: String =
    InstrumentationRegistry.getArguments().getString("baseUrl")?.trimEnd('/') ?: "https://picsum.photos"

  private val runId: String = UUID.randomUUID().toString().take(8)

  private fun url(engine: String, index: Int, size: Int): String =
    "$baseUrl/seed/${runId}_${engine}_$index/$size/$size"

  /** Runs [block] WARMUP times unmeasured, then MEASURED times, returning per-iteration nanos. */
  private fun measure(block: (index: Int) -> Boolean): List<Long> {
    repeat(WARMUP) { i -> check(block(-1 - i)) { "warmup load failed" } }
    val samples = ArrayList<Long>(MEASURED)
    for (i in 0 until MEASURED) {
      var ok = false
      val ns = measureNanoTime { ok = block(i) }
      check(ok) { "measured load failed at $i" }
      samples.add(ns)
    }
    return samples
  }

  @Test
  fun engineSpeed() {
    val results = LinkedHashMap<String, List<Long>>()

    results["Coil3"] = measureCoil("coil", allowHardware = true)
    results["Coil3-ARGB"] = measureCoil("coilargb", allowHardware = false)

    results["Glide"] = measure { i ->
      val target = Glide.with(context)
        .asBitmap()
        .load(url("glide", i, SIZE))
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true)
        .submit(SIZE, SIZE)
      val bitmap: Bitmap? = target.get(30, TimeUnit.SECONDS)
      Glide.with(context).clear(target)
      bitmap != null
    }

    results["Landscapist"] = measureLandscapist(landscapist, "ls")
    results["Landscapist-ARGB"] = measureLandscapist(landscapistArgb, "lsargb")
    results["Landscapist-ARGB-disk"] = measureLandscapist(landscapistArgb, "lsdisk", disk = true)
    results["Landscapist-565"] = measureLandscapist(landscapist565, "ls565")

    report(results)
  }

  private fun measureCoil(tag: String, allowHardware: Boolean): List<Long> = measure { i ->
    runBlocking {
      val request = coil3.request.ImageRequest.Builder(context)
        .data(url(tag, i, SIZE))
        .size(SIZE, SIZE)
        .allowHardware(allowHardware)
        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
        .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
        .build()
      coilLoader.execute(request) is coil3.request.SuccessResult
    }
  }

  private fun measureLandscapist(
    loader: Landscapist,
    tag: String,
    disk: Boolean = false,
  ): List<Long> = measure { i ->
    runBlocking {
      val request = ImageRequest.builder()
        .model(url(tag, i, SIZE))
        .size(SIZE, SIZE)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(if (disk) CachePolicy.ENABLED else CachePolicy.DISABLED)
        .build()
      val terminal = loader.load(request)
        .first { it is ImageResult.Success || it is ImageResult.Failure }
      terminal is ImageResult.Success
    }
  }

  private fun report(results: Map<String, List<Long>>) {
    println("\n" + "=".repeat(72))
    println("ENGINE SPEED BENCHMARK  size=$SIZE  warmup=$WARMUP  measured=$MEASURED  base=$baseUrl")
    println("=".repeat(72))
    println(String.format(Locale.US, "%-14s | %9s | %9s | %9s | %9s", "Engine", "avg", "median", "min", "p90"))
    println("-".repeat(72))
    results.entries
      .map { (engine, ns) -> engine to ns.sorted() }
      .sortedBy { (_, ns) -> ns[ns.size / 2] }
      .forEach { (engine, ns) ->
        val ms = ns.map { it / 1_000_000.0 }
        val avg = ms.average()
        val median = ms[ms.size / 2]
        val min = ms.first()
        val p90 = ms[(ms.size * 90 / 100).coerceAtMost(ms.size - 1)]
        println(
          String.format(
            Locale.US,
            "%-14s | %7.2fms | %7.2fms | %7.2fms | %7.2fms",
            engine, avg, median, min, p90,
          ),
        )
      }
    println("=".repeat(72) + "\n")
  }

  private val coilLoader: coil3.ImageLoader by lazy {
    coil3.ImageLoader.Builder(context)
      .components { add(coil3.network.okhttp.OkHttpNetworkFetcherFactory()) }
      .build()
  }

  private val landscapist: Landscapist by lazy { Landscapist.builder(context).build() }

  private val landscapistArgb: Landscapist by lazy {
    Landscapist.builder(context)
      .config(LandscapistConfig(bitmapConfig = BitmapConfig(allowHardware = false)))
      .build()
  }

  private val landscapist565: Landscapist by lazy {
    Landscapist.builder(context)
      .config(LandscapistConfig(allowRgb565 = true, bitmapConfig = BitmapConfig(allowHardware = false)))
      .build()
  }

  private companion object {
    private const val SIZE = 1200
    private const val WARMUP = 6
    private const val MEASURED = 40
  }
}
