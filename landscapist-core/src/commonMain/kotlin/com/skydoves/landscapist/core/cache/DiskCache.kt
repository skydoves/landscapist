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
package com.skydoves.landscapist.core.cache

import okio.Closeable
import okio.FileSystem
import okio.Path
import okio.Source

/**
 * Interface for disk cache operations.
 */
public interface DiskCache {
  /** The directory where cache files are stored. */
  public val directory: Path

  /** Maximum size of the cache in bytes. */
  public val maxSize: Long

  /** Current size of the cache in bytes. */
  public val size: Long

  /** The file system used for I/O operations. */
  public val fileSystem: FileSystem

  /**
   * Opens a snapshot of an entry in the cache.
   *
   * @param key The cache key.
   * @return A snapshot of the entry, or null if not found.
   */
  public suspend fun get(key: CacheKey): Snapshot?

  /**
   * Opens an editor for writing to an entry.
   *
   * @param key The cache key.
   * @return An editor for the entry, or null if the entry is being edited.
   */
  public suspend fun edit(key: CacheKey): Editor?

  /**
   * Removes an entry from the cache.
   *
   * @param key The cache key.
   * @return true if the entry was removed, false otherwise.
   */
  public suspend fun remove(key: CacheKey): Boolean

  /**
   * Clears all entries from the cache.
   */
  public suspend fun clear()

  /**
   * A snapshot of a cache entry.
   */
  public interface Snapshot : Closeable {
    /** The path to the cached data file. */
    public val dataPath: Path

    /** Opens a source to read the cached data. */
    public fun data(): Source
  }

  /**
   * An editor for writing to a cache entry.
   */
  public interface Editor : Closeable {
    /** The path to write data to. */
    public val dataPath: Path

    /** Commits the changes and closes the editor. */
    public suspend fun commit()

    /** Aborts the edit and discards changes. */
    public suspend fun abort()
  }
}
