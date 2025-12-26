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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventListenerTest {

  @Test
  fun `NONE listener does not throw`() {
    val listener = EventListener.NONE
    val event = ImageLoadEvent.Started(model = "test", timestamp = 0L)

    // Should not throw
    listener.onEvent(event)
    assertTrue(true)
  }

  @Test
  fun `NONE_FACTORY returns NONE listener`() {
    val listener = EventListener.NONE_FACTORY.create("model")

    assertEquals(EventListener.NONE, listener)
  }

  @Test
  fun `LoggingEventListener logs events`() {
    val logs = mutableListOf<String>()
    val listener = LoggingEventListener(tag = "Test") { logs.add(it) }

    listener.onEvent(ImageLoadEvent.Started(model = "image.jpg", timestamp = 0L))

    assertEquals(1, logs.size)
    assertTrue(logs[0].contains("Test"))
    assertTrue(logs[0].contains("Started"))
  }

  @Test
  fun `LoggingEventListener logs all event types`() {
    val logs = mutableListOf<String>()
    val listener = LoggingEventListener(tag = "Test") { logs.add(it) }

    val events = listOf(
      ImageLoadEvent.Started("model", 0L),
      ImageLoadEvent.MemoryCacheHit("model", 100L, 0L),
      ImageLoadEvent.DiskCacheHit("model", 200L, 0L),
      ImageLoadEvent.FetchStarted("model", "http://example.com", 0L),
      ImageLoadEvent.FetchCompleted("model", 1000L, 50L, 0L),
      ImageLoadEvent.DecodeStarted("model", 500L, 0L),
      ImageLoadEvent.DecodeCompleted("model", 100, 200, 10L, 0L),
      ImageLoadEvent.Success("model", DataSource.NETWORK, 100L, 0L),
      ImageLoadEvent.Failure("model", RuntimeException("error"), 100L, 0L),
      ImageLoadEvent.Cancelled("model", 0L),
      ImageLoadEvent.MemoryCacheWrite("model", 100L, 0L),
      ImageLoadEvent.DiskCacheWrite("model", 200L, 0L),
    )

    events.forEach { listener.onEvent(it) }

    assertEquals(12, logs.size)
  }

  @Test
  fun `LoggingEventListener factory creates listener`() {
    val logs = mutableListOf<String>()
    val factory = LoggingEventListener.factory(tag = "Factory") { logs.add(it) }

    val listener = factory.create("model")

    assertNotNull(listener)
    listener.onEvent(ImageLoadEvent.Started("model", 0L))
    assertEquals(1, logs.size)
  }

  @Test
  fun `CompositeEventListener dispatches to all listeners`() {
    val logs1 = mutableListOf<String>()
    val logs2 = mutableListOf<String>()
    val listener1 = LoggingEventListener(tag = "L1") { logs1.add(it) }
    val listener2 = LoggingEventListener(tag = "L2") { logs2.add(it) }

    val composite = CompositeEventListener(listOf(listener1, listener2))
    composite.onEvent(ImageLoadEvent.Started("model", 0L))

    assertEquals(1, logs1.size)
    assertEquals(1, logs2.size)
  }

  @Test
  fun `CompositeEventListener factory combines factories`() {
    val logs1 = mutableListOf<String>()
    val logs2 = mutableListOf<String>()
    val factory1 = LoggingEventListener.factory(tag = "L1") { logs1.add(it) }
    val factory2 = LoggingEventListener.factory(tag = "L2") { logs2.add(it) }

    val compositeFactory = CompositeEventListener.factory(factory1, factory2)
    val listener = compositeFactory.create("model")

    assertNotNull(listener)
    listener.onEvent(ImageLoadEvent.Started("model", 0L))
    assertEquals(1, logs1.size)
    assertEquals(1, logs2.size)
  }

  @Test
  fun `CompositeEventListener factory returns null for empty`() {
    val factory = EventListener.Factory { null }
    val compositeFactory = CompositeEventListener.factory(factory, factory)

    val listener = compositeFactory.create("model")

    assertNull(listener)
  }

  @Test
  fun `MetricsEventListener tracks started event`() {
    val listener = MetricsEventListener()

    listener.onEvent(ImageLoadEvent.Started("model", timestamp = 1000L))

    assertFalse(listener.wasMemoryCacheHit)
    assertFalse(listener.wasDiskCacheHit)
  }

  @Test
  fun `MetricsEventListener tracks memory cache hit`() {
    val listener = MetricsEventListener()

    listener.onEvent(ImageLoadEvent.Started("model", 0L))
    listener.onEvent(ImageLoadEvent.MemoryCacheHit("model", 100L, 0L))

    assertTrue(listener.wasMemoryCacheHit)
    assertFalse(listener.wasDiskCacheHit)
  }

  @Test
  fun `MetricsEventListener tracks disk cache hit`() {
    val listener = MetricsEventListener()

    listener.onEvent(ImageLoadEvent.Started("model", 0L))
    listener.onEvent(ImageLoadEvent.DiskCacheHit("model", 100L, 0L))

    assertFalse(listener.wasMemoryCacheHit)
    assertTrue(listener.wasDiskCacheHit)
  }

  @Test
  fun `MetricsEventListener tracks fetch duration`() {
    val listener = MetricsEventListener()

    listener.onEvent(ImageLoadEvent.FetchCompleted("model", 1000L, durationMs = 50L, 0L))

    assertEquals(50L, listener.fetchDurationMs)
  }

  @Test
  fun `MetricsEventListener tracks decode duration`() {
    val listener = MetricsEventListener()

    listener.onEvent(ImageLoadEvent.DecodeCompleted("model", 100, 200, durationMs = 25L, 0L))

    assertEquals(25L, listener.decodeDurationMs)
  }

  @Test
  fun `MetricsEventListener tracks success`() {
    val listener = MetricsEventListener()

    listener.onEvent(
      ImageLoadEvent.Success("model", DataSource.NETWORK, totalDurationMs = 100L, 0L),
    )

    assertEquals(100L, listener.totalDurationMs)
    assertEquals(DataSource.NETWORK, listener.lastDataSource)
  }

  @Test
  fun `MetricsEventListener resets on new started event`() {
    val listener = MetricsEventListener()

    listener.onEvent(ImageLoadEvent.Started("model1", 0L))
    listener.onEvent(ImageLoadEvent.MemoryCacheHit("model1", 100L, 0L))
    assertTrue(listener.wasMemoryCacheHit)

    listener.onEvent(ImageLoadEvent.Started("model2", 0L))
    assertFalse(listener.wasMemoryCacheHit)
  }
}
