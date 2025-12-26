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

/**
 * Represents a cancellable image loading request.
 */
public interface Disposable {
  /**
   * The unique identifier for this request.
   */
  public val id: String

  /**
   * Returns true if the request has been disposed (cancelled or completed).
   */
  public val isDisposed: Boolean

  /**
   * Cancels the ongoing image loading request.
   * If the request has already completed or been cancelled, this is a no-op.
   */
  public fun dispose()

  /**
   * Suspends until the request completes or is cancelled.
   */
  public suspend fun await()
}

/**
 * Internal implementation of [Disposable] backed by a coroutine [Job].
 */
internal class JobDisposable(
  override val id: String,
  private val job: Job,
) : Disposable {

  override val isDisposed: Boolean
    get() = !job.isActive

  override fun dispose() {
    job.cancel()
  }

  override suspend fun await() {
    job.join()
  }
}

/**
 * A no-op [Disposable] for requests that complete synchronously (e.g., memory cache hits).
 */
internal object ImmediateDisposable : Disposable {
  override val id: String = "immediate"
  override val isDisposed: Boolean = true
  override fun dispose() {}
  override suspend fun await() {}
}
