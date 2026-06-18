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
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A scheduler for prioritized decode operations.
 *
 * Decode work is admitted through a priority gate that limits concurrency to [parallelism] and,
 * whenever a slot frees up, hands it to the highest-priority waiter (ties broken oldest-first).
 * The gate suspends waiters on a [CompletableDeferred] instead of busy-waiting, so queued requests
 * cost nothing while they wait.
 *
 * Features:
 * - Priority-based admission (IMMEDIATE > HIGH > NORMAL > LOW > BACKGROUND)
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
  private val gate = PriorityGate(parallelism)

  // Tracks scheduled requests (waiting or running) for cancellation by id/tag.
  private val activeLock = SynchronizedObject()
  private val active = mutableMapOf<String, ScheduledTask>()

  private class ScheduledTask(val deferred: Deferred<*>, val tag: String?)

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
    val createdAt = currentTimeMillis()
    val deferred = scope.async {
      gate.withPermit(id, priority, createdAt) {
        decoder()
      }
    }

    synchronized(activeLock) { active[id] = ScheduledTask(deferred, tag) }
    deferred.invokeOnCompletion {
      synchronized(activeLock) {
        // Only remove our own entry; a later schedule() may have reused the same id.
        if (active[id]?.deferred === deferred) active.remove(id)
      }
    }

    return deferred
  }

  /**
   * Boosts the priority of an existing request that is still waiting for a decode slot.
   *
   * Use this when an off-screen image becomes visible so it loads before other waiting images.
   *
   * @param id The request ID to boost.
   * @param newPriority The new priority (only applied if higher than the current one).
   */
  public suspend fun boostPriority(id: String, newPriority: DecodePriority) {
    gate.boost(id, newPriority)
  }

  /**
   * Cancels a specific request.
   *
   * @param id The request ID to cancel.
   * @return true if the request was found and cancelled.
   */
  public suspend fun cancel(id: String): Boolean {
    val task = synchronized(activeLock) { active.remove(id) }
    task?.deferred?.cancel()
    return task != null
  }

  /**
   * Cancels all requests with the specified tag.
   *
   * @param tag The tag to match.
   * @return Number of requests cancelled.
   */
  public suspend fun cancelByTag(tag: String): Int {
    val toCancel = synchronized(activeLock) {
      val matching = active.filterValues { it.tag == tag }
      matching.keys.forEach { active.remove(it) }
      matching.values.toList()
    }
    toCancel.forEach { it.deferred.cancel() }
    return toCancel.size
  }

  /**
   * Cancels all requests that are still waiting for a decode slot. Requests that are already
   * decoding continue to completion.
   */
  public suspend fun clearPending() {
    gate.cancelAllWaiting()
  }

  /**
   * Returns the number of requests waiting for a decode slot.
   */
  public suspend fun pendingCount(): Int = gate.waitingCount()

  /**
   * Returns the number of currently running decode operations. The value is approximate, since the
   * scheduled count and the waiting count are read under different locks.
   */
  public suspend fun activeCount(): Int {
    val scheduled = synchronized(activeLock) { active.size }
    return (scheduled - gate.waitingCount()).coerceAtLeast(0)
  }

  /**
   * A concurrency limiter that admits waiters in priority order without busy-waiting.
   *
   * A waiter that finds a free permit takes it immediately; otherwise it suspends on a
   * [CompletableDeferred] until [release] hands it a permit. Releases always go to the highest
   * priority waiter (oldest first on ties). Permit accounting runs under [NonCancellable] so a
   * cancelled waiter or decode never leaks a permit.
   */
  private class PriorityGate(permits: Int) {
    private val mutex = Mutex()
    private var available = permits
    private val waiters = mutableListOf<Waiter>()

    private class Waiter(
      val id: String,
      var priorityValue: Int,
      val createdAt: Long,
      val signal: CompletableDeferred<Unit> = CompletableDeferred(),
    ) {
      // Set under the mutex when a permit is actually handed to this waiter.
      var granted: Boolean = false
    }

    suspend fun <T> withPermit(
      id: String,
      priority: DecodePriority,
      createdAt: Long,
      block: suspend () -> T,
    ): T {
      acquire(id, priority, createdAt)
      try {
        return block()
      } finally {
        withContext(NonCancellable) { release() }
      }
    }

    private suspend fun acquire(id: String, priority: DecodePriority, createdAt: Long) {
      val waiter = mutex.withLock {
        if (available > 0) {
          available--
          return
        }
        Waiter(id, priority.value, createdAt).also { waiters.add(it) }
      }

      try {
        waiter.signal.await()
      } catch (cancellation: CancellationException) {
        withContext(NonCancellable) {
          mutex.withLock {
            // If still queued, just drop it (no permit was held). If it was already removed AND a
            // permit was granted to it, that permit will go unused, so return it.
            val stillWaiting = waiters.remove(waiter)
            if (!stillWaiting && waiter.granted) {
              available++
              grantNext()
            }
          }
        }
        throw cancellation
      }
    }

    private suspend fun release() {
      mutex.withLock {
        available++
        grantNext()
      }
    }

    /** Must be called while holding [mutex]. */
    private fun grantNext() {
      while (available > 0) {
        val next = waiters.maxWithOrNull(
          compareBy<Waiter>({ it.priorityValue }, { -it.createdAt }),
        ) ?: return
        waiters.remove(next)
        // Only consume a permit if the waiter actually takes it. A signal that was already
        // cancelled returns false, so move on to the next waiter without burning a permit.
        if (next.signal.complete(Unit)) {
          next.granted = true
          available--
          return
        }
      }
    }

    suspend fun boost(id: String, newPriority: DecodePriority) {
      mutex.withLock {
        waiters.forEach { waiter ->
          if (waiter.id == id && newPriority.value > waiter.priorityValue) {
            waiter.priorityValue = newPriority.value
          }
        }
      }
    }

    suspend fun cancelAllWaiting() {
      mutex.withLock {
        // Cancelling each signal resumes its waiter, whose own cancellation handler removes it.
        waiters.toList().forEach { it.signal.cancel() }
      }
    }

    suspend fun waitingCount(): Int = mutex.withLock { waiters.size }
  }

  public companion object {
    /**
     * Default parallelism for decode operations.
     * 4 is a good balance for most devices.
     */
    public const val DEFAULT_PARALLELISM: Int = 4

    private val globalLock = SynchronizedObject()

    /**
     * Global decode scheduler instance.
     */
    private var globalScheduler: DecodeScheduler? = null

    /**
     * Gets the global decode scheduler, creating it if necessary.
     */
    public fun global(): DecodeScheduler = synchronized(globalLock) {
      globalScheduler ?: DecodeScheduler().also { globalScheduler = it }
    }

    /**
     * Initializes the global scheduler with custom settings.
     */
    public fun initialize(parallelism: Int = DEFAULT_PARALLELISM) {
      synchronized(globalLock) {
        globalScheduler = DecodeScheduler(parallelism)
      }
    }
  }
}
