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
package com.skydoves.landscapist.core.request

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestManagerTest {

  @Test
  fun `initial state has no active requests`() {
    val manager = RequestManager()

    assertEquals(0, manager.activeCount)
    assertTrue(manager.getActiveRequestIds().isEmpty())
  }

  @Test
  fun `register adds request and returns disposable`() = runTest {
    val manager = RequestManager()
    val job = launch { delay(1000) }

    val disposable = manager.register(job, tag = "test", model = "image.jpg")

    assertTrue(disposable.id.startsWith("request-"))
    assertEquals(1, manager.activeCount)

    job.cancel()
  }

  @Test
  fun `cancel cancels request by id`() = runTest {
    val manager = RequestManager()
    val job = launch { delay(10000) }

    val disposable = manager.register(job, tag = null, model = null)
    val cancelled = manager.cancel(disposable.id)

    assertTrue(cancelled)
    assertTrue(job.isCancelled)
  }

  @Test
  fun `cancel returns false for non-existent id`() {
    val manager = RequestManager()

    val cancelled = manager.cancel("non-existent-id")

    assertFalse(cancelled)
  }

  @Test
  fun `cancelByTag cancels all requests with tag`() = runTest {
    val manager = RequestManager()
    val job1 = launch { delay(10000) }
    val job2 = launch { delay(10000) }
    val job3 = launch { delay(10000) }

    manager.register(job1, tag = "group-a", model = null)
    manager.register(job2, tag = "group-a", model = null)
    manager.register(job3, tag = "group-b", model = null)

    val count = manager.cancelByTag("group-a")

    assertEquals(2, count)
    assertTrue(job1.isCancelled)
    assertTrue(job2.isCancelled)
    assertFalse(job3.isCancelled)

    job3.cancel()
  }

  @Test
  fun `cancelByModel cancels all requests for model`() = runTest {
    val manager = RequestManager()
    val job1 = launch { delay(10000) }
    val job2 = launch { delay(10000) }

    manager.register(job1, tag = null, model = "image1.jpg")
    manager.register(job2, tag = null, model = "image2.jpg")

    val count = manager.cancelByModel("image1.jpg")

    assertEquals(1, count)
    assertTrue(job1.isCancelled)
    assertFalse(job2.isCancelled)

    job2.cancel()
  }

  @Test
  fun `cancelAll cancels all active requests`() = runTest {
    val manager = RequestManager()
    val job1 = launch { delay(10000) }
    val job2 = launch { delay(10000) }
    val job3 = launch { delay(10000) }

    manager.register(job1, tag = "a", model = null)
    manager.register(job2, tag = "b", model = null)
    manager.register(job3, tag = "c", model = null)

    val count = manager.cancelAll()

    assertEquals(3, count)
    assertTrue(job1.isCancelled)
    assertTrue(job2.isCancelled)
    assertTrue(job3.isCancelled)
  }

  @Test
  fun `hasActiveRequest returns true for active model`() = runTest {
    val manager = RequestManager()
    val job = launch { delay(10000) }

    manager.register(job, tag = null, model = "image.jpg")

    assertTrue(manager.hasActiveRequest("image.jpg"))
    assertFalse(manager.hasActiveRequest("other.jpg"))

    job.cancel()
  }

  @Test
  fun `getActiveRequestIds returns all ids`() = runTest {
    val manager = RequestManager()
    val job1 = launch { delay(10000) }
    val job2 = launch { delay(10000) }

    val disposable1 = manager.register(job1, tag = null, model = null)
    val disposable2 = manager.register(job2, tag = null, model = null)

    val ids = manager.getActiveRequestIds()

    assertEquals(2, ids.size)
    assertTrue(ids.contains(disposable1.id))
    assertTrue(ids.contains(disposable2.id))

    job1.cancel()
    job2.cancel()
  }

  @Test
  fun `getActiveRequestIdsByTag returns matching ids`() = runTest {
    val manager = RequestManager()
    val job1 = launch { delay(10000) }
    val job2 = launch { delay(10000) }
    val job3 = launch { delay(10000) }

    val d1 = manager.register(job1, tag = "group-x", model = null)
    manager.register(job2, tag = "group-y", model = null)
    val d3 = manager.register(job3, tag = "group-x", model = null)

    val ids = manager.getActiveRequestIdsByTag("group-x")

    assertEquals(2, ids.size)
    assertTrue(ids.contains(d1.id))
    assertTrue(ids.contains(d3.id))

    job1.cancel()
    job2.cancel()
    job3.cancel()
  }

  @Test
  fun `completed job is automatically removed`() = runTest {
    val manager = RequestManager()
    val job = Job()

    manager.register(job, tag = null, model = null)
    assertEquals(1, manager.activeCount)

    job.complete()
    // Wait for completion handler
    job.join()

    assertEquals(0, manager.activeCount)
  }

  @Test
  fun `request ids are unique`() = runTest {
    val manager = RequestManager()
    val ids = mutableSetOf<String>()

    repeat(100) {
      val job = launch { delay(10000) }
      val disposable = manager.register(job, tag = null, model = null)
      ids.add(disposable.id)
      job.cancel()
    }

    assertEquals(100, ids.size)
  }
}
