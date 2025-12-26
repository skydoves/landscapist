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

/**
 * Represents events that occur during image loading.
 */
public sealed class ImageLoadEvent {

  /** The image model being loaded. */
  public abstract val model: Any?

  /** Timestamp when the event occurred (milliseconds). */
  public abstract val timestamp: Long

  /**
   * Event emitted when an image load request starts.
   */
  public data class Started(
    override val model: Any?,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when an image is found in memory cache.
   */
  public data class MemoryCacheHit(
    override val model: Any?,
    val sizeBytes: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when an image is found in disk cache.
   */
  public data class DiskCacheHit(
    override val model: Any?,
    val sizeBytes: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when a network fetch starts.
   */
  public data class FetchStarted(
    override val model: Any?,
    val url: String?,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when a network fetch completes.
   */
  public data class FetchCompleted(
    override val model: Any?,
    val sizeBytes: Long,
    val durationMs: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when decoding starts.
   */
  public data class DecodeStarted(
    override val model: Any?,
    val inputSizeBytes: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when decoding completes.
   */
  public data class DecodeCompleted(
    override val model: Any?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when an image load completes successfully.
   */
  public data class Success(
    override val model: Any?,
    val dataSource: DataSource,
    val totalDurationMs: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when an image load fails.
   */
  public data class Failure(
    override val model: Any?,
    val throwable: Throwable,
    val totalDurationMs: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when an image load is cancelled.
   */
  public data class Cancelled(
    override val model: Any?,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when an image is written to memory cache.
   */
  public data class MemoryCacheWrite(
    override val model: Any?,
    val sizeBytes: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()

  /**
   * Event emitted when an image is written to disk cache.
   */
  public data class DiskCacheWrite(
    override val model: Any?,
    val sizeBytes: Long,
    override val timestamp: Long = currentTimeMillis(),
  ) : ImageLoadEvent()
}

/**
 * Returns the current time in milliseconds.
 */
internal expect fun currentTimeMillis(): Long
