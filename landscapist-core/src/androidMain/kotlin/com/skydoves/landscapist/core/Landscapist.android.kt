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

import android.content.Context
import com.skydoves.landscapist.core.cache.DiskCache
import com.skydoves.landscapist.core.cache.DiskLruCache
import com.skydoves.landscapist.core.fetcher.AndroidContextProvider
import com.skydoves.landscapist.core.fetcher.AndroidFetchers
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Creates a default disk cache for Android.
 */
internal actual fun createDefaultDiskCache(maxSize: Long): DiskCache? {
  // On Android, we need context to get cache directory
  // This will be null until context is provided
  return null
}

/**
 * Android-specific extension to create Landscapist with context.
 */
public fun Landscapist.Companion.builder(context: Context): AndroidBuilder {
  return AndroidBuilder(context.applicationContext)
}

/**
 * Builder for creating [Landscapist] instances on Android with context.
 *
 * This builder automatically configures:
 * - Disk cache in the app's cache directory
 * - Android-specific fetchers for various image model types:
 *   - [android.graphics.Bitmap]
 *   - [android.graphics.drawable.Drawable]
 *   - [ByteArray]
 *   - [java.nio.ByteBuffer]
 *   - [java.io.File]
 *   - [android.net.Uri] (content://, file://, android.resource://, http://, https://)
 *   - [com.skydoves.landscapist.core.fetcher.DrawableResModel] (for R.drawable.* resources)
 *   - [Int] drawable resource IDs (e.g., R.drawable.image)
 *   - [String] URLs (http://, https://)
 */
public class AndroidBuilder(private val context: Context) {
  private var config: LandscapistConfig = LandscapistConfig()

  init {
    // Initialize the context provider for Android fetchers
    AndroidContextProvider.initialize(context)
  }

  /** Sets the configuration. */
  public fun config(config: LandscapistConfig): AndroidBuilder = apply {
    this.config = config
  }

  /** Builds the [Landscapist] instance. */
  public fun build(): Landscapist {
    val cacheDir = File(context.cacheDir, "landscapist_cache")
    val diskCache = DiskLruCache.create(
      directory = cacheDir.toOkioPath(),
      maxSize = config.diskCacheSize,
      fileSystem = FileSystem.SYSTEM,
    )

    // Create Android-specific fetcher with all supported model types
    val fetcher = AndroidFetchers.createDefault(config.networkConfig)

    // Size the memory cache relative to the app heap unless the caller set an explicit size. A
    // caller that sets exactly the 64MB default is treated as "default" and gets the heap-relative
    // size; to pin 64MB, set a value that differs by a byte or configure the cache directly.
    val usesDefaultMemoryCacheSize =
      config.memoryCacheSize == LandscapistConfig.DEFAULT_MEMORY_CACHE_SIZE
    val effectiveConfig = if (usesDefaultMemoryCacheSize) {
      config.copy(memoryCacheSize = heapRelativeMemoryCacheSize())
    } else {
      config
    }

    return Landscapist.builder()
      .config(effectiveConfig)
      .diskCache(diskCache)
      .fetcher(fetcher)
      .build()
  }

  /** Memory cache size scaled to the app heap: 25% of max heap, capped at 256MB. */
  private fun heapRelativeMemoryCacheSize(): Long {
    val maxHeap = Runtime.getRuntime().maxMemory()
    return (maxHeap / 4).coerceAtMost(MAX_MEMORY_CACHE_SIZE)
  }

  private companion object {
    private const val MAX_MEMORY_CACHE_SIZE: Long = 256L * 1024 * 1024
  }
}
