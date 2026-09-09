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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.builder
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Android entry point has to be able to turn the disk cache off.
 *
 * `noDiskCache()` was added to the common builder only, and Android's own builder always created a
 * `DiskLruCache`, so the documented way to keep images off disk could not be called on the platform
 * the documentation is written for.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NoDiskCacheDeviceTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var server: LocalImageServer

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve(PATH, ImageFixtures.photo(200, 200))
    cacheDirectory().deleteRecursively()
  }

  @After
  fun stop() = server.close()

  private fun cacheDirectory() = File(context.cacheDir, "landscapist_cache")

  private fun loadOnce(loader: Landscapist) {
    val result = runBlocking {
      loader.load(
        ImageRequest.builder().model(server.url(PATH)).size(100, 100).build(),
      ).first { it is ImageResult.Success || it is ImageResult.Failure }
    }
    assertTrue("the image did not load: $result", result is ImageResult.Success)
  }

  @Test
  fun theAndroidBuilderCanTurnTheDiskCacheOff() {
    loadOnce(Landscapist.builder(context).noDiskCache().build())

    val onDisk = cacheDirectory().walkTopDown().filter { it.isFile }.toList()
    assertTrue("noDiskCache still wrote ${onDisk.map { it.name }}", onDisk.isEmpty())
  }

  @Test
  fun theAndroidBuilderStillWritesToDiskByDefault() {
    // The control, so an empty directory above cannot come from the loader failing to cache at all.
    loadOnce(Landscapist.builder(context).build())
    Thread.sleep(WRITE_SETTLE_MS)

    val onDisk = cacheDirectory().walkTopDown().filter { it.isFile }.toList()
    assertTrue("the default builder wrote nothing to disk", onDisk.isNotEmpty())
  }

  @Test
  fun theSameLoaderStillReadsFromMemory() {
    val loader = Landscapist.builder(context).noDiskCache().build()
    loadOnce(loader)
    loadOnce(loader)

    assertEquals("the second load went back to the network", 1, server.hitCount(PATH))
  }

  private companion object {
    const val PATH = "/no-disk-cache.jpg"
    const val WRITE_SETTLE_MS = 1_000L
  }
}
