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

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Job

/**
 * Manages active image loading requests and provides cancellation capabilities.
 *
 * Thread-safe implementation that tracks all ongoing requests and allows
 * cancellation by ID, tag, or all at once.
 */
public class RequestManager {

  private val lock = SynchronizedObject()
  private val activeRequests = mutableMapOf<String, RequestEntry>()
  private val requestIdCounter = atomic(0L)

  /**
   * Represents an active request entry.
   */
  private data class RequestEntry(
    val id: String,
    val job: Job,
    val tag: String?,
    val model: Any?,
  )

  /**
   * Returns the number of active requests.
   */
  public val activeCount: Int
    get() = synchronized(lock) { activeRequests.size }

  /**
   * Registers a new request and returns a [Disposable] to manage it.
   *
   * @param job The coroutine job backing this request.
   * @param tag Optional tag for grouping requests.
   * @param model The image model being loaded.
   * @return A [Disposable] that can be used to cancel the request.
   */
  internal fun register(
    job: Job,
    tag: String?,
    model: Any?,
  ): Disposable {
    val id = "request-${requestIdCounter.incrementAndGet()}"

    synchronized(lock) {
      activeRequests[id] = RequestEntry(id, job, tag, model)
    }

    // Automatically remove when job completes
    job.invokeOnCompletion {
      synchronized(lock) {
        activeRequests.remove(id)
      }
    }

    return JobDisposable(id, job)
  }

  /**
   * Cancels a request by its ID.
   *
   * @param id The request ID.
   * @return true if the request was found and cancelled.
   */
  public fun cancel(id: String): Boolean {
    val entry = synchronized(lock) { activeRequests[id] }
    return if (entry != null) {
      entry.job.cancel()
      true
    } else {
      false
    }
  }

  /**
   * Cancels all requests with the given tag.
   *
   * @param tag The tag to match.
   * @return The number of requests cancelled.
   */
  public fun cancelByTag(tag: String): Int {
    val toCancel = synchronized(lock) {
      activeRequests.values.filter { it.tag == tag }
    }
    toCancel.forEach { it.job.cancel() }
    return toCancel.size
  }

  /**
   * Cancels all requests for the given model.
   *
   * @param model The image model to match.
   * @return The number of requests cancelled.
   */
  public fun cancelByModel(model: Any): Int {
    val toCancel = synchronized(lock) {
      activeRequests.values.filter { it.model == model }
    }
    toCancel.forEach { it.job.cancel() }
    return toCancel.size
  }

  /**
   * Cancels all active requests.
   *
   * @return The number of requests cancelled.
   */
  public fun cancelAll(): Int {
    val toCancel = synchronized(lock) {
      activeRequests.values.toList()
    }
    toCancel.forEach { it.job.cancel() }
    return toCancel.size
  }

  /**
   * Checks if there's an active request for the given model.
   *
   * @param model The image model to check.
   * @return true if there's an active request for this model.
   */
  public fun hasActiveRequest(model: Any): Boolean {
    return synchronized(lock) {
      activeRequests.values.any { it.model == model }
    }
  }

  /**
   * Gets all active request IDs.
   *
   * @return A list of active request IDs.
   */
  public fun getActiveRequestIds(): List<String> {
    return synchronized(lock) {
      activeRequests.keys.toList()
    }
  }

  /**
   * Gets all active request IDs with the given tag.
   *
   * @param tag The tag to match.
   * @return A list of matching request IDs.
   */
  public fun getActiveRequestIdsByTag(tag: String): List<String> {
    return synchronized(lock) {
      activeRequests.values
        .filter { it.tag == tag }
        .map { it.id }
    }
  }
}
