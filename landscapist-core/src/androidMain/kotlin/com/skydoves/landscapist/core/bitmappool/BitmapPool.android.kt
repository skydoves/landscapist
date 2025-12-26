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

import android.graphics.Bitmap
import android.os.Build
import java.util.NavigableMap
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Creates an Android bitmap pool.
 *
 * @param maxSize Maximum size in bytes. If 0, uses 1/8 of max heap.
 */
public actual fun createBitmapPool(maxSize: Long): BitmapPool {
  val effectiveMaxSize = if (maxSize > 0) {
    maxSize
  } else {
    val maxMemory = Runtime.getRuntime().maxMemory()
    maxMemory / 8
  }
  return AndroidBitmapPool(effectiveMaxSize)
}

/**
 * Android implementation of [BitmapPool] using a size-based strategy.
 *
 * This implementation uses a TreeMap to organize bitmaps by byte count,
 * allowing efficient lookup of reusable bitmaps. On API 19+, any bitmap
 * with sufficient byte count can be reused via inBitmap.
 *
 * Key optimizations:
 * - O(log n) lookup for reusable bitmaps
 * - LRU eviction when pool is full
 * - Thread-safe operations using synchronized blocks
 * - Immediate recycling of non-reusable bitmaps
 */
internal class AndroidBitmapPool(
  override val maxSize: Long,
) : BitmapPool {

  // Bitmaps organized by byte count for efficient lookup
  // Key: byte count, Value: list of bitmaps with that byte count
  private val bitmapsBySize: NavigableMap<Int, MutableList<PooledBitmap>> = TreeMap()

  // Track pool size
  private val _currentSize = AtomicLong(0)
  override val currentSize: Long get() = _currentSize.get()

  // Statistics
  private val _hits = AtomicLong(0)
  private val _misses = AtomicLong(0)
  private val _evictions = AtomicLong(0)

  override fun get(width: Int, height: Int, format: BitmapFormat): Any {
    val config = format.toAndroidConfig()

    // Hardware bitmaps can't be pooled
    if (config == Bitmap.Config.HARDWARE) {
      _misses.incrementAndGet()
      return Bitmap.createBitmap(width, height, config)
    }

    // Try to get a reusable bitmap
    val reusable = getReusable(width, height, format) as? Bitmap
    if (reusable != null) {
      // Reconfigure to exact dimensions if needed
      if (reusable.width != width || reusable.height != height) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          reusable.reconfigure(width, height, config)
        }
      }
      reusable.eraseColor(0) // Clear to transparent
      return reusable
    }

    // Allocate new bitmap
    _misses.incrementAndGet()
    return Bitmap.createBitmap(width, height, config)
  }

  override fun getReusable(width: Int, height: Int, format: BitmapFormat): Any? {
    val config = format.toAndroidConfig()

    // Hardware bitmaps can't be reused
    if (config == Bitmap.Config.HARDWARE) {
      return null
    }

    val requiredBytes = calculateByteCount(width, height, config)

    synchronized(bitmapsBySize) {
      // On API 19+, we can reuse any bitmap with enough bytes
      // Find the smallest bitmap that's large enough
      val entry = bitmapsBySize.ceilingEntry(requiredBytes)
      if (entry != null) {
        val bitmaps = entry.value
        if (bitmaps.isNotEmpty()) {
          val pooled = bitmaps.removeAt(bitmaps.size - 1)
          if (bitmaps.isEmpty()) {
            bitmapsBySize.remove(entry.key)
          }
          _currentSize.addAndGet(-pooled.byteCount.toLong())
          _hits.incrementAndGet()
          return pooled.bitmap
        }
      }
    }

    return null
  }

  override fun put(bitmap: Any): Boolean {
    if (bitmap !is Bitmap) return false

    // Can't pool recycled, hardware, or mutable=false bitmaps
    if (bitmap.isRecycled) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
      return false
    }
    if (!bitmap.isMutable) return false

    val byteCount = bitmap.allocationByteCount

    // Don't pool if bitmap is too large (> 25% of max size)
    if (byteCount > maxSize / 4) {
      bitmap.recycle()
      return false
    }

    synchronized(bitmapsBySize) {
      // Evict if needed to make room
      while (_currentSize.get() + byteCount > maxSize) {
        if (!evictOne()) {
          // Can't evict anything, reject this bitmap
          bitmap.recycle()
          return false
        }
      }

      // Add to pool
      val pooled = PooledBitmap(bitmap, byteCount)
      val list = bitmapsBySize.getOrPut(byteCount) { mutableListOf() }
      list.add(pooled)
      _currentSize.addAndGet(byteCount.toLong())
    }

    return true
  }

  override fun clear() {
    synchronized(bitmapsBySize) {
      for (entry in bitmapsBySize.values) {
        for (pooled in entry) {
          pooled.bitmap.recycle()
        }
      }
      bitmapsBySize.clear()
      _currentSize.set(0)
    }
  }

  override fun trimToSize(targetSize: Long) {
    synchronized(bitmapsBySize) {
      while (_currentSize.get() > targetSize) {
        if (!evictOne()) break
      }
    }
  }

  override fun getStats(): BitmapPoolStats {
    val count = synchronized(bitmapsBySize) {
      bitmapsBySize.values.sumOf { it.size }
    }
    return BitmapPoolStats(
      hits = _hits.get(),
      misses = _misses.get(),
      pooledCount = count,
      pooledBytes = _currentSize.get(),
      evictions = _evictions.get(),
    )
  }

  /**
   * Evicts the smallest bitmap from the pool.
   * Smaller bitmaps are evicted first as they're less valuable for reuse.
   *
   * @return true if a bitmap was evicted, false if pool was empty.
   */
  private fun evictOne(): Boolean {
    val firstEntry = bitmapsBySize.firstEntry() ?: return false
    val bitmaps = firstEntry.value
    if (bitmaps.isEmpty()) {
      bitmapsBySize.remove(firstEntry.key)
      return evictOne()
    }

    val pooled = bitmaps.removeAt(0)
    if (bitmaps.isEmpty()) {
      bitmapsBySize.remove(firstEntry.key)
    }

    pooled.bitmap.recycle()
    _currentSize.addAndGet(-pooled.byteCount.toLong())
    _evictions.incrementAndGet()
    return true
  }

  private fun calculateByteCount(width: Int, height: Int, config: Bitmap.Config): Int {
    val bytesPerPixel = when (config) {
      Bitmap.Config.ALPHA_8 -> 1
      Bitmap.Config.RGB_565 -> 2
      Bitmap.Config.ARGB_4444 -> 2
      Bitmap.Config.ARGB_8888 -> 4
      Bitmap.Config.RGBA_F16 -> 8
      else -> 4
    }
    return width * height * bytesPerPixel
  }

  private fun BitmapFormat.toAndroidConfig(): Bitmap.Config = when (this) {
    BitmapFormat.ARGB_8888 -> Bitmap.Config.ARGB_8888
    BitmapFormat.RGB_565 -> Bitmap.Config.RGB_565
    BitmapFormat.HARDWARE -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Bitmap.Config.HARDWARE
      } else {
        Bitmap.Config.ARGB_8888
      }
    }
  }

  /**
   * Wrapper for pooled bitmaps to track byte count.
   */
  private data class PooledBitmap(
    val bitmap: Bitmap,
    val byteCount: Int,
  )
}
