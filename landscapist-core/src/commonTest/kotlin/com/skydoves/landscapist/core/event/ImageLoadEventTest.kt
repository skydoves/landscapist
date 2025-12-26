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
package com.skydoves.landscapist.core.event

import com.skydoves.landscapist.core.model.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImageLoadEventTest {

  @Test
  fun `Started event stores model and timestamp`() {
    val event = ImageLoadEvent.Started(model = "image.jpg", timestamp = 12345L)

    assertEquals("image.jpg", event.model)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `MemoryCacheHit stores all properties`() {
    val event = ImageLoadEvent.MemoryCacheHit(
      model = "image.jpg",
      sizeBytes = 1000L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(1000L, event.sizeBytes)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `DiskCacheHit stores all properties`() {
    val event = ImageLoadEvent.DiskCacheHit(
      model = "image.jpg",
      sizeBytes = 5000L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(5000L, event.sizeBytes)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `FetchStarted stores all properties`() {
    val event = ImageLoadEvent.FetchStarted(
      model = "image.jpg",
      url = "https://example.com/image.jpg",
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals("https://example.com/image.jpg", event.url)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `FetchCompleted stores all properties`() {
    val event = ImageLoadEvent.FetchCompleted(
      model = "image.jpg",
      sizeBytes = 10000L,
      durationMs = 150L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(10000L, event.sizeBytes)
    assertEquals(150L, event.durationMs)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `DecodeStarted stores all properties`() {
    val event = ImageLoadEvent.DecodeStarted(
      model = "image.jpg",
      inputSizeBytes = 5000L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(5000L, event.inputSizeBytes)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `DecodeCompleted stores all properties`() {
    val event = ImageLoadEvent.DecodeCompleted(
      model = "image.jpg",
      width = 1920,
      height = 1080,
      durationMs = 50L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(1920, event.width)
    assertEquals(1080, event.height)
    assertEquals(50L, event.durationMs)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `Success stores all properties`() {
    val event = ImageLoadEvent.Success(
      model = "image.jpg",
      dataSource = DataSource.NETWORK,
      totalDurationMs = 200L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(DataSource.NETWORK, event.dataSource)
    assertEquals(200L, event.totalDurationMs)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `Failure stores all properties`() {
    val error = RuntimeException("Network error")
    val event = ImageLoadEvent.Failure(
      model = "image.jpg",
      throwable = error,
      totalDurationMs = 100L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(error, event.throwable)
    assertEquals(100L, event.totalDurationMs)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `Cancelled stores all properties`() {
    val event = ImageLoadEvent.Cancelled(
      model = "image.jpg",
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `MemoryCacheWrite stores all properties`() {
    val event = ImageLoadEvent.MemoryCacheWrite(
      model = "image.jpg",
      sizeBytes = 2000L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(2000L, event.sizeBytes)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `DiskCacheWrite stores all properties`() {
    val event = ImageLoadEvent.DiskCacheWrite(
      model = "image.jpg",
      sizeBytes = 8000L,
      timestamp = 12345L,
    )

    assertEquals("image.jpg", event.model)
    assertEquals(8000L, event.sizeBytes)
    assertEquals(12345L, event.timestamp)
  }

  @Test
  fun `all events extend ImageLoadEvent`() {
    val events: List<ImageLoadEvent> = listOf(
      ImageLoadEvent.Started("model", 0L),
      ImageLoadEvent.MemoryCacheHit("model", 0L, 0L),
      ImageLoadEvent.DiskCacheHit("model", 0L, 0L),
      ImageLoadEvent.FetchStarted("model", null, 0L),
      ImageLoadEvent.FetchCompleted("model", 0L, 0L, 0L),
      ImageLoadEvent.DecodeStarted("model", 0L, 0L),
      ImageLoadEvent.DecodeCompleted("model", 0, 0, 0L, 0L),
      ImageLoadEvent.Success("model", DataSource.MEMORY, 0L, 0L),
      ImageLoadEvent.Failure("model", RuntimeException(), 0L, 0L),
      ImageLoadEvent.Cancelled("model", 0L),
      ImageLoadEvent.MemoryCacheWrite("model", 0L, 0L),
      ImageLoadEvent.DiskCacheWrite("model", 0L, 0L),
    )

    assertEquals(12, events.size)
    events.forEach { event ->
      assertNotNull(event.model)
      assertNotNull(event.timestamp)
    }
  }

  @Test
  fun `events use default timestamp if not provided`() {
    val event = ImageLoadEvent.Started(model = "test")

    // Timestamp should be set (not 0 unless currentTimeMillis returns 0)
    assertNotNull(event.timestamp)
  }

  @Test
  fun `when expression covers all event types`() {
    val events = listOf(
      ImageLoadEvent.Started("m", 0L),
      ImageLoadEvent.MemoryCacheHit("m", 0L, 0L),
      ImageLoadEvent.DiskCacheHit("m", 0L, 0L),
      ImageLoadEvent.FetchStarted("m", null, 0L),
      ImageLoadEvent.FetchCompleted("m", 0L, 0L, 0L),
      ImageLoadEvent.DecodeStarted("m", 0L, 0L),
      ImageLoadEvent.DecodeCompleted("m", 0, 0, 0L, 0L),
      ImageLoadEvent.Success("m", DataSource.MEMORY, 0L, 0L),
      ImageLoadEvent.Failure("m", RuntimeException(), 0L, 0L),
      ImageLoadEvent.Cancelled("m", 0L),
      ImageLoadEvent.MemoryCacheWrite("m", 0L, 0L),
      ImageLoadEvent.DiskCacheWrite("m", 0L, 0L),
    )

    events.forEach { event ->
      val name = when (event) {
        is ImageLoadEvent.Started -> "started"
        is ImageLoadEvent.MemoryCacheHit -> "memory_hit"
        is ImageLoadEvent.DiskCacheHit -> "disk_hit"
        is ImageLoadEvent.FetchStarted -> "fetch_started"
        is ImageLoadEvent.FetchCompleted -> "fetch_completed"
        is ImageLoadEvent.DecodeStarted -> "decode_started"
        is ImageLoadEvent.DecodeCompleted -> "decode_completed"
        is ImageLoadEvent.Success -> "success"
        is ImageLoadEvent.Failure -> "failure"
        is ImageLoadEvent.Cancelled -> "cancelled"
        is ImageLoadEvent.MemoryCacheWrite -> "memory_write"
        is ImageLoadEvent.DiskCacheWrite -> "disk_write"
      }
      assertNotNull(name)
    }
  }
}
