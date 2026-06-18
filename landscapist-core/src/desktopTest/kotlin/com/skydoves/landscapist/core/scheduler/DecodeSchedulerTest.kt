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

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DecodeSchedulerTest {

  @Test
  fun neverExceedsConfiguredParallelism() = runBlocking {
    val parallelism = 3
    val scheduler = DecodeScheduler(parallelism = parallelism)
    val concurrent = atomic(0)
    val peak = atomic(0)

    val tasks = (1..24).map { index ->
      scheduler.schedule(id = "task_$index") {
        val now = concurrent.incrementAndGet()
        peak.update { existing -> if (now > existing) now else existing }
        delay(30)
        concurrent.decrementAndGet()
        index
      }
    }
    tasks.awaitAll()

    assertTrue(peak.value <= parallelism, "peak concurrency ${peak.value} exceeded $parallelism")
    assertEquals(0, concurrent.value)
  }

  @Test
  fun higherPriorityWaiterRunsFirst() = runBlocking {
    val scheduler = DecodeScheduler(parallelism = 1)
    val order = Collections.synchronizedList(mutableListOf<String>())
    val gate = CompletableDeferred<Unit>()

    // Occupy the single permit so the next two requests must queue.
    val blocker = scheduler.schedule(id = "blocker") {
      gate.await()
      order.add("blocker")
    }
    delay(50)

    val low = scheduler.schedule(id = "low", priority = DecodePriority.LOW) { order.add("low") }
    val high = scheduler.schedule(id = "high", priority = DecodePriority.HIGH) { order.add("high") }
    delay(50)

    // Release the permit; the gate should admit HIGH before LOW.
    gate.complete(Unit)
    listOf(blocker, low, high).awaitAll()

    assertEquals(listOf("blocker", "high", "low"), order.toList())
  }

  @Test
  fun cancelDropsAWaitingRequest() = runBlocking {
    val scheduler = DecodeScheduler(parallelism = 1)
    val gate = CompletableDeferred<Unit>()
    var waitingRan = false

    val blocker = scheduler.schedule(id = "blocker") { gate.await() }
    delay(50)
    val waiting = scheduler.schedule(id = "waiting") { waitingRan = true }
    delay(20)

    val cancelled = scheduler.cancel("waiting")
    gate.complete(Unit)
    blocker.await()
    delay(30)

    assertTrue(cancelled, "cancel should report the waiting request was found")
    assertTrue(waiting.isCancelled)
    assertFalse(waitingRan, "a cancelled waiting request must not execute")
  }

  @Test
  fun cancelByTagCancelsMatchingRequests() = runBlocking {
    val scheduler = DecodeScheduler(parallelism = 1)
    val gate = CompletableDeferred<Unit>()

    val blocker = scheduler.schedule(id = "blocker") { gate.await() }
    delay(50)
    val a = scheduler.schedule(id = "a", tag = "group") { 1 }
    val b = scheduler.schedule(id = "b", tag = "group") { 2 }
    val c = scheduler.schedule(id = "c", tag = "other") { 3 }
    delay(20)

    val count = scheduler.cancelByTag("group")
    gate.complete(Unit)
    blocker.await()
    c.await()

    assertEquals(2, count)
    assertTrue(a.isCancelled && b.isCancelled)
    assertFalse(c.isCancelled)
  }
}
