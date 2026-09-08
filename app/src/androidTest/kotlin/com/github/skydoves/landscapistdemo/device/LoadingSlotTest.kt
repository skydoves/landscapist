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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.image.LandscapistImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An image with a caller supplied loading slot still has to finish loading.
 *
 * `LandscapistImageTest.testImageWithFixedSize` never leaves its loading state, on this branch and
 * on main alike. That test goes to the internet, so it could not say whether the slot or the
 * network was at fault. This one serves the bytes locally, so there is nothing left to blame.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LoadingSlotTest {

  @get:Rule val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  @Before fun start() {
    server = LocalImageServer()
    server.serve("/slot.jpg", ImageFixtures.photo(360, 360))
  }

  @After fun stop() = server.close()

  private fun states(withLoadingSlot: Boolean): List<String> {
    val seen = CopyOnWriteArrayList<String>()
    val loader = Landscapist.builder().noDiskCache().build()
    compose.setContent {
      LandscapistImage(
        imageModel = { server.url("/slot.jpg") },
        landscapist = loader,
        modifier = Modifier.size(128.dp),
        imageOptions = ImageOptions(contentScale = ContentScale.Crop),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
        onImageStateChanged = { seen += it::class.simpleName ?: "?" },
        loading = if (withLoadingSlot) {
          { Box(Modifier.size(128.dp)) }
        } else {
          null
        },
      )
    }
    val deadline = System.currentTimeMillis() + 10_000
    fun settled() = seen.any { it == "Success" || it == "Failure" }
    while (System.currentTimeMillis() < deadline && !settled()) {
      compose.mainClock.advanceTimeByFrame()
      Thread.sleep(20)
    }
    return seen.toList()
  }

  @Test
  fun withoutALoadingSlotTheImageResolves() {
    val seen = states(withLoadingSlot = false)
    assertTrue("never resolved, saw $seen", seen.contains("Success"))
  }

  @Test
  fun withALoadingSlotTheImageStillResolves() {
    val seen = states(withLoadingSlot = true)
    assertTrue("never resolved, saw $seen", seen.contains("Success"))
  }
}
