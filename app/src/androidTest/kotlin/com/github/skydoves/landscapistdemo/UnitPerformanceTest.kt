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
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.FutureTarget
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Unit-style performance tests for image loading libraries.
 * Tests the core loading/decoding performance without UI overhead.
 *
 * Run with:
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.UnitPerformanceTest
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class UnitPerformanceTest {

  companion object {
    /** Long enough for a cold fetch on an emulator. */
    private const val FRESCO_TIMEOUT_MS = 30_000L

    // Using the same GitHub-hosted test image (large JPEG)
    private const val TEST_IMAGE = "https://user-images.githubusercontent.com/24237865/" +
      "75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"

    private const val TARGET_SIZE = 800
    private const val ROUNDS = 5
  }

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setup() {
    clearAllCaches()
    Thread.sleep(2000)
    println("\n" + "=".repeat(80))
    println("Starting new test round")
    println("=".repeat(80))
  }

  @Test
  fun testGlidePerformance() {
    println("\n[Glide] Unit Performance Test ($ROUNDS rounds)")

    val results = mutableListOf<TestResult>()

    repeat(ROUNDS) { round ->
      clearAllCaches()
      Thread.sleep(500)

      val startMemory = getUsedMemoryKb()
      var bitmap: Bitmap? = null
      var success = false
      var error: String? = null

      val loadTime = measureTimeMillis {
        try {
          val futureTarget: FutureTarget<Bitmap> = Glide.with(context)
            .asBitmap()
            .load(TEST_IMAGE)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .submit(TARGET_SIZE, TARGET_SIZE)

          bitmap = futureTarget.get(30, TimeUnit.SECONDS)
          success = bitmap != null

          Glide.with(context).clear(futureTarget)
        } catch (e: Exception) {
          error = e.message ?: e::class.java.simpleName
          println("  ✗ Round ${round + 1} Error: $error")
        }
      }

      val endMemory = getUsedMemoryKb()
      val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

      results.add(TestResult(loadTime, memoryUsed, success, error))
      println("  Round ${round + 1}: ${loadTime}ms, ${memoryUsed}KB, Success: $success")

      bitmap?.recycle()
      Thread.sleep(500)
    }

    printResults("Glide", results)
    assertEveryRoundLoaded("Glide", results)
  }

  @Test
  fun testCoil3Performance() {
    println("\n[Coil3] Unit Performance Test ($ROUNDS rounds)")

    val results = mutableListOf<TestResult>()

    repeat(ROUNDS) { round ->
      clearAllCaches()
      Thread.sleep(500)

      val startMemory = getUsedMemoryKb()
      var image: coil3.Image? = null
      var success = false
      var error: String? = null

      val loadTime = measureTimeMillis {
        try {
          runBlocking {
            val imageLoader = coil3.ImageLoader.Builder(context)
              .diskCache(null)
              .memoryCache(null)
              .build()

            val request = coil3.request.ImageRequest.Builder(context)
              .data(TEST_IMAGE)
              .size(TARGET_SIZE, TARGET_SIZE)
              .build()

            val result = imageLoader.execute(request)
            success = result is coil3.request.SuccessResult
            image = (result as? coil3.request.SuccessResult)?.image
            if (!success) error = "coil returned $result"
          }
        } catch (e: Exception) {
          error = e.message ?: e::class.java.simpleName
          println("  ✗ Round ${round + 1} Error: $error")
        }
      }

      val endMemory = getUsedMemoryKb()
      val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

      results.add(TestResult(loadTime, memoryUsed, success, error))
      println("  Round ${round + 1}: ${loadTime}ms, ${memoryUsed}KB, Success: $success")

      Thread.sleep(500)
    }

    printResults("Coil3", results)
    assertEveryRoundLoaded("Coil3", results)
  }

  @Test
  fun testFrescoPerformance() {
    println("\n[Fresco] Unit Performance Test ($ROUNDS rounds)")

    val results = mutableListOf<TestResult>()

    repeat(ROUNDS) { round ->
      clearAllCaches()
      Thread.sleep(500)

      val startMemory = getUsedMemoryKb()
      var success = false
      var error: String? = null

      val loadTime = measureTimeMillis {
        try {
          runBlocking {
            val imagePipeline = com.facebook.imagepipeline.core.ImagePipelineFactory
              .getInstance()
              .imagePipeline

            val imageRequest = com.facebook.imagepipeline.request.ImageRequestBuilder
              .newBuilderWithSource(android.net.Uri.parse(TEST_IMAGE))
              .setResizeOptions(
                com.facebook.imagepipeline.common.ResizeOptions(TARGET_SIZE, TARGET_SIZE),
              )
              .build()

            val dataSource = imagePipeline.fetchDecodedImage(imageRequest, context)
            // fetchDecodedImage is asynchronous, so reading `result` straight away always saw
            // null: this measured how long it took to start a load, not to finish one.
            val deadline = System.currentTimeMillis() + FRESCO_TIMEOUT_MS
            while (!dataSource.isFinished && System.currentTimeMillis() < deadline) {
              Thread.sleep(10)
            }
            val result = dataSource.result
            success = result != null
            if (!success) error = dataSource.failureCause?.message ?: "the load did not finish"

            result?.close()
            dataSource.close()
          }
        } catch (e: Exception) {
          error = e.message ?: e::class.java.simpleName
          println("  ✗ Round ${round + 1} Error: $error")
        }
      }

      val endMemory = getUsedMemoryKb()
      val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

      results.add(TestResult(loadTime, memoryUsed, success, error))
      println("  Round ${round + 1}: ${loadTime}ms, ${memoryUsed}KB, Success: $success")

      Thread.sleep(500)
    }

    printResults("Fresco", results)
    assertEveryRoundLoaded("Fresco", results)
  }

  @Test
  fun testLandscapistPerformance() {
    println("\n[Landscapist] Unit Performance Test ($ROUNDS rounds)")

    val results = mutableListOf<TestResult>()
    val landscapist = Landscapist.getInstance()

    repeat(ROUNDS) { round ->
      clearAllCaches()
      Thread.sleep(500)

      val startMemory = getUsedMemoryKb()
      var bitmap: Any? = null
      var success = false
      var error: String? = null

      val loadTime = measureTimeMillis {
        try {
          runBlocking {
            val request = ImageRequest.builder()
              .model(TEST_IMAGE)
              .size(TARGET_SIZE, TARGET_SIZE)
              .memoryCachePolicy(com.skydoves.landscapist.core.model.CachePolicy.DISABLED)
              .diskCachePolicy(com.skydoves.landscapist.core.model.CachePolicy.DISABLED)
              .build()

            landscapist.load(request).collect { result ->
              when (result) {
                is com.skydoves.landscapist.core.model.ImageResult.Success -> {
                  bitmap = result.data
                  success = true
                }

                is com.skydoves.landscapist.core.model.ImageResult.Failure -> {
                  error = result.throwable?.message ?: "a failure with no reason given"
                  println("  ✗ Round ${round + 1} Error: $error")
                }

                else -> {}
              }
            }
          }
        } catch (e: Exception) {
          error = e.message ?: e::class.java.simpleName
          println("  ✗ Round ${round + 1} Error: $error")
        }
      }

      val endMemory = getUsedMemoryKb()
      val memoryUsed = (endMemory - startMemory).coerceAtLeast(0)

      results.add(TestResult(loadTime, memoryUsed, success, error))
      println("  Round ${round + 1}: ${loadTime}ms, ${memoryUsed}KB, Success: $success")

      Thread.sleep(500)
    }

    printResults("Landscapist", results)
    assertEveryRoundLoaded("Landscapist", results)
  }

  /** The rounds are the whole measurement, so one that loaded nothing fails the test. */
  private fun assertEveryRoundLoaded(library: String, results: List<TestResult>) {
    val failed = results.withIndex().filterNot { it.value.success }
    assertTrue(
      "$library loaded the image in only ${results.size - failed.size} of $ROUNDS rounds: " +
        failed.joinToString { "round ${it.index + 1}: ${it.value.error ?: "no image returned"}" },
      failed.isEmpty(),
    )
  }

  private fun clearAllCaches() {
    try {
      // On the main thread, which Glide requires, and before anything holds a bitmap from it:
      // clearing it underneath a live one recycles a bitmap Glide then tries to reconfigure.
      InstrumentationRegistry.getInstrumentation().runOnMainSync {
        Glide.get(context).clearMemory()
      }

      // Clear Landscapist cache
      Landscapist.getInstance().clearMemoryCache()

      // Clear Coil cache
      try {
        coil3.ImageLoader.Builder(context).build().memoryCache?.clear()
      } catch (e: Exception) {
        // Coil might not be initialized
      }

      // Clear Fresco cache
      try {
        com.facebook.imagepipeline.core.ImagePipelineFactory.getInstance()
          .imagePipeline.clearMemoryCaches()
      } catch (e: Exception) {
        // Fresco might not be initialized
      }

      // Force garbage collection
      Runtime.getRuntime().gc()
      System.gc()
      Thread.sleep(1000)

      println("All caches cleared")
    } catch (e: Exception) {
      println("Warning: Could not clear all caches: ${e.message}")
    }
  }

  private fun getUsedMemoryKb(): Long {
    Runtime.getRuntime().gc()
    Thread.sleep(100)
    val memInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memInfo)
    return memInfo.totalPss.toLong()
  }

  private fun printResults(libraryName: String, results: List<TestResult>) {
    val successResults = results.filter { it.success }

    if (successResults.isEmpty()) {
      println("\n[$libraryName] All tests failed!")
      return
    }

    val avgTime = successResults.map { it.loadTimeMs }.average()
    val minTime = successResults.minOf { it.loadTimeMs }
    val maxTime = successResults.maxOf { it.loadTimeMs }

    val avgMemory = successResults.map { it.memoryKb }.average()
    val minMemory = successResults.minOf { it.memoryKb }
    val maxMemory = successResults.maxOf { it.memoryKb }

    val successRate = (successResults.size.toFloat() / results.size * 100).toInt()

    println("\n" + "=".repeat(60))
    println("[$libraryName] Summary (${successResults.size}/${results.size} successful)")
    println("=".repeat(60))
    println("Load Time:")
    println("  Average: ${avgTime.toInt()}ms")
    println("  Range: ${minTime}ms - ${maxTime}ms")
    println("Memory:")
    println("  Average: ${avgMemory.toInt()}KB")
    println("  Range: ${minMemory}KB - ${maxMemory}KB")
    println("Success Rate: $successRate%")
    println("=".repeat(60))
    println()
  }

  private data class TestResult(
    val loadTimeMs: Long,
    val memoryKb: Long,
    val success: Boolean,
    val error: String? = null,
  )
}
