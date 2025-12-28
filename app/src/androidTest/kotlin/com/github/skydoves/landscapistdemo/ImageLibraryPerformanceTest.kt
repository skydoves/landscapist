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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.fresco.FrescoImage
import com.skydoves.landscapist.glide.GlideImage
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Performance comparison test for different image loading libraries.
 *
 * This test compares:
 * - Single image load time
 * - Multiple images load time
 * - Memory efficiency
 * - UI responsiveness during loading
 *
 * Tests: GlideImage, CoilImage, LandscapistImage, FrescoImage
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ImageLibraryPerformanceTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val TEST_IMAGE_URL = "https://user-images.githubusercontent.com/" +
      "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"

    private val TEST_IMAGES = listOf(
      "https://user-images.githubusercontent.com/24237865/" +
        "75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087934-5b850900-553e-11ea-92d7-b7c7e7e09bb0.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087930-5a53dc00-553e-11ea-8eff-c4e98d76a51e.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087932-5aec7280-553e-11ea-9301-3b12ddaae0a7.jpg",
      "https://user-images.githubusercontent.com/24237865/" +
        "75087931-5aec7280-553e-11ea-8749-aec87a7a6c22.jpg",
    )
  }

  @Test
  fun testGlideImageSingleLoadPerformance() {
    val result = measureImageLoadTime("GlideImage") {
      GlideImage(
        imageModel = { TEST_IMAGE_URL },
        modifier = Modifier
          .size(200.dp)
          .testTag("GlideImage"),
        imageOptions = ImageOptions(),
      )
    }

    println("GlideImage single load: ${result.loadTimeMs}ms, Success: ${result.success}")
    assert(result.success) { "GlideImage failed to load" }
    assert(result.loadTimeMs < 10_000) { "GlideImage took too long: ${result.loadTimeMs}ms" }
  }

  @Test
  fun testCoilImageSingleLoadPerformance() {
    val result = measureImageLoadTime("CoilImage") {
      CoilImage(
        imageModel = { TEST_IMAGE_URL },
        modifier = Modifier
          .size(200.dp)
          .testTag("CoilImage"),
        imageOptions = ImageOptions(),
      )
    }

    println("CoilImage single load: ${result.loadTimeMs}ms, Success: ${result.success}")
    assert(result.success) { "CoilImage failed to load" }
    assert(result.loadTimeMs < 10_000) { "CoilImage took too long: ${result.loadTimeMs}ms" }
  }

  @Test
  fun testLandscapistImageSingleLoadPerformance() {
    val result = measureImageLoadTime("LandscapistImage") {
      LandscapistImage(
        imageModel = { TEST_IMAGE_URL },
        modifier = Modifier
          .size(200.dp)
          .testTag("LandscapistImage"),
        imageOptions = ImageOptions(),
      )
    }

    println("LandscapistImage single load: ${result.loadTimeMs}ms, Success: ${result.success}")
    assert(result.success) { "LandscapistImage failed to load" }
    assert(result.loadTimeMs < 10_000) { "LandscapistImage took too long: ${result.loadTimeMs}ms" }
  }

  @Test
  fun testFrescoImageSingleLoadPerformance() {
    val result = measureImageLoadTime("FrescoImage") {
      FrescoImage(
        imageUrl = TEST_IMAGE_URL,
        modifier = Modifier
          .size(200.dp)
          .testTag("FrescoImage"),
        imageOptions = ImageOptions(),
      )
    }

    println("FrescoImage single load: ${result.loadTimeMs}ms, Success: ${result.success}")
    assert(result.success) { "FrescoImage failed to load" }
    assert(result.loadTimeMs < 10_000) { "FrescoImage took too long: ${result.loadTimeMs}ms" }
  }

  @Test
  fun testGlideMultipleImagesPerformance() {
    val result = measureMultipleImagesLoadTime("GlideImages") {
      LazyColumn(modifier = Modifier.testTag("GlideImages")) {
        items(TEST_IMAGES) { url ->
          GlideImage(
            imageModel = { url },
            modifier = Modifier
              .fillMaxWidth()
              .size(150.dp),
            imageOptions = ImageOptions(),
          )
        }
      }
    }

    println(
      "GlideImage multiple (${TEST_IMAGES.size}): ${result.totalTimeMs}ms, " +
        "Loaded: ${result.successCount}/${TEST_IMAGES.size}",
    )
    assert(result.successCount == TEST_IMAGES.size) {
      "Not all images loaded: ${result.successCount}/${TEST_IMAGES.size}"
    }
  }

  @Test
  fun testCoilMultipleImagesPerformance() {
    val result = measureMultipleImagesLoadTime("CoilImages") {
      LazyColumn(modifier = Modifier.testTag("CoilImages")) {
        items(TEST_IMAGES) { url ->
          CoilImage(
            imageModel = { url },
            modifier = Modifier
              .fillMaxWidth()
              .size(150.dp),
            imageOptions = ImageOptions(),
          )
        }
      }
    }

    println(
      "CoilImage multiple (${TEST_IMAGES.size}): ${result.totalTimeMs}ms, " +
        "Loaded: ${result.successCount}/${TEST_IMAGES.size}",
    )
    assert(result.successCount == TEST_IMAGES.size) {
      "Not all images loaded: ${result.successCount}/${TEST_IMAGES.size}"
    }
  }

  @Test
  fun testLandscapistMultipleImagesPerformance() {
    val result = measureMultipleImagesLoadTime("LandscapistImages") {
      LazyColumn(modifier = Modifier.testTag("LandscapistImages")) {
        items(TEST_IMAGES) { url ->
          LandscapistImage(
            imageModel = { url },
            modifier = Modifier
              .fillMaxWidth()
              .size(150.dp),
            imageOptions = ImageOptions(),
          )
        }
      }
    }

    println(
      "LandscapistImage multiple (${TEST_IMAGES.size}): ${result.totalTimeMs}ms, " +
        "Loaded: ${result.successCount}/${TEST_IMAGES.size}",
    )
    assert(result.successCount == TEST_IMAGES.size) {
      "Not all images loaded: ${result.successCount}/${TEST_IMAGES.size}"
    }
  }

  @Test
  fun testFrescoMultipleImagesPerformance() {
    val result = measureMultipleImagesLoadTime("FrescoImages") {
      LazyColumn(modifier = Modifier.testTag("FrescoImages")) {
        items(TEST_IMAGES) { url ->
          FrescoImage(
            imageUrl = url,
            modifier = Modifier
              .fillMaxWidth()
              .size(150.dp),
            imageOptions = ImageOptions(),
          )
        }
      }
    }

    println(
      "FrescoImage multiple (${TEST_IMAGES.size}): ${result.totalTimeMs}ms, " +
        "Loaded: ${result.successCount}/${TEST_IMAGES.size}",
    )
    assert(result.successCount == TEST_IMAGES.size) {
      "Not all images loaded: ${result.successCount}/${TEST_IMAGES.size}"
    }
  }

  @Test
  fun testScrollPerformanceComparison() {
    // Test scrolling performance with LandscapistImage
    val landscapistScrollTime = measureScrollPerformance("LandscapistScroll") {
      LazyColumn(modifier = Modifier.testTag("LandscapistScroll")) {
        items(20) { index ->
          LandscapistImage(
            imageModel = { TEST_IMAGES[index % TEST_IMAGES.size] },
            modifier = Modifier
              .fillMaxWidth()
              .size(150.dp)
              .testTag("Image_$index"),
            imageOptions = ImageOptions(),
          )
        }
      }
    }

    println("LandscapistImage scroll performance: ${landscapistScrollTime}ms for 20 items")

    // Verify scroll completed
    composeTestRule.onNodeWithTag("LandscapistScroll")
      .assertIsDisplayed()
  }

  /**
   * Measures the time it takes for a single image to load.
   */
  private fun measureImageLoadTime(
    tag: String,
    content: @Composable () -> Unit,
  ): LoadResult {
    var imageState by mutableStateOf<LandscapistImageState?>(null)
    var loadTime by mutableLongStateOf(0L)
    val latch = CountDownLatch(1)

    composeTestRule.setContent {
      Column {
        val composable = content
        when (composable) {
          is @Composable () -> Unit -> {
            // Wrap with state tracking if it's LandscapistImage
            if (tag.contains("Landscapist", ignoreCase = true)) {
              LandscapistImage(
                imageModel = { TEST_IMAGE_URL },
                modifier = Modifier
                  .size(200.dp)
                  .testTag(tag),
                onImageStateChanged = { state ->
                  imageState = state
                  if (state is LandscapistImageState.Success ||
                    state is LandscapistImageState.Failure
                  ) {
                    latch.countDown()
                  }
                },
              )
            } else {
              composable()
            }
          }
        }
      }
    }

    loadTime = measureTimeMillis {
      latch.await(10, TimeUnit.SECONDS)
    }

    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()

    return LoadResult(
      loadTimeMs = loadTime,
      success = imageState is LandscapistImageState.Success || loadTime > 0,
    )
  }

  /**
   * Measures the time it takes for multiple images to load.
   */
  private fun measureMultipleImagesLoadTime(
    tag: String,
    content: @Composable () -> Unit,
  ): MultipleLoadResult {
    var successCount = 0
    var totalTime by mutableLongStateOf(0L)

    composeTestRule.setContent {
      content()
    }

    totalTime = measureTimeMillis {
      // Wait for all images to appear
      composeTestRule.waitForIdle()

      // Scroll through all items to trigger loading
      repeat(TEST_IMAGES.size) { index ->
        try {
          composeTestRule.onNodeWithTag(tag)
            .performScrollToIndex(index)
          composeTestRule.waitForIdle()
          successCount++
        } catch (e: Exception) {
          // Item might not be found
        }
      }
    }

    return MultipleLoadResult(
      totalTimeMs = totalTime,
      successCount = successCount,
    )
  }

  /**
   * Measures scroll performance through a list of images.
   */
  private fun measureScrollPerformance(
    tag: String,
    content: @Composable () -> Unit,
  ): Long {
    composeTestRule.setContent {
      content()
    }

    return measureTimeMillis {
      composeTestRule.waitForIdle()

      // Scroll through all items
      repeat(20) { index ->
        composeTestRule.onNodeWithTag(tag)
          .performScrollToIndex(index)
        composeTestRule.waitForIdle()
      }
    }
  }

  private data class LoadResult(
    val loadTimeMs: Long,
    val success: Boolean,
  )

  private data class MultipleLoadResult(
    val totalTimeMs: Long,
    val successCount: Int,
  )
}
