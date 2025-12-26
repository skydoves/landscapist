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
package com.skydoves.landscapist.core

import com.skydoves.landscapist.core.event.EventListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LandscapistConfigTest {

  @Test
  fun `default config has expected values`() {
    val config = LandscapistConfig()

    assertEquals(LandscapistConfig.DEFAULT_MEMORY_CACHE_SIZE, config.memoryCacheSize)
    assertEquals(LandscapistConfig.DEFAULT_DISK_CACHE_SIZE, config.diskCacheSize)
    assertEquals(LandscapistConfig.DEFAULT_MAX_BITMAP_SIZE, config.maxBitmapSize)
    assertNull(config.memoryCache)
    assertNull(config.diskCache)
    assertFalse(config.allowRgb565)
    assertTrue(config.weakReferencesEnabled)
    assertTrue(config.interceptors.isEmpty())
  }

  @Test
  fun `default memory cache size is 64MB`() {
    assertEquals(64 * 1024 * 1024L, LandscapistConfig.DEFAULT_MEMORY_CACHE_SIZE)
  }

  @Test
  fun `default disk cache size is 100MB`() {
    assertEquals(100 * 1024 * 1024L, LandscapistConfig.DEFAULT_DISK_CACHE_SIZE)
  }

  @Test
  fun `default max bitmap size is 4096`() {
    assertEquals(4096, LandscapistConfig.DEFAULT_MAX_BITMAP_SIZE)
  }

  @Test
  fun `custom config values are preserved`() {
    val config = LandscapistConfig(
      memoryCacheSize = 128 * 1024 * 1024L,
      diskCacheSize = 200 * 1024 * 1024L,
      maxBitmapSize = 2048,
      allowRgb565 = true,
      weakReferencesEnabled = false,
    )

    assertEquals(128 * 1024 * 1024L, config.memoryCacheSize)
    assertEquals(200 * 1024 * 1024L, config.diskCacheSize)
    assertEquals(2048, config.maxBitmapSize)
    assertTrue(config.allowRgb565)
    assertFalse(config.weakReferencesEnabled)
  }

  @Test
  fun `NetworkConfig default values`() {
    val config = NetworkConfig()

    assertEquals(10.seconds, config.connectTimeout)
    assertEquals(30.seconds, config.readTimeout)
    assertEquals("Landscapist/1.0", config.userAgent)
    assertTrue(config.defaultHeaders.isEmpty())
    assertTrue(config.followRedirects)
    assertEquals(5, config.maxRedirects)
  }

  @Test
  fun `NetworkConfig custom values`() {
    val config = NetworkConfig(
      connectTimeout = 5.seconds,
      readTimeout = 15.seconds,
      userAgent = "CustomAgent/2.0",
      defaultHeaders = mapOf("Authorization" to "Bearer token"),
      followRedirects = false,
      maxRedirects = 3,
    )

    assertEquals(5.seconds, config.connectTimeout)
    assertEquals(15.seconds, config.readTimeout)
    assertEquals("CustomAgent/2.0", config.userAgent)
    assertEquals("Bearer token", config.defaultHeaders["Authorization"])
    assertFalse(config.followRedirects)
    assertEquals(3, config.maxRedirects)
  }

  @Test
  fun `BitmapConfig default values`() {
    val config = BitmapConfig()

    assertEquals(DownsamplingStrategy.AUTO, config.strategy)
    assertEquals(LandscapistConfig.DEFAULT_MAX_BITMAP_SIZE, config.maxBitmapSize)
    assertFalse(config.allowRgb565)
    assertFalse(config.enableProgressiveDecoding)
    assertTrue(config.allowHardware)
  }

  @Test
  fun `BitmapConfig custom values`() {
    val config = BitmapConfig(
      strategy = DownsamplingStrategy.FIT,
      maxBitmapSize = 2048,
      allowRgb565 = true,
      enableProgressiveDecoding = true,
      allowHardware = false,
    )

    assertEquals(DownsamplingStrategy.FIT, config.strategy)
    assertEquals(2048, config.maxBitmapSize)
    assertTrue(config.allowRgb565)
    assertTrue(config.enableProgressiveDecoding)
    assertFalse(config.allowHardware)
  }

  @Test
  fun `LandscapistConfig uses default NetworkConfig`() {
    val config = LandscapistConfig()

    assertEquals(10.seconds, config.networkConfig.connectTimeout)
    assertEquals(30.seconds, config.networkConfig.readTimeout)
  }

  @Test
  fun `LandscapistConfig uses default BitmapConfig`() {
    val config = LandscapistConfig()

    assertEquals(DownsamplingStrategy.AUTO, config.bitmapConfig.strategy)
  }

  @Test
  fun `LandscapistConfig uses default EventListener factory`() {
    val config = LandscapistConfig()

    assertEquals(EventListener.NONE_FACTORY, config.eventListenerFactory)
  }

  @Test
  fun `config is a data class and supports copy`() {
    val original = LandscapistConfig(memoryCacheSize = 64 * 1024 * 1024L)
    val modified = original.copy(memoryCacheSize = 128 * 1024 * 1024L)

    assertEquals(64 * 1024 * 1024L, original.memoryCacheSize)
    assertEquals(128 * 1024 * 1024L, modified.memoryCacheSize)
  }
}
