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
package com.github.skydoves.landscapistdemo.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.github.skydoves.landscapistdemo.measure.DeviceMeasure
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@LargeTest
@RunWith(AndroidJUnit4::class)
class ScreenOfImagesTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  @Before fun start() {
    server = LocalImageServer()
    repeat(24) { server.serve("/p-$it.jpg", ImageFixtures.photo(360, 360)) }
  }

  @After fun stop() = server.close()

  /** Loads [count] distinct urls with no Compose in the way. */
  private fun loaderOnly(count: Int): Int {
    val loader = Landscapist.builder().noDiskCache().build()
    return runBlocking {
      (0 until count).map { index ->
        async {
          val result = loader.load(
            ImageRequest.builder()
              .model(server.url("/p-$index.jpg"))
              .diskCachePolicy(CachePolicy.DISABLED)
              .size(120, 120)
              .build(),
          ).first { it is ImageResult.Success || it is ImageResult.Failure }
          if (result is ImageResult.Success) 1 else 0
        }
      }.awaitAll().sum()
    }
  }

  @Test fun theLoaderAloneResolvesEveryUrl() {
    assertEquals("the loader alone did not resolve every url", 20, loaderOnly(20))
  }

  /** Keyed by index, so one image reporting twice cannot stand in for one that never loaded. */
  private val succeeded: MutableSet<Int> = ConcurrentHashMap.newKeySet()

  /** Composes [count] images and reports which of them reported success within the timeout. */
  private fun composed(count: Int): String {
    val failed = AtomicInteger()
    val reasons = CopyOnWriteArrayList<String>()
    val loader = Landscapist.builder().noDiskCache().build()
    compose.setContent {
      Column {
        for (index in 0 until count) {
          LandscapistImage(
            imageModel = { server.url("/p-$index.jpg") },
            landscapist = loader,
            modifier = Modifier.size(40.dp),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
            onImageStateChanged = {
              if (it is LandscapistImageState.Success) succeeded += index
              if (it is LandscapistImageState.Failure) {
                failed.incrementAndGet()
                reasons += "index $index: " +
                  (
                    it.reason?.stackTraceToString()?.lines()?.take(14)?.joinToString(" / ")
                      ?: "null"
                    )
              }
            },
          )
        }
      }
    }
    val deadline = System.currentTimeMillis() + 20_000
    while (System.currentTimeMillis() < deadline && succeeded.size < count) {
      compose.mainClock.advanceTimeByFrame()
      Thread.sleep(20)
    }
    return "${succeeded.size} succeeded, ${failed.get()} failed, of $count :: $reasons"
  }

  @Test fun oneImageLoads() = assertAllLoad(1)

  @Test fun threeImagesLoad() = assertAllLoad(3)

  @Test fun twentyImagesOnOneScreenAllLoad() = assertAllLoad(20)

  private fun assertAllLoad(count: Int) {
    val outcome = composed(count)
    DeviceMeasure.report("composed, $count image(s)", outcome)
    val missing = (0 until count).filterNot { it in succeeded }
    assertTrue("the images at $missing never reported success: $outcome", missing.isEmpty())
  }
}
