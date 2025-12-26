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
import com.skydoves.landscapist.core.cache.DiskLruCache
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Creates a default disk cache for Apple platforms.
 */
internal actual fun createDefaultDiskCache(maxSize: Long): DiskCache? {
  val cacheDirectories = NSSearchPathForDirectoriesInDomains(
    NSCachesDirectory,
    NSUserDomainMask,
    true,
  )

  val cacheDir = (cacheDirectories.firstOrNull() as? String)
    ?: return null

  val landscapistCacheDir = "$cacheDir/landscapist"

  return DiskLruCache.create(
    directory = landscapistCacheDir.toPath(),
    maxSize = maxSize,
    fileSystem = FileSystem.SYSTEM,
  )
}
