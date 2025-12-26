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

import com.skydoves.landscapist.core.event.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * A scheduler for prioritized decode operations.
 *
 * The scheduler maintains a priority queue of decode requests and processes them
 * in order of priority. Higher priority requests are processed first, ensuring
 * that visible images load before off-screen images.
 *
 * Features:
 * - Priority-based execution (IMMEDIATE > HIGH > NORMAL > LOW > BACKGROUND)
 * - Configurable parallelism (number of concurrent decode operations)
 * - Request cancellation by ID or tag
 * - Priority boosting for requests that become visible
 *
 * Usage:
 * ```kotlin
 * val scheduler = DecodeScheduler(parallelism = 4)
 *
 * // Schedule a high-priority decode
 * val result = scheduler.schedule(
 *   id = "image_1",
 *   priority = DecodePriority.HIGH,
 * ) {
 *   decodeImage(imageData)
 * }
 *
 * // Cancel when no longer needed
 * scheduler.cancel("image_1")
 * ```
 */
public class DecodeScheduler(
  /**
   * Maximum number of concurrent decode operations.
   * Default is 4 for a good balance between throughput and memory usage.
   */
  private val parallelism: Int = DEFAULT_PARALLELISM,
  /**
   * Dispatcher for decode operations.
   */
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val semaphore = Semaphore(parallelism)
  private val mutex = Mutex()

  // Active and pending requests
  private val activeRequests = mutableMapOf<String, Deferred<*>>()
  private val pendingRequests = mutableMapOf<String, PrioritizedRequest<*>>()

  /**
   * Schedules a decode operation with the specified priority.
   *
   * @param id Unique identifier for this request.
   * @param priority The decode priority.
   * @param tag Optional tag for grouping/cancellation.
   * @param decoder The suspend function that performs the decode.
   * @return A Deferred that completes with the decode result.
   */
  public fun <T> schedule(
    id: String,
    priority: DecodePriority = DecodePriority.NORMAL,
    tag: String? = null,
    decoder: suspend () -> T,
  ): Deferred<T> {
    val request = PrioritizedRequest(
      id = id,
      priority = priority,
      createdAt = currentTimeMillis(),
      tag = tag,
      decoder = decoder,
    )

    val deferred = scope.async {
      // Add to pending
      mutex.withLock {
        pendingRequests[id] = request
      }

      // Wait for our turn based on priority
      waitForTurn(request)

      semaphore.withPermit {
        mutex.withLock {
          pendingRequests.remove(id)
        }

        try {
          decoder()
        } finally {
          mutex.withLock {
            activeRequests.remove(id)
          }
        }
      }
    }

    // Track active request
    scope.async {
      mutex.withLock {
        activeRequests[id] = deferred
      }
    }

    return deferred
  }

  /**
   * Waits until this request should be processed based on priority.
   */
  private suspend fun waitForTurn(request: PrioritizedRequest<*>) {
    // For IMMEDIATE priority, skip the queue
    if (request.priority == DecodePriority.IMMEDIATE) {
      return
    }

    // Wait until this request has highest priority among pending
    while (true) {
      val shouldProcess = mutex.withLock {
        val higherPriorityExists = pendingRequests.values.any { other ->
          other.id != request.id && other.priority.value > request.priority.value
        }
        !higherPriorityExists
      }

      if (shouldProcess) {
        break
      }

      // Yield to allow other coroutines to process
      kotlinx.coroutines.yield()
    }
  }

  /**
   * Boosts the priority of an existing request.
   *
   * Use this when an off-screen image becomes visible - boost its priority
   * so it loads before other off-screen images.
   *
   * @param id The request ID to boost.
   * @param newPriority The new priority (must be higher than current).
   */
  public suspend fun boostPriority(id: String, newPriority: DecodePriority) {
    mutex.withLock {
      val existing = pendingRequests[id] ?: return
      if (newPriority.value <= existing.priority.value) return

      // Update with new priority
      val boosted = existing.copy(priority = newPriority)
      pendingRequests[id] = boosted
    }
  }

  /**
   * Cancels a specific request.
   *
   * @param id The request ID to cancel.
   * @return true if the request was found and cancelled.
   */
  public suspend fun cancel(id: String): Boolean {
    return mutex.withLock {
      // Cancel active request
      val active = activeRequests.remove(id)
      active?.cancel()

      // Remove from pending
      val pending = pendingRequests.remove(id)

      active != null || pending != null
    }
  }

  /**
   * Cancels all requests with the specified tag.
   *
   * @param tag The tag to match.
   * @return Number of requests cancelled.
   */
  public suspend fun cancelByTag(tag: String): Int {
    return mutex.withLock {
      var count = 0

      // Cancel active requests with this tag
      val activeToRemove = pendingRequests.entries
        .filter { it.value.tag == tag }
        .mapNotNull { activeRequests[it.key] }

      for (deferred in activeToRemove) {
        deferred.cancel()
        count++
      }

      // Remove pending requests with this tag
      val idsToRemove = pendingRequests.entries
        .filter { it.value.tag == tag }
        .map { it.key }

      for (id in idsToRemove) {
        pendingRequests.remove(id)
        activeRequests.remove(id)
      }

      count + idsToRemove.size
    }
  }

  /**
   * Cancels all pending requests (active requests continue).
   */
  public suspend fun clearPending() {
    mutex.withLock {
      pendingRequests.clear()
    }
  }

  /**
   * Returns the number of pending requests in the queue.
   */
  public suspend fun pendingCount(): Int = mutex.withLock { pendingRequests.size }

  /**
   * Returns the number of currently active decode operations.
   */
  public suspend fun activeCount(): Int = mutex.withLock { activeRequests.size }

  public companion object {
    /**
     * Default parallelism for decode operations.
     * 4 is a good balance for most devices.
     */
    public const val DEFAULT_PARALLELISM: Int = 4

    /**
     * Global decode scheduler instance.
     */
    private var globalScheduler: DecodeScheduler? = null

    /**
     * Gets the global decode scheduler, creating it if necessary.
     */
    public fun global(): DecodeScheduler {
      globalScheduler?.let { return it }

      val newScheduler = DecodeScheduler()
      globalScheduler = newScheduler
      return newScheduler
    }

    /**
     * Initializes the global scheduler with custom settings.
     */
    public fun initialize(parallelism: Int = DEFAULT_PARALLELISM) {
      globalScheduler = DecodeScheduler(parallelism)
    }
  }
}
