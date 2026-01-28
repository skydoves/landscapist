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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * UI tests that mirror the exact layout patterns from MainPosters.kt.
 * These tests verify that LandscapistImage works correctly in real-world layouts.
 *
 * Pattern 1: PosterItem - Modifier.size(50.dp) inside Card
 * Pattern 2: SelectedPoster - Modifier.aspectRatio(0.75f)
 * Pattern 3: GIF display - Modifier.fillMaxWidth().padding().clip()
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MainPostersLayoutTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val TAG_POSTER_ITEM = "PosterItem"
    private const val TAG_SELECTED_POSTER = "SelectedPoster"
    private const val TAG_GIF_IMAGE = "GifImage"
  }

  /**
   * Pattern 1: PosterItem - exactly like in MainPosters.kt
   * LandscapistImage with Modifier.size(50.dp) inside a Card
   */
  @Test
  fun testPosterItemPattern_fixedSize50dp() {
    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    val latch = CountDownLatch(1)

    composeTestRule.setContent {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
      ) {
        // Exactly like PosterItem in MainPosters.kt
        Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
          LandscapistImage(
            imageModel = { R.drawable.poster },
            modifier = Modifier
              .size(50.dp)
              .testTag(TAG_POSTER_ITEM),
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
            onImageStateChanged = { state ->
              imageState = state
              if (state is LandscapistImageState.Success ||
                state is LandscapistImageState.Failure
              ) {
                latch.countDown()
              }
            },
          )
        }
      }
    }

    val loaded = latch.await(5, TimeUnit.SECONDS)
    assert(loaded) { "Image failed to load. Final state: ${imageState::class.simpleName}" }

    composeTestRule.onNodeWithTag(TAG_POSTER_ITEM)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(50.dp)
      .assertHeightIsAtLeast(50.dp)

    // Verify the bounds are approximately 50dp x 50dp
    val bounds = composeTestRule.onNodeWithTag(TAG_POSTER_ITEM).getBoundsInRoot()
    val widthDp = bounds.right - bounds.left
    val heightDp = bounds.bottom - bounds.top
    assert(widthDp.value >= 49f && widthDp.value <= 51f) {
      "Expected width ~50dp but got $widthDp"
    }
    assert(heightDp.value >= 49f && heightDp.value <= 51f) {
      "Expected height ~50dp but got $heightDp"
    }

    composeTestRule.runOnIdle {
      assert(imageState is LandscapistImageState.Success) {
        "Expected Success but got ${imageState::class.simpleName}"
      }
    }
  }

  /**
   * Pattern 2: SelectedPoster - exactly like in MainPosters.kt
   * LandscapistImage with Modifier.aspectRatio(0.75f)
   */
  @Test
  fun testSelectedPosterPattern_aspectRatio() {
    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var screenWidthDp by mutableStateOf(0.dp)
    val latch = CountDownLatch(1)

    composeTestRule.setContent {
      screenWidthDp = LocalConfiguration.current.screenWidthDp.dp

      Column(
        Modifier.verticalScroll(rememberScrollState()),
      ) {
        // Exactly like SelectedPoster in MainPosters.kt
        LandscapistImage(
          imageModel = { R.drawable.poster },
          modifier = Modifier
            .aspectRatio(0.75f)
            .testTag(TAG_SELECTED_POSTER),
          onImageStateChanged = { state ->
            imageState = state
            if (state is LandscapistImageState.Success ||
              state is LandscapistImageState.Failure
            ) {
              latch.countDown()
            }
          },
        )
      }
    }

    val loaded = latch.await(5, TimeUnit.SECONDS)
    assert(loaded) { "Image failed to load. Final state: ${imageState::class.simpleName}" }

    composeTestRule.onNodeWithTag(TAG_SELECTED_POSTER)
      .assertIsDisplayed()

    // Verify aspect ratio: width / height should be approximately 0.75
    val bounds = composeTestRule.onNodeWithTag(TAG_SELECTED_POSTER).getBoundsInRoot()
    val widthDp = bounds.right - bounds.left
    val heightDp = bounds.bottom - bounds.top
    val actualRatio = widthDp.value / heightDp.value
    val expectedRatio = 0.75f

    assert(abs(actualRatio - expectedRatio) < 0.05f) {
      "Expected aspect ratio ~0.75 but got $actualRatio (width=$widthDp, height=$heightDp)"
    }

    composeTestRule.runOnIdle {
      assert(imageState is LandscapistImageState.Success) {
        "Expected Success but got ${imageState::class.simpleName}"
      }
    }
  }

  /**
   * Pattern 3: GIF display - exactly like in MainPosters.kt (PosterInformation)
   * LandscapistImage with Modifier.fillMaxWidth().padding(8.dp).clip(...)
   */
  @Test
  fun testGifPattern_fillMaxWidth() {
    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var screenWidthDp by mutableStateOf(0.dp)
    val latch = CountDownLatch(1)

    composeTestRule.setContent {
      screenWidthDp = LocalConfiguration.current.screenWidthDp.dp

      Column(
        Modifier.verticalScroll(rememberScrollState()),
      ) {
        // Exactly like the GIF display in PosterInformation (MainPosters.kt)
        LandscapistImage(
          imageModel = { R.drawable.poster },
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .testTag(TAG_GIF_IMAGE),
          onImageStateChanged = { state ->
            imageState = state
            if (state is LandscapistImageState.Success ||
              state is LandscapistImageState.Failure
            ) {
              latch.countDown()
            }
          },
        )
      }
    }

    val loaded = latch.await(5, TimeUnit.SECONDS)
    assert(loaded) { "Image failed to load. Final state: ${imageState::class.simpleName}" }

    composeTestRule.onNodeWithTag(TAG_GIF_IMAGE)
      .assertIsDisplayed()

    // Verify the image fills the width (minus padding)
    val bounds = composeTestRule.onNodeWithTag(TAG_GIF_IMAGE).getBoundsInRoot()
    val widthDp = bounds.right - bounds.left
    val expectedWidthValue = (screenWidthDp - 16.dp).value // 8.dp padding on each side

    // Allow some tolerance for density conversion
    assert(widthDp.value >= expectedWidthValue - 2f) {
      "Expected width ~${screenWidthDp - 16.dp} but got $widthDp"
    }

    composeTestRule.runOnIdle {
      assert(imageState is LandscapistImageState.Success) {
        "Expected Success but got ${imageState::class.simpleName}"
      }
    }
  }

  /**
   * Combined test: All three patterns together like in DisneyPosters
   */
  @Test
  fun testAllPatternsInScrollableColumn() {
    var posterItemState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var selectedPosterState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    var gifState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    val latch = CountDownLatch(3)

    composeTestRule.setContent {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
      ) {
        // Pattern 1: PosterItem
        Card(modifier = Modifier.padding(8.dp)) {
          LandscapistImage(
            imageModel = { R.drawable.poster },
            modifier = Modifier
              .size(50.dp)
              .testTag(TAG_POSTER_ITEM),
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
            onImageStateChanged = { state ->
              posterItemState = state
              if (state is LandscapistImageState.Success ||
                state is LandscapistImageState.Failure
              ) {
                latch.countDown()
              }
            },
          )
        }

        // Pattern 2: SelectedPoster
        LandscapistImage(
          imageModel = { R.drawable.poster },
          modifier = Modifier
            .aspectRatio(0.75f)
            .testTag(TAG_SELECTED_POSTER),
          onImageStateChanged = { state ->
            selectedPosterState = state
            if (state is LandscapistImageState.Success ||
              state is LandscapistImageState.Failure
            ) {
              latch.countDown()
            }
          },
        )

        // Pattern 3: GIF
        LandscapistImage(
          imageModel = { R.drawable.poster },
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .testTag(TAG_GIF_IMAGE),
          onImageStateChanged = { state ->
            gifState = state
            if (state is LandscapistImageState.Success ||
              state is LandscapistImageState.Failure
            ) {
              latch.countDown()
            }
          },
        )
      }
    }

    val loaded = latch.await(10, TimeUnit.SECONDS)
    assert(loaded) {
      "Not all images loaded. States: " +
        "posterItem=${posterItemState::class.simpleName}, " +
        "selectedPoster=${selectedPosterState::class.simpleName}, " +
        "gif=${gifState::class.simpleName}"
    }

    // Verify all images are displayed
    composeTestRule.onNodeWithTag(TAG_POSTER_ITEM).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TAG_SELECTED_POSTER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TAG_GIF_IMAGE).assertIsDisplayed()

    // Verify Pattern 1: Fixed size
    val posterBounds = composeTestRule.onNodeWithTag(TAG_POSTER_ITEM).getBoundsInRoot()
    val posterWidth = posterBounds.right - posterBounds.left
    assert(posterWidth.value >= 49f && posterWidth.value <= 51f) {
      "PosterItem: Expected width ~50dp but got $posterWidth"
    }

    // Verify Pattern 2: Aspect ratio
    val selectedBounds = composeTestRule.onNodeWithTag(TAG_SELECTED_POSTER).getBoundsInRoot()
    val selectedWidth = selectedBounds.right - selectedBounds.left
    val selectedHeight = selectedBounds.bottom - selectedBounds.top
    val aspectRatio = selectedWidth.value / selectedHeight.value
    assert(abs(aspectRatio - 0.75f) < 0.05f) {
      "SelectedPoster: Expected aspect ratio ~0.75 but got $aspectRatio"
    }

    // Verify Pattern 3: Fill width (should be wider than PosterItem)
    val gifBounds = composeTestRule.onNodeWithTag(TAG_GIF_IMAGE).getBoundsInRoot()
    val gifWidth = gifBounds.right - gifBounds.left
    assert(gifWidth.value > 100f) {
      "GIF: Expected wide image but got $gifWidth"
    }

    composeTestRule.runOnIdle {
      assert(posterItemState is LandscapistImageState.Success) {
        "PosterItem should be Success but got ${posterItemState::class.simpleName}"
      }
      assert(selectedPosterState is LandscapistImageState.Success) {
        "SelectedPoster should be Success but got ${selectedPosterState::class.simpleName}"
      }
      assert(gifState is LandscapistImageState.Success) {
        "GIF should be Success but got ${gifState::class.simpleName}"
      }
    }
  }
}
