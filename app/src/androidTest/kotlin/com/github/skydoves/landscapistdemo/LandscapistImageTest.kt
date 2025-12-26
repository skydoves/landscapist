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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
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

@LargeTest
@RunWith(AndroidJUnit4::class)
class LandscapistImageTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val TAG_IMAGE = "LandscapistImageTag"
    private const val TAG_LOADING = "LoadingTag"
    private const val IMAGE_URL = "https://user-images.githubusercontent.com/" +
      "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"
  }

  @Test
  fun testImageWithFixedSize() {
    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    val stateHistory = mutableListOf<String>()
    val latch = CountDownLatch(1)

    composeTestRule.setContent {
      LandscapistImage(
        imageModel = { IMAGE_URL },
        modifier = Modifier
          .size(128.dp)
          .testTag(TAG_IMAGE),
        imageOptions = ImageOptions(contentScale = ContentScale.Crop),
        onImageStateChanged = { state ->
          stateHistory.add(state::class.simpleName ?: "Unknown")
          imageState = state
          if (state is LandscapistImageState.Success || state is LandscapistImageState.Failure) {
            latch.countDown()
          }
        },
        loading = {
          Box(modifier = Modifier.testTag(TAG_LOADING))
        },
      )
    }

    // Wait for image to load (max 10 seconds)
    val loaded = latch.await(10, TimeUnit.SECONDS)
    assert(loaded) {
      "Image failed to load within timeout. " +
        "State history: $stateHistory, Final state: ${imageState::class.simpleName}"
    }

    composeTestRule.onNodeWithTag(TAG_IMAGE)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)

    composeTestRule.runOnIdle {
      assert(imageState is LandscapistImageState.Success) {
        "Expected Success state but got: $imageState"
      }
    }
  }

  @Test
  fun testImageWithFillMaxWidth() {
    var imageState by mutableStateOf<LandscapistImageState>(LandscapistImageState.None)
    val latch = CountDownLatch(1)

    composeTestRule.setContent {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        LandscapistImage(
          imageModel = { IMAGE_URL },
          modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_IMAGE),
          imageOptions = ImageOptions(contentScale = ContentScale.FillWidth),
          onImageStateChanged = { state ->
            imageState = state
            if (state is LandscapistImageState.Success || state is LandscapistImageState.Failure) {
              latch.countDown()
            }
          },
          loading = {
            Box(modifier = Modifier.testTag(TAG_LOADING))
          },
        )
      }
    }

    // Wait for image to load (max 10 seconds)
    val loaded = latch.await(10, TimeUnit.SECONDS)
    assert(loaded) { "Image failed to load within timeout - this indicates ANR or freeze!" }

    composeTestRule.onNodeWithTag(TAG_IMAGE)
      .assertIsDisplayed()

    composeTestRule.runOnIdle {
      assert(imageState is LandscapistImageState.Success) {
        "Expected Success state but got: $imageState"
      }
    }
  }
}
