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
package com.skydoves.landscapist.core.scheduler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrioritizedRequestTest {

  @Test
  fun `DecodePriority IMMEDIATE has highest value`() {
    assertTrue(DecodePriority.IMMEDIATE.value > DecodePriority.HIGH.value)
    assertTrue(DecodePriority.IMMEDIATE.value > DecodePriority.NORMAL.value)
    assertTrue(DecodePriority.IMMEDIATE.value > DecodePriority.LOW.value)
    assertTrue(DecodePriority.IMMEDIATE.value > DecodePriority.BACKGROUND.value)
  }

  @Test
  fun `DecodePriority values are in correct order`() {
    assertTrue(DecodePriority.IMMEDIATE.value > DecodePriority.HIGH.value)
    assertTrue(DecodePriority.HIGH.value > DecodePriority.NORMAL.value)
    assertTrue(DecodePriority.NORMAL.value > DecodePriority.LOW.value)
    assertTrue(DecodePriority.LOW.value > DecodePriority.BACKGROUND.value)
  }

  @Test
  fun `DecodePriority BACKGROUND has lowest value`() {
    assertEquals(0, DecodePriority.BACKGROUND.value)
  }

  @Test
  fun `PrioritizedRequest stores properties correctly`() {
    val request = PrioritizedRequest(
      id = "test-id",
      priority = DecodePriority.HIGH,
      createdAt = 12345L,
      tag = "test-tag",
      decoder = { "result" },
    )

    assertEquals("test-id", request.id)
    assertEquals(DecodePriority.HIGH, request.priority)
    assertEquals(12345L, request.createdAt)
    assertEquals("test-tag", request.tag)
  }

  @Test
  fun `PrioritizedRequest tag can be null`() {
    val request = PrioritizedRequest(
      id = "test-id",
      priority = DecodePriority.NORMAL,
      createdAt = 0L,
      tag = null,
      decoder = { },
    )

    assertEquals(null, request.tag)
  }

  @Test
  fun `higher priority request compares as less than lower priority`() {
    val highPriority = PrioritizedRequest(
      id = "high",
      priority = DecodePriority.HIGH,
      createdAt = 100L,
      decoder = { },
    )
    val lowPriority = PrioritizedRequest(
      id = "low",
      priority = DecodePriority.LOW,
      createdAt = 100L,
      decoder = { },
    )

    // Higher priority should come first (negative comparison)
    assertTrue(highPriority < lowPriority)
  }

  @Test
  fun `same priority requests are ordered by creation time FIFO`() {
    val older = PrioritizedRequest(
      id = "older",
      priority = DecodePriority.NORMAL,
      createdAt = 100L,
      decoder = { },
    )
    val newer = PrioritizedRequest(
      id = "newer",
      priority = DecodePriority.NORMAL,
      createdAt = 200L,
      decoder = { },
    )

    // Older request should come first (FIFO)
    assertTrue(older < newer)
  }

  @Test
  fun `IMMEDIATE priority is always first`() {
    val immediate = PrioritizedRequest(
      id = "immediate",
      priority = DecodePriority.IMMEDIATE,
      createdAt = 1000L, // Created later
      decoder = { },
    )
    val normal = PrioritizedRequest(
      id = "normal",
      priority = DecodePriority.NORMAL,
      createdAt = 1L, // Created earlier
      decoder = { },
    )

    assertTrue(immediate < normal)
  }

  @Test
  fun `equal requests compare as zero`() {
    val request1 = PrioritizedRequest(
      id = "test",
      priority = DecodePriority.NORMAL,
      createdAt = 100L,
      decoder = { },
    )
    val request2 = PrioritizedRequest(
      id = "test",
      priority = DecodePriority.NORMAL,
      createdAt = 100L,
      decoder = { },
    )

    assertEquals(0, request1.compareTo(request2))
  }

  @Test
  fun `sorting list orders by priority then time`() {
    val requests = listOf(
      PrioritizedRequest("low", DecodePriority.LOW, 100L) { },
      PrioritizedRequest("high1", DecodePriority.HIGH, 200L) { },
      PrioritizedRequest("normal", DecodePriority.NORMAL, 50L) { },
      PrioritizedRequest("high2", DecodePriority.HIGH, 100L) { },
      PrioritizedRequest("immediate", DecodePriority.IMMEDIATE, 300L) { },
    )

    val sorted = requests.sorted()

    assertEquals("immediate", sorted[0].id)
    assertEquals("high2", sorted[1].id) // Same priority, older first
    assertEquals("high1", sorted[2].id)
    assertEquals("normal", sorted[3].id)
    assertEquals("low", sorted[4].id)
  }

  @Test
  fun `copy creates new request with updated priority`() {
    val original = PrioritizedRequest(
      id = "test",
      priority = DecodePriority.LOW,
      createdAt = 100L,
      tag = "tag",
      decoder = { "result" },
    )

    val boosted = original.copy(priority = DecodePriority.HIGH)

    assertEquals("test", boosted.id)
    assertEquals(DecodePriority.HIGH, boosted.priority)
    assertEquals(100L, boosted.createdAt)
    assertEquals("tag", boosted.tag)
  }
}
