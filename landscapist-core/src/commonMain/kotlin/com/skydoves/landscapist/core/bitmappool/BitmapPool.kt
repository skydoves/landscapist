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
package com.skydoves.landscapist.core.bitmappool

/**
 * Configuration for bitmap formats used in pooling.
 */
public enum class BitmapFormat {
  /** Standard 32-bit ARGB format with alpha channel. */
  ARGB_8888,

  /** 16-bit RGB format without alpha, uses 50% less memory. */
  RGB_565,

  /** Hardware-accelerated bitmap (Android API 26+). Not poolable. */
  HARDWARE,
}

/**
 * A pool for reusable bitmap objects to reduce memory allocation and GC pressure.
 *
 * Bitmap pooling is a critical optimization for image-heavy applications because:
 * 1. Bitmap allocation is expensive (large contiguous memory blocks)
 * 2. Frequent allocations cause GC pauses that lead to UI jank
 * 3. Reusing bitmaps eliminates both allocation and deallocation overhead
 *
 * Usage:
 * ```kotlin
 * val pool = createBitmapPool(maxSize = 32 * 1024 * 1024) // 32MB pool
 *
 * // Get a reusable bitmap (may be recycled or newly allocated)
 * val bitmap = pool.get(width = 1080, height = 1920, format = BitmapFormat.ARGB_8888)
 *
 * // When done with the bitmap, return it to the pool
 * pool.put(bitmap)
 * ```
 *
 * Thread-safety: All implementations must be thread-safe.
 */
public interface BitmapPool {
  /**
   * The maximum size of the pool in bytes.
   */
  public val maxSize: Long

  /**
   * The current size of bitmaps stored in the pool in bytes.
   */
  public val currentSize: Long

  /**
   * Gets a bitmap from the pool that matches the requested dimensions and format.
   *
   * If a suitable bitmap is found in the pool, it will be returned after being cleared.
   * If no suitable bitmap is available, a new bitmap will be allocated.
   *
   * @param width The required width in pixels.
   * @param height The required height in pixels.
   * @param format The required bitmap format.
   * @return A bitmap matching the requirements, either from the pool or newly allocated.
   */
  public fun get(width: Int, height: Int, format: BitmapFormat): Any?

  /**
   * Gets a bitmap from the pool that can be reused with [BitmapFactory.Options.inBitmap].
   *
   * This is more flexible than [get] as it finds any bitmap that can hold the requested
   * dimensions, not just exact matches. On Android API 19+, any bitmap with enough bytes
   * can be reused.
   *
   * @param width The required width in pixels.
   * @param height The required height in pixels.
   * @param format The required bitmap format.
   * @return A reusable bitmap, or null if none available.
   */
  public fun getReusable(width: Int, height: Int, format: BitmapFormat): Any?

  /**
   * Returns a bitmap to the pool for future reuse.
   *
   * The bitmap must not be used after calling this method. The pool may recycle
   * the bitmap immediately if it's not suitable for reuse (e.g., hardware bitmap,
   * already recycled, or pool is full).
   *
   * @param bitmap The bitmap to return to the pool.
   * @return true if the bitmap was added to the pool, false if it was rejected.
   */
  public fun put(bitmap: Any): Boolean

  /**
   * Removes all bitmaps from the pool and recycles them.
   */
  public fun clear()

  /**
   * Trims the pool to the specified size by removing least-recently-used bitmaps.
   *
   * @param targetSize The target size in bytes. Pass 0 to clear the pool.
   */
  public fun trimToSize(targetSize: Long)

  /**
   * Returns statistics about pool usage for debugging and monitoring.
   */
  public fun getStats(): BitmapPoolStats
}

/**
 * Statistics about bitmap pool usage.
 */
public data class BitmapPoolStats(
  /** Number of successful cache hits (reused bitmaps). */
  val hits: Long,
  /** Number of cache misses (new allocations). */
  val misses: Long,
  /** Number of bitmaps currently in the pool. */
  val pooledCount: Int,
  /** Total bytes of bitmaps in the pool. */
  val pooledBytes: Long,
  /** Number of bitmaps rejected when putting (e.g., pool full). */
  val evictions: Long,
) {
  /** Hit rate as a percentage (0.0 to 1.0). */
  val hitRate: Float
    get() = if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f
}

/**
 * Creates a platform-specific bitmap pool.
 *
 * @param maxSize Maximum size of the pool in bytes. Default is 1/8 of available memory.
 * @return A [BitmapPool] implementation for the current platform.
 */
public expect fun createBitmapPool(maxSize: Long = 0): BitmapPool

/**
 * Global bitmap pool instance for shared use.
 * Lazily initialized with default size (1/8 of max memory).
 */
public object GlobalBitmapPool {
  private var pool: BitmapPool? = null

  /**
   * Gets the global bitmap pool, creating it if necessary.
   */
  public fun get(): BitmapPool {
    pool?.let { return it }

    // Simple lazy initialization - acceptable race condition
    // as createBitmapPool() returns equivalent instances
    val newPool = createBitmapPool()
    pool = newPool
    return newPool
  }

  /**
   * Initializes the global pool with a custom size.
   * Should be called early in app lifecycle (e.g., Application.onCreate).
   *
   * @param maxSize Maximum pool size in bytes.
   */
  public fun initialize(maxSize: Long) {
    val oldPool = pool
    pool = createBitmapPool(maxSize)
    oldPool?.clear()
  }

  /**
   * Clears the global pool, releasing all cached bitmaps.
   */
  public fun clear() {
    pool?.clear()
  }

  /**
   * Trims the global pool to reduce memory usage.
   *
   * @param fraction Fraction of current size to keep (0.0 to 1.0).
   */
  public fun trim(fraction: Float) {
    pool?.let { p ->
      val targetSize = (p.currentSize * fraction).toLong()
      p.trimToSize(targetSize)
    }
  }
}
