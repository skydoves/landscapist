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
package com.github.skydoves.landscapistdemo.measure

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.github.skydoves.landscapistdemo.measure.DeviceMeasure.formatBytes
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.image.LandscapistImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import coil3.request.ImageRequest as CoilRequest

@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollComparisonTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer
  private val items = 60
  private val rowHeight = 90

  @Before
  fun start() {
    server = LocalImageServer()
    repeat(items) { server.serve("/row-$it.jpg", ImageFixtures.photo(360, 270)) }
  }

  @After
  fun stop() = server.close()

  private fun urls() = List(items) { server.url("/row-$it.jpg") }

  @Test
  fun scrollLandscapist() {
    val loader = Landscapist.builder().noDiskCache().build()
    val urls = urls()
    measureScroll("landscapist") {
      LazyColumn {
        items(urls) { url ->
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            modifier = Modifier.fillMaxWidth().height(rowHeight.dp),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
  }

  @Test
  fun scrollCoil() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val loader = ImageLoader.Builder(context)
      .memoryCache { MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).build() }
      .diskCache(null)
      .build()
    val urls = urls()
    measureScroll("coil") {
      LazyColumn {
        items(urls) { url ->
          AsyncImage(
            model = CoilRequest.Builder(context).data(url).build(),
            imageLoader = loader,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(rowHeight.dp),
          )
        }
      }
    }
  }

  /** Scrolled first to fill the caches, so the measured pass is over images already held. */
  private fun measureScroll(label: String, content: @Composable () -> Unit) {
    compose.setContent { content() }
    compose.waitForIdle()

    repeat(WARM_SWIPES) {
      compose.onRoot().performTouchInput { swipeUp() }
      compose.waitForIdle()
    }
    DeviceMeasure.settle()

    val allocated = DeviceMeasure.allocatedDuring {
      repeat(MEASURED_SWIPES) {
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
      }
    }

    DeviceMeasure.report(
      "scroll $MEASURED_SWIPES swipes over $items rows, $label",
      "allocated ${allocated.formatBytes()}",
    )
    assertTrue("the allocation counter is not available on this device", allocated > 0)
  }

  private companion object {
    const val WARM_SWIPES = 6
    const val MEASURED_SWIPES = 8
  }
}
