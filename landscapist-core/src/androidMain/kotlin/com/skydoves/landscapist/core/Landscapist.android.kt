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
 */
public class AndroidBuilder(private val context: Context) {
  private var config: LandscapistConfig = LandscapistConfig()

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

    return Landscapist.builder()
      .config(config)
      .diskCache(diskCache)
      .build()
  }
}
