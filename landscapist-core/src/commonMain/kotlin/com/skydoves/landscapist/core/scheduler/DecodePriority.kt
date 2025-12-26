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

/**
 * Priority levels for image decode requests.
 *
 * Higher priority requests are processed before lower priority ones,
 * ensuring that visible content loads first.
 */
public enum class DecodePriority(public val value: Int) {
  /**
   * Critical priority for images that must load immediately.
   * Use for hero images, splash screens, or user-initiated loads.
   */
  IMMEDIATE(1000),

  /**
   * High priority for currently visible images.
   * Default priority for images in the viewport.
   */
  HIGH(750),

  /**
   * Normal priority for nearby off-screen images.
   * Use for prefetching images just outside the viewport.
   */
  NORMAL(500),

  /**
   * Low priority for distant off-screen images.
   * Use for background prefetching.
   */
  LOW(250),

  /**
   * Lowest priority for non-essential images.
   * Only processed when the decode queue is empty.
   */
  BACKGROUND(0),
}

/**
 * A decode request with priority information.
 *
 * @param T The type of the decode result.
 * @property id Unique identifier for this request.
 * @property priority The decode priority.
 * @property createdAt Timestamp when the request was created.
 * @property tag Optional tag for request grouping/cancellation.
 */
public data class PrioritizedRequest<T>(
  val id: String,
  val priority: DecodePriority,
  val createdAt: Long,
  val tag: String? = null,
  val decoder: suspend () -> T,
) : Comparable<PrioritizedRequest<T>> {

  /**
   * Compare by priority (higher first), then by creation time (older first).
   */
  override fun compareTo(other: PrioritizedRequest<T>): Int {
    // Higher priority value = more important = should come first (negative comparison)
    val priorityCompare = other.priority.value.compareTo(this.priority.value)
    if (priorityCompare != 0) return priorityCompare

    // For same priority, older requests come first (FIFO within priority)
    return this.createdAt.compareTo(other.createdAt)
  }
}
