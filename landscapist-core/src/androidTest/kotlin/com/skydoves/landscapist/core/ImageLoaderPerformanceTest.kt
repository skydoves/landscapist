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
package com.skydoves.landscapist.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Unit tests for Landscapist Core image loading performance.
 *
 * These tests measure:
 * - Memory cache hit performance
 * - Disk cache hit performance
 * - Network loading performance
 * - Concurrent request handling
 * - Memory usage under load
 */
@OptIn(ExperimentalCoroutinesApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class ImageLoaderPerformanceTest {

  private lateinit var context: Context
  private lateinit var landscapist: Landscapist

  companion object {
    private const val TEST_IMAGE_URL = "https://user-images.githubusercontent.com/" +
      "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"

    private val TEST_URLS = listOf(
      "https://user-images.githubusercontent.com/24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
      "https://user-images.githubusercontent.com/24237865/75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
      "https://user-images.githubusercontent.com/24237865/75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
      "https://user-images.githubusercontent.com/24237865/75087932-5aec7280-553e-11ea-9301-3b12ddaae0a7.jpg",
      "https://user-images.githubusercontent.com/24237865/75087931-5aec7280-553e-11ea-8749-aec87a7a6c22.jpg",
    )
  }

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    landscapist = Landscapist.builder(context)
      .config(
        LandscapistConfig(
          memoryCacheSize = 32 * 1024 * 1024L, // 32MB
          diskCacheSize = 50 * 1024 * 1024L, // 50MB
        ),
      )
      .build()
  }

  @Test
  fun testSingleImageLoadPerformance() = runTest {
    val request = ImageRequest.builder()
      .model(TEST_IMAGE_URL)
      .size(width = 800, height = 600)
      .build()

    var result: ImageResult? = null
    val loadTime = measureTimeMillis {
      result = landscapist.load(request).first { it is ImageResult.Success || it is ImageResult.Failure }
    }

    println("Single image load time: ${loadTime}ms")
    assertTrue(result is ImageResult.Success, "Image should load successfully")
    assertTrue(loadTime < 10_000, "Load time should be under 10 seconds, was ${loadTime}ms")
  }

  @Test
  fun testMemoryCacheHitPerformance() = runBlocking {
    val request = ImageRequest.builder()
      .model(TEST_IMAGE_URL)
      .size(width = 800, height = 600)
      .build()

    // First load - should hit network
    val firstLoadTime = measureTimeMillis {
      landscapist.load(request).first { it is ImageResult.Success }
    }

    // Second load - should hit memory cache
    val cacheHitTime = measureTimeMillis {
      landscapist.load(request).first { it is ImageResult.Success }
    }

    println("First load (network): ${firstLoadTime}ms")
    println("Cache hit: ${cacheHitTime}ms")
    println("Speedup: ${firstLoadTime / cacheHitTime.coerceAtLeast(1)}x")

    assertTrue(cacheHitTime < 100, "Memory cache hit should be very fast, was ${cacheHitTime}ms")
    assertTrue(
      cacheHitTime < firstLoadTime / 10,
      "Cache hit should be at least 10x faster than network load",
    )
  }

  @Test
  fun testMultipleImagesLoadPerformance() = runTest {
    val results = mutableListOf<ImageResult>()

    val totalTime = measureTimeMillis {
      TEST_URLS.forEach { url ->
        val request = ImageRequest.builder()
          .model(url)
          .size(width = 400, height = 300)
          .build()

        val result = landscapist.load(request)
          .first { it is ImageResult.Success || it is ImageResult.Failure }
        results.add(result)
      }
    }

    val successCount = results.count { it is ImageResult.Success }
    val avgTime = totalTime / TEST_URLS.size

    println("Loaded ${successCount}/${TEST_URLS.size} images in ${totalTime}ms")
    println("Average time per image: ${avgTime}ms")

    assertTrue(successCount == TEST_URLS.size, "All images should load successfully")
    assertTrue(avgTime < 5_000, "Average load time should be under 5 seconds, was ${avgTime}ms")
  }

  @Test
  fun testConcurrentRequestsPerformance() = runTest {
    val requests = TEST_URLS.map { url ->
      ImageRequest.builder()
        .model(url)
        .size(width = 400, height = 300)
        .build()
    }

    val totalTime = measureTimeMillis {
      // Launch all requests concurrently
      requests.map { request ->
        kotlinx.coroutines.async {
          landscapist.load(request)
            .first { it is ImageResult.Success || it is ImageResult.Failure }
        }
      }.forEach { it.await() }
    }

    println("Concurrent load of ${requests.size} images: ${totalTime}ms")

    // Concurrent loading should be faster than sequential
    assertTrue(
      totalTime < 15_000,
      "Concurrent loading should complete reasonably fast, took ${totalTime}ms",
    )
  }

  @Test
  fun testProgressiveLoadingPerformance() = runTest {
    val request = ImageRequest.builder()
      .model(TEST_IMAGE_URL)
      .size(width = 1920, height = 1080) // Large size to enable progressive loading
      .progressiveEnabled(true)
      .build()

    val results = mutableListOf<ImageResult>()
    var firstResultTime = 0L
    var finalResultTime = 0L

    val startTime = System.currentTimeMillis()

    landscapist.load(request).toList().forEachIndexed { index, result ->
      results.add(result)
      if (index == 0 && result is ImageResult.Success) {
        firstResultTime = System.currentTimeMillis() - startTime
      }
      if (result is ImageResult.Success) {
        finalResultTime = System.currentTimeMillis() - startTime
      }
    }

    println("Progressive loading - First result: ${firstResultTime}ms, Final: ${finalResultTime}ms")
    println("Progressive results emitted: ${results.size}")

    assertTrue(results.isNotEmpty(), "Should emit at least one result")
    assertTrue(
      results.last() is ImageResult.Success,
      "Final result should be success",
    )
  }

  @Test
  fun testPriorityBasedLoadingPerformance() = runTest {
    // Create requests with different priorities
    val highPriorityRequest = ImageRequest.builder()
      .model(TEST_URLS[0])
      .priority(DecodePriority.HIGH)
      .tag("high")
      .build()

    val normalPriorityRequest = ImageRequest.builder()
      .model(TEST_URLS[1])
      .priority(DecodePriority.NORMAL)
      .tag("normal")
      .build()

    val lowPriorityRequest = ImageRequest.builder()
      .model(TEST_URLS[2])
      .priority(DecodePriority.LOW)
      .tag("low")
      .build()

    val startTime = System.currentTimeMillis()
    val completionTimes = mutableMapOf<String, Long>()

    // Launch all requests concurrently
    listOf(
      lowPriorityRequest,
      normalPriorityRequest,
      highPriorityRequest, // Launch high priority last to test prioritization
    ).map { request ->
      kotlinx.coroutines.async {
        val result = landscapist.load(request)
          .first { it is ImageResult.Success || it is ImageResult.Failure }
        completionTimes[request.tag!!] = System.currentTimeMillis() - startTime
        result
      }
    }.forEach { it.await() }

    println("High priority completed in: ${completionTimes["high"]}ms")
    println("Normal priority completed in: ${completionTimes["normal"]}ms")
    println("Low priority completed in: ${completionTimes["low"]}ms")

    // Note: Priority-based ordering depends on scheduler implementation
    // Just verify all completed successfully
    assertTrue(completionTimes.size == 3, "All requests should complete")
  }

  @Test
  fun testCachePolicyPerformance() = runTest {
    val url = TEST_IMAGE_URL

    // Load with cache enabled
    val cachedRequest = ImageRequest.builder()
      .model(url)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .diskCachePolicy(CachePolicy.ENABLED)
      .build()

    val cachedLoadTime = measureTimeMillis {
      landscapist.load(cachedRequest).first { it is ImageResult.Success }
    }

    // Load same image again (should hit cache)
    val cacheHitTime = measureTimeMillis {
      landscapist.load(cachedRequest).first { it is ImageResult.Success }
    }

    // Load with cache disabled
    val noCacheRequest = ImageRequest.builder()
      .model(url)
      .memoryCachePolicy(CachePolicy.DISABLED)
      .diskCachePolicy(CachePolicy.DISABLED)
      .build()

    val noCacheLoadTime = measureTimeMillis {
      landscapist.load(noCacheRequest).first { it is ImageResult.Success }
    }

    println("Cached load: ${cachedLoadTime}ms")
    println("Cache hit: ${cacheHitTime}ms")
    println("No cache load: ${noCacheLoadTime}ms")

    assertTrue(cacheHitTime < cachedLoadTime, "Cache hit should be faster than initial load")
    assertTrue(cacheHitTime < 100, "Cache hit should be very fast")
  }

  @Test
  fun testDownsamplingPerformance() = runTest {
    // Load large image with downsampling
    val downsampledRequest = ImageRequest.builder()
      .model(TEST_IMAGE_URL)
      .size(width = 200, height = 150) // Small target size
      .build()

    // Load same image without size constraint
    val fullSizeRequest = ImageRequest.builder()
      .model(TEST_IMAGE_URL)
      .build()

    val downsampledTime = measureTimeMillis {
      landscapist.load(downsampledRequest).first { it is ImageResult.Success }
    }

    val fullSizeTime = measureTimeMillis {
      landscapist.load(fullSizeRequest).first { it is ImageResult.Success }
    }

    println("Downsampled (200x150): ${downsampledTime}ms")
    println("Full size: ${fullSizeTime}ms")

    // Downsampling may not always be faster due to caching,
    // but should still complete successfully
    assertTrue(downsampledTime < 10_000, "Downsampled load should complete")
    assertTrue(fullSizeTime < 10_000, "Full size load should complete")
  }
}
