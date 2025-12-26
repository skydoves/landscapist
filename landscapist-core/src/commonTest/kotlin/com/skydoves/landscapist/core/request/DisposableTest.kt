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

class DisposableTest {

  @Test
  fun `JobDisposable id matches constructor`() = runTest {
    val job = launch { delay(1000) }
    val disposable = JobDisposable("test-id", job)

    assertEquals("test-id", disposable.id)

    job.cancel()
  }

  @Test
  fun `JobDisposable isDisposed returns false for active job`() = runTest {
    val job = launch { delay(10000) }
    val disposable = JobDisposable("test", job)

    assertFalse(disposable.isDisposed)

    job.cancel()
  }

  @Test
  fun `JobDisposable isDisposed returns true for cancelled job`() = runTest {
    val job = launch { delay(10000) }
    val disposable = JobDisposable("test", job)

    job.cancel()

    assertTrue(disposable.isDisposed)
  }

  @Test
  fun `JobDisposable isDisposed returns true for completed job`() = runTest {
    val job = Job()
    val disposable = JobDisposable("test", job)

    job.complete()

    assertTrue(disposable.isDisposed)
  }

  @Test
  fun `JobDisposable dispose cancels job`() = runTest {
    val job = launch { delay(10000) }
    val disposable = JobDisposable("test", job)

    disposable.dispose()

    assertTrue(job.isCancelled)
    assertTrue(disposable.isDisposed)
  }

  @Test
  fun `JobDisposable await waits for job completion`() = runTest {
    var completed = false
    val job = launch {
      delay(100)
      completed = true
    }
    val disposable = JobDisposable("test", job)

    disposable.await()

    assertTrue(completed)
    assertTrue(disposable.isDisposed)
  }

  @Test
  fun `ImmediateDisposable id is immediate`() {
    assertEquals("immediate", ImmediateDisposable.id)
  }

  @Test
  fun `ImmediateDisposable isDisposed is always true`() {
    assertTrue(ImmediateDisposable.isDisposed)
  }

  @Test
  fun `ImmediateDisposable dispose is no-op`() {
    // Should not throw
    ImmediateDisposable.dispose()
    assertTrue(ImmediateDisposable.isDisposed)
  }

  @Test
  fun `ImmediateDisposable await completes immediately`() = runTest {
    // Should not block
    ImmediateDisposable.await()
    assertTrue(true)
  }
}
