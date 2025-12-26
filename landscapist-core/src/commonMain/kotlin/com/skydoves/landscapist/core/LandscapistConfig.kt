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
package com.skydoves.landscapist.core

import com.skydoves.landscapist.core.cache.DiskCache
import com.skydoves.landscapist.core.cache.MemoryCache
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Global configuration for the Landscapist image loader.
 *
 * @property memoryCacheSize Maximum size of the memory cache in bytes.
 * @property memoryCache Custom memory cache implementation, if any.
 * @property diskCacheSize Maximum size of the disk cache in bytes.
 * @property diskCache Custom disk cache implementation, if any.
 * @property networkConfig Network configuration settings.
 * @property maxBitmapSize Maximum dimension for decoded bitmaps.
 * @property allowRgb565 Whether to allow RGB_565 format for images without alpha.
 */
public data class LandscapistConfig(
  val memoryCacheSize: Long = DEFAULT_MEMORY_CACHE_SIZE,
  val memoryCache: MemoryCache? = null,
  val diskCacheSize: Long = DEFAULT_DISK_CACHE_SIZE,
  val diskCache: DiskCache? = null,
  val networkConfig: NetworkConfig = NetworkConfig(),
  val maxBitmapSize: Int = DEFAULT_MAX_BITMAP_SIZE,
  val allowRgb565: Boolean = false,
) {
  public companion object {
    /** Default memory cache size: 25% of available memory, max 256MB. */
    public const val DEFAULT_MEMORY_CACHE_SIZE: Long = 64 * 1024 * 1024L // 64MB

    /** Default disk cache size: 100MB. */
    public const val DEFAULT_DISK_CACHE_SIZE: Long = 100 * 1024 * 1024L // 100MB

    /** Default maximum bitmap dimension. */
    public const val DEFAULT_MAX_BITMAP_SIZE: Int = 4096
  }
}

/**
 * Network configuration for HTTP requests.
 *
 * @property connectTimeout Connection timeout duration.
 * @property readTimeout Read timeout duration.
 * @property userAgent User-Agent header value.
 * @property defaultHeaders Default headers to include in all requests.
 * @property followRedirects Whether to follow HTTP redirects.
 * @property maxRedirects Maximum number of redirects to follow.
 */
public data class NetworkConfig(
  val connectTimeout: Duration = 10.seconds,
  val readTimeout: Duration = 30.seconds,
  val userAgent: String = "Landscapist/1.0",
  val defaultHeaders: Map<String, String> = emptyMap(),
  val followRedirects: Boolean = true,
  val maxRedirects: Int = 5,
)
