/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
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

package com.skydoves.landscapist.glide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.LargeTest
import com.bumptech.glide.Glide
import com.skydoves.landscapist.CircularReveal
import com.skydoves.landscapist.ShimmerParams
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(JUnit4::class)
internal class GlideImageTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun requestSuccess_withoutComposables() {
    composeTestRule.setContent {
      GlideImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_GLIDE),
        contentScale = ContentScale.Crop
      )
    }

    composeTestRule.onNodeWithTag(TAG_GLIDE)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)
  }

  @Test
  fun requestSuccess_shimmer_withoutComposables() {
    composeTestRule.setContent {
      GlideImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_GLIDE),
        circularReveal = CircularReveal(),
        shimmerParams = ShimmerParams(
          baseColor = Color.DarkGray,
          highlightColor = Color.LightGray
        ),
        contentScale = ContentScale.Crop
      )
    }

    composeTestRule.onNodeWithTag(TAG_GLIDE)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)
  }

  @Test
  fun requestSuccess_requestBuilder_local() {
    val latch = CountDownLatch(1)
    composeTestRule.setContent {
      val glide = Glide.with(LocalView.current)
        .asDrawable()
        .thumbnail(0.1f)
        .addListener(
          TestRequestListener {
            latch.countDown()
          }
        )

      CompositionLocalProvider(LocalGlideRequestBuilder provides glide) {
        GlideImage(
          imageModel = IMAGE,
          modifier = Modifier
            .size(128.dp, 128.dp)
            .testTag(TAG_GLIDE),
          contentScale = ContentScale.Crop
        )
      }

      // wait for the onRequestCompleted to release the latch
      latch.await(5, TimeUnit.SECONDS)

      assertThat(latch.count, `is`(1))
    }

    composeTestRule.onNodeWithTag(TAG_GLIDE)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)
  }

  @Test
  fun requestSuccess_withLoadingComposables() {
    composeTestRule.setContent {
      GlideImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_GLIDE),
        contentScale = ContentScale.Crop,
        loading = {
          Box(modifier = Modifier.testTag(TAG_PROGRESS))
          composeTestRule.onNodeWithTag(TAG_PROGRESS)
            .assertIsDisplayed()
        }
      )
    }

    composeTestRule.onNodeWithTag(TAG_GLIDE)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)
  }

  @Test
  fun requestSuccess_withSuccessComposables() {
    val state = ArrayList<GlideImageState>()

    composeTestRule.setContent {
      GlideImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_GLIDE),
        contentScale = ContentScale.Crop,
        success = {
          state.add(it)
          assertThat(it.drawable, `is`(notNullValue()))
        },
        loading = {
          Box(modifier = Modifier.testTag(TAG_PROGRESS))

          composeTestRule.onNodeWithTag(TAG_PROGRESS)
            .assertIsDisplayed()
        }
      )
    }

    composeTestRule.onNodeWithTag(TAG_GLIDE)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)

    composeTestRule.runOnIdle {
      assertThat(state.size, `is`(1))
      assertThat(state[0], instanceOf(GlideImageState.Success::class.java))
    }
  }

  @Test
  fun requestFailure_withFailureComposables() {
    val state = ArrayList<GlideImageState>()

    composeTestRule.setContent {
      GlideImage(
        imageModel = "",
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_GLIDE),
        contentScale = ContentScale.Crop,
        failure = {
          Box(modifier = Modifier.testTag(TAG_ERROR))
          state.add(it)
        }
      )
    }

    composeTestRule.onNodeWithTag(TAG_ERROR)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)

    composeTestRule.runOnIdle {
      assertThat(state.size, `is`(1))
      assertThat(state[0], instanceOf(GlideImageState.Failure::class.java))
    }
  }
}
