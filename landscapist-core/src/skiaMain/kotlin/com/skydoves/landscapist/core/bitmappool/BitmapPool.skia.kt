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
 * Creates a no-op bitmap pool for Skia platforms.
 *
 * Skia manages its own memory efficiently, so bitmap pooling provides
 * less benefit compared to Android. This implementation simply allocates
 * new bitmaps on demand without pooling.
 */
public actual fun createBitmapPool(maxSize: Long): BitmapPool = NoOpBitmapPool

/**
 * No-op implementation of [BitmapPool] for non-Android platforms.
 *
 * Skia platforms don't benefit as much from bitmap pooling because:
 * 1. Skia has its own internal memory management
 * 2. Desktop/iOS have different memory pressure characteristics
 * 3. The overhead of pooling may not be justified
 */
internal object NoOpBitmapPool : BitmapPool {
  override val maxSize: Long = 0L
  override val currentSize: Long = 0L

  override fun get(width: Int, height: Int, format: BitmapFormat): Any? = null

  override fun getReusable(width: Int, height: Int, format: BitmapFormat): Any? = null

  override fun put(bitmap: Any): Boolean = false

  override fun clear() {}

  override fun trimToSize(targetSize: Long) {}

  override fun getStats(): BitmapPoolStats = BitmapPoolStats(
    hits = 0,
    misses = 0,
    pooledCount = 0,
    pooledBytes = 0,
    evictions = 0,
  )
}
