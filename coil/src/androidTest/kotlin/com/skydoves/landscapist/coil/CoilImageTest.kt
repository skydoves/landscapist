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

package com.skydoves.landscapist.coil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.test.platform.app.InstrumentationRegistry
import coil.EventListener
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.Options
import coil.fetch.Fetcher
import coil.request.ImageRequest
import com.skydoves.landscapist.ShimmerParams
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@LargeTest
@RunWith(AndroidJUnit4::class)
internal class CoilImageTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun requestSuccess_withoutComposables() {
    composeTestRule.setContent {
      CoilImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_COIL),
        contentScale = ContentScale.Crop
      )
    }

    composeTestRule.onNodeWithTag(TAG_COIL)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)
  }

  @Test
  fun requestSuccess_shimmer_withoutComposables() {
    composeTestRule.setContent {
      CoilImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_COIL),
        shimmerParams = ShimmerParams(
          baseColor = Color.DarkGray,
          highlightColor = Color.LightGray
        ),
        contentScale = ContentScale.Crop
      )
    }

    composeTestRule.onNodeWithTag(TAG_COIL)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)
  }

  @Test
  fun requestSuccess_withLoadingComposables() {
    composeTestRule.setContent {
      CoilImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_COIL),
        contentScale = ContentScale.Crop,
        loading = { Box(modifier = Modifier) }
      )
    }

    composeTestRule.onNodeWithTag(TAG_COIL)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)
  }

  @ExperimentalCoilApi
  @Test
  fun requestSuccess_imageLoader_local() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val latch = CountDownLatch(1)

    val eventListener = object : EventListener {
      val startCalled = AtomicInteger()

      override fun fetchStart(request: ImageRequest, fetcher: Fetcher<*>, options: Options) {
        startCalled.incrementAndGet()
      }
    }

    val imageLoader = ImageLoader.Builder(context)
      .eventListener(eventListener)
      .build()

    composeTestRule.setContent {
      CompositionLocalProvider(LocalCoilImageLoader provides imageLoader) {
        CoilImage(
          imageModel = IMAGE,
          modifier = Modifier.size(128.dp, 128.dp),
          contentScale = ContentScale.Crop,
          loading = { Box(modifier = Modifier) }
        )
      }
    }

    // wait for the onRequestCompleted to release the latch
    latch.await(5, TimeUnit.SECONDS)

    assertThat(eventListener.startCalled.get(), `is`(1))
  }

  @Test
  fun requestSuccess_withSuccessComposables() {
    val state = ArrayList<CoilImageState>()

    composeTestRule.setContent {
      CoilImage(
        imageModel = IMAGE,
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_COIL),
        contentScale = ContentScale.Crop,
        success = {
          state.add(it)
          assertThat(it.drawable, `is`(CoreMatchers.notNullValue()))
        },
        loading = {
          Box(modifier = Modifier.testTag(TAG_PROGRESS))
        }
      )
    }

    composeTestRule.onNodeWithTag(TAG_COIL)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)

    composeTestRule.runOnIdle {
      assertThat(state.size, `is`(1))
      assertThat(
        state[0],
        CoreMatchers.instanceOf(CoilImageState.Success::class.java)
      )
    }
  }

  @Test
  fun requestFailure_withFailureComposables() {
    val state = ArrayList<CoilImageState>()

    composeTestRule.setContent {
      CoilImage(
        imageModel = "",
        modifier = Modifier
          .size(128.dp, 128.dp)
          .testTag(TAG_COIL),
        contentScale = ContentScale.Crop,
        failure = {
          Box(modifier = Modifier.testTag(TAG_ERROR))
          state.add(it)
        }
      )
    }

    composeTestRule.onNodeWithTag(TAG_COIL)
      .assertIsDisplayed()
      .assertWidthIsAtLeast(128.dp)
      .assertHeightIsAtLeast(128.dp)

    composeTestRule.runOnIdle {
      assertThat(state.size, `is`(1))
      assertThat(
        state[0],
        CoreMatchers.instanceOf(CoilImageState.Failure::class.java)
      )
    }
  }
}
