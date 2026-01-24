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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Performance comparison test between LandscapistImage and direct Coil3.
 *
 * This test reproduces the investigation from GitHub issue #830:
 * https://github.com/skydoves/landscapist/issues/830
 *
 * The user observed that cached images load at 0ms with direct Coil3
 * but ~50ms with LandscapistImage.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LandscapistVsCoil3PerformanceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    // Test image URL - 600x600 JPG as mentioned in the issue
    private const val TEST_IMAGE_URL = "https://user-images.githubusercontent.com/" +
      "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"

    private val TEST_IMAGES = listOf(
      "https://user-images.githubusercontent.com/24237865/" +
        "75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
    )
  }

  /**
   * Test: Compare cached image load performance - the main issue
   * This measures time from composition start to success callback
   */
  @Test
  fun compareCachedLoadPerformance() {
    var directCoil3Time by mutableLongStateOf(0L)
    var landscapistTime by mutableLongStateOf(0L)
    var directCoil3Done by mutableStateOf(false)
    var landscapistDone by mutableStateOf(false)

    val latch = CountDownLatch(2)

    composeTestRule.setContent {
      Column {
        // Test Direct Coil3
        DirectCoil3Test(
          url = TEST_IMAGE_URL,
          onComplete = { time ->
            directCoil3Time = time
            directCoil3Done = true
            latch.countDown()
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Test LandscapistImage
        LandscapistTest(
          url = TEST_IMAGE_URL,
          onComplete = { time ->
            landscapistTime = time
            landscapistDone = true
            latch.countDown()
          },
        )
      }
    }

    // Wait for both tests to complete
    latch.await(30, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    // Print results
    println("\n" + "=".repeat(80))
    println("PERFORMANCE COMPARISON: LandscapistImage vs Direct Coil3")
    println("=".repeat(80))
    println()
    println(String.format("%-25s | %s", "Library", "Time to Success"))
    println("-".repeat(50))
    println(String.format("%-25s | %dms", "Direct Coil3", directCoil3Time))
    println(String.format("%-25s | %dms", "LandscapistImage", landscapistTime))
    println("-".repeat(50))

    val overhead = landscapistTime - directCoil3Time
    val ratio = if (directCoil3Time > 0) landscapistTime.toDouble() / directCoil3Time else 0.0

    println(String.format("Overhead: %dms (%.2fx)", overhead, ratio))
    println("=".repeat(80))

    // Assertions
    assert(directCoil3Done) { "Direct Coil3 test did not complete" }
    assert(landscapistDone) { "Landscapist test did not complete" }
  }

  /**
   * Test: Multiple iterations for more accurate measurements
   */
  @Test
  fun compareMultipleImagesPerformance() {
    val directCoil3Times = mutableListOf<Long>()
    val landscapistTimes = mutableListOf<Long>()
    val completedCount = mutableStateOf(0)

    val latch = CountDownLatch(TEST_IMAGES.size * 2)

    composeTestRule.setContent {
      Column {
        TEST_IMAGES.forEachIndexed { index, url ->
          Row {
            // Direct Coil3
            DirectCoil3Test(
              url = url,
              tag = "direct-$index",
              onComplete = { time ->
                synchronized(directCoil3Times) {
                  directCoil3Times.add(time)
                }
                latch.countDown()
              },
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Landscapist
            LandscapistTest(
              url = url,
              tag = "landscapist-$index",
              onComplete = { time ->
                synchronized(landscapistTimes) {
                  landscapistTimes.add(time)
                }
                latch.countDown()
              },
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }

    // Wait for all tests to complete
    latch.await(60, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    // Calculate averages
    val avgDirectCoil3 = directCoil3Times.average()
    val avgLandscapist = landscapistTimes.average()

    // Print results
    println("\n" + "=".repeat(80))
    println("MULTIPLE IMAGES PERFORMANCE (${TEST_IMAGES.size} images)")
    println("=".repeat(80))
    println()
    println("Individual Results:")
    println("-".repeat(50))
    TEST_IMAGES.indices.forEach { i ->
      val directTime = directCoil3Times.getOrNull(i) ?: 0L
      val landscapistTime = landscapistTimes.getOrNull(i) ?: 0L
      println(
        String.format(
          "  Image %d: Direct=%dms, Landscapist=%dms",
          i + 1,
          directTime,
          landscapistTime,
        ),
      )
    }
    println()
    println("Averages:")
    println("-".repeat(50))
    println(String.format("%-25s | %.1fms", "Direct Coil3 (avg)", avgDirectCoil3))
    println(String.format("%-25s | %.1fms", "Landscapist (avg)", avgLandscapist))
    println("-".repeat(50))
    println(String.format("Average Overhead: %.1fms", avgLandscapist - avgDirectCoil3))
    println("=".repeat(80))
  }

  /**
   * Test: Detailed time-to-first-bitmap measurement
   */
  @Test
  fun measureTimeToFirstBitmap() {
    var directStartTime by mutableLongStateOf(0L)
    var directSuccessTime by mutableLongStateOf(0L)
    var directBitmapInfo by mutableStateOf("")

    var landscapistStartTime by mutableLongStateOf(0L)
    var landscapistSuccessTime by mutableLongStateOf(0L)
    var landscapistBitmapInfo by mutableStateOf("")

    val latch = CountDownLatch(2)

    composeTestRule.setContent {
      Column {
        // Direct Coil3 with detailed timing
        DirectCoil3DetailedTest(
          url = TEST_IMAGE_URL,
          onStart = { directStartTime = System.currentTimeMillis() },
          onSuccess = { time, info ->
            directSuccessTime = time
            directBitmapInfo = info
            latch.countDown()
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Landscapist with detailed timing
        LandscapistDetailedTest(
          url = TEST_IMAGE_URL,
          onStart = { landscapistStartTime = System.currentTimeMillis() },
          onSuccess = { time, info ->
            landscapistSuccessTime = time
            landscapistBitmapInfo = info
            latch.countDown()
          },
        )
      }
    }

    latch.await(30, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    val directElapsed = directSuccessTime - directStartTime
    val landscapistElapsed = landscapistSuccessTime - landscapistStartTime

    println("\n" + "=".repeat(80))
    println("DETAILED TIME TO FIRST BITMAP")
    println("=".repeat(80))
    println()
    println("Direct Coil3:")
    println("  Start time:      $directStartTime")
    println("  Success time:    $directSuccessTime")
    println("  Elapsed:         ${directElapsed}ms")
    println("  Bitmap info:     $directBitmapInfo")
    println()
    println("LandscapistImage:")
    println("  Start time:      $landscapistStartTime")
    println("  Success time:    $landscapistSuccessTime")
    println("  Elapsed:         ${landscapistElapsed}ms")
    println("  Bitmap info:     $landscapistBitmapInfo")
    println()
    println("-".repeat(50))
    println("Difference: ${landscapistElapsed - directElapsed}ms overhead")
    println("=".repeat(80))
  }

  /**
   * Test: First load performance (network)
   */
  @Test
  fun compareFirstLoadPerformance() {
    var directCoil3Time by mutableLongStateOf(0L)
    var landscapistTime by mutableLongStateOf(0L)

    val latch = CountDownLatch(2)

    composeTestRule.setContent {
      Column {
        // Direct Coil3 (disable cache for first-load test)
        DirectCoil3Test(
          url = TEST_IMAGE_URL,
          cacheEnabled = true, // Cache should be warm from previous runs
          onComplete = { time ->
            directCoil3Time = time
            latch.countDown()
          },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // LandscapistImage
        LandscapistTest(
          url = TEST_IMAGE_URL,
          onComplete = { time ->
            landscapistTime = time
            latch.countDown()
          },
        )
      }
    }

    latch.await(30, TimeUnit.SECONDS)
    composeTestRule.waitForIdle()

    println("\n" + "=".repeat(80))
    println("FIRST LOAD PERFORMANCE")
    println("=".repeat(80))
    println()
    println(String.format("%-25s | %dms", "Direct Coil3", directCoil3Time))
    println(String.format("%-25s | %dms", "LandscapistImage", landscapistTime))
    println("-".repeat(50))
    println(String.format("Overhead: %dms", landscapistTime - directCoil3Time))
    println("=".repeat(80))
  }

  @Composable
  private fun DirectCoil3Test(
    url: String,
    tag: String = "direct-coil3",
    cacheEnabled: Boolean = true,
    onComplete: (Long) -> Unit,
  ) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    var startTime by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    val cachePolicy = if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(cachePolicy)
        .diskCachePolicy(cachePolicy)
        .build(),
      imageLoader = imageLoader,
    )

    val state by painter.state.collectAsState()

    LaunchedEffect(Unit) {
      startTime = System.currentTimeMillis()
    }

    LaunchedEffect(state) {
      if (state is AsyncImagePainter.State.Success && !completed) {
        completed = true
        val elapsed = System.currentTimeMillis() - startTime
        onComplete(elapsed)
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(200.dp).testTag(tag),
      contentScale = ContentScale.Crop,
    )
  }

  @Composable
  private fun LandscapistTest(
    url: String,
    tag: String = "landscapist",
    onComplete: (Long) -> Unit,
  ) {
    var startTime by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      startTime = System.currentTimeMillis()
    }

    LandscapistImage(
      imageModel = { url },
      modifier = Modifier.size(200.dp).testTag(tag),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !completed) {
          completed = true
          val elapsed = System.currentTimeMillis() - startTime
          onComplete(elapsed)
        }
      },
    )
  }

  @Composable
  private fun DirectCoil3DetailedTest(
    url: String,
    onStart: () -> Unit,
    onSuccess: (Long, String) -> Unit,
  ) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader(context) }
    var completed by remember { mutableStateOf(false) }

    val painter = rememberAsyncImagePainter(
      model = ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build(),
      imageLoader = imageLoader,
    )

    val state by painter.state.collectAsState()

    LaunchedEffect(Unit) {
      onStart()
    }

    LaunchedEffect(state) {
      if (state is AsyncImagePainter.State.Success && !completed) {
        completed = true
        val successState = state as AsyncImagePainter.State.Success
        val info = "${successState.result.image.width}x${successState.result.image.height}"
        onSuccess(System.currentTimeMillis(), info)
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(200.dp).testTag("direct-detailed"),
      contentScale = ContentScale.Crop,
    )
  }

  @Composable
  private fun LandscapistDetailedTest(
    url: String,
    onStart: () -> Unit,
    onSuccess: (Long, String) -> Unit,
  ) {
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
      onStart()
    }

    LandscapistImage(
      imageModel = { url },
      modifier = Modifier.size(200.dp).testTag("landscapist-detailed"),
      imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      onImageStateChanged = { state ->
        if (state is LandscapistImageState.Success && !completed) {
          completed = true
          val info = "${state.originalWidth}x${state.originalHeight}"
          onSuccess(System.currentTimeMillis(), info)
        }
      },
    )
  }
}
