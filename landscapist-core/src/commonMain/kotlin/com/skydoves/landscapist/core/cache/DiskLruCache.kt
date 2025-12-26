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

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Source

/**
 * A simple disk cache implementation using Okio.
 *
 * This is a simplified LRU disk cache that stores files in a directory.
 * It tracks access order using file modification times.
 *
 * @property directory The directory for cache files.
 * @property maxSize Maximum cache size in bytes.
 * @property fileSystem The file system to use.
 * @property dispatcher The dispatcher for I/O operations.
 */
public class DiskLruCache(
  override val directory: Path,
  override val maxSize: Long,
  override val fileSystem: FileSystem,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DiskCache {

  private val lock = SynchronizedObject()
  private val entries = linkedMapOf<String, Entry>()
  private val currentSize = atomic(0L)
  private val initialized = atomic(false)

  override val size: Long
    get() = currentSize.value

  private suspend fun initialize() {
    if (initialized.value) return

    withContext(dispatcher) {
      synchronized(lock) {
        if (initialized.value) return@synchronized

        try {
          fileSystem.createDirectories(directory)

          // Scan existing files
          if (fileSystem.exists(directory)) {
            fileSystem.list(directory).forEach { path ->
              val name = path.name
              if (!name.endsWith(".tmp")) {
                try {
                  val metadata = fileSystem.metadata(path)
                  val size = metadata.size ?: 0L
                  entries[name] = Entry(name, size)
                  currentSize.addAndGet(size)
                } catch (_: IOException) {
                  // Ignore corrupted entries
                }
              }
            }
          }

          // Evict if over size
          evictIfNeeded()
        } catch (_: IOException) {
          // Directory creation failed, cache will be disabled
        }

        initialized.value = true
      }
    }
  }

  override suspend fun get(key: CacheKey): DiskCache.Snapshot? {
    initialize()

    return withContext(dispatcher) {
      synchronized(lock) {
        val diskKey = key.diskKey
        val entry = entries.remove(diskKey) ?: return@synchronized null

        // Re-insert to update access order
        entries[diskKey] = entry

        val path = directory / diskKey
        if (fileSystem.exists(path)) {
          SnapshotImpl(path, fileSystem)
        } else {
          entries.remove(diskKey)
          currentSize.addAndGet(-entry.size)
          null
        }
      }
    }
  }

  override suspend fun edit(key: CacheKey): DiskCache.Editor? {
    initialize()

    return withContext(dispatcher) {
      synchronized(lock) {
        val diskKey = key.diskKey
        val tempPath = directory / "$diskKey.tmp"
        val finalPath = directory / diskKey

        // Remove temp file if exists
        try {
          fileSystem.delete(tempPath)
        } catch (_: IOException) {
          // Ignore
        }

        EditorImpl(
          diskKey = diskKey,
          tempPath = tempPath,
          finalPath = finalPath,
          fileSystem = fileSystem,
          onCommit = { size -> commitEntry(diskKey, size) },
          onAbort = { /* Nothing to do */ },
        )
      }
    }
  }

  private fun commitEntry(diskKey: String, size: Long) {
    synchronized(lock) {
      // Remove old entry if exists
      entries.remove(diskKey)?.let { old ->
        currentSize.addAndGet(-old.size)
      }

      // Add new entry
      entries[diskKey] = Entry(diskKey, size)
      currentSize.addAndGet(size)

      evictIfNeeded()
    }
  }

  override suspend fun remove(key: CacheKey): Boolean {
    initialize()

    return withContext(dispatcher) {
      synchronized(lock) {
        val diskKey = key.diskKey
        entries.remove(diskKey)?.let { entry ->
          currentSize.addAndGet(-entry.size)
          val path = directory / diskKey
          try {
            fileSystem.delete(path)
            true
          } catch (_: IOException) {
            false
          }
        } ?: false
      }
    }
  }

  override suspend fun clear() {
    initialize()

    withContext(dispatcher) {
      synchronized(lock) {
        entries.keys.toList().forEach { diskKey ->
          try {
            fileSystem.delete(directory / diskKey)
          } catch (_: IOException) {
            // Ignore
          }
        }
        entries.clear()
        currentSize.value = 0
      }
    }
  }

  private fun evictIfNeeded() {
    while (currentSize.value > maxSize && entries.isNotEmpty()) {
      val iterator = entries.entries.iterator()
      if (iterator.hasNext()) {
        val eldest = iterator.next()
        iterator.remove()
        currentSize.addAndGet(-eldest.value.size)
        try {
          fileSystem.delete(directory / eldest.key)
        } catch (_: IOException) {
          // Ignore
        }
      }
    }
  }

  private data class Entry(
    val key: String,
    val size: Long,
  )

  private class SnapshotImpl(
    override val dataPath: Path,
    private val fileSystem: FileSystem,
  ) : DiskCache.Snapshot {
    private var source: Source? = null

    override fun data(): Source {
      return source ?: fileSystem.source(dataPath).also { source = it }
    }

    override fun close() {
      try {
        source?.close()
      } catch (_: IOException) {
        // Ignore
      }
    }
  }

  private class EditorImpl(
    private val diskKey: String,
    private val tempPath: Path,
    private val finalPath: Path,
    private val fileSystem: FileSystem,
    private val onCommit: (Long) -> Unit,
    private val onAbort: () -> Unit,
  ) : DiskCache.Editor {
    private var committed = false
    private var aborted = false

    override val dataPath: Path
      get() = tempPath

    override suspend fun commit() {
      if (committed || aborted) return
      committed = true

      try {
        // Move temp file to final location
        if (fileSystem.exists(tempPath)) {
          fileSystem.delete(finalPath)
          fileSystem.atomicMove(tempPath, finalPath)
          val size = fileSystem.metadata(finalPath).size ?: 0L
          onCommit(size)
        }
      } catch (_: IOException) {
        abort()
      }
    }

    override suspend fun abort() {
      if (committed || aborted) return
      aborted = true

      try {
        fileSystem.delete(tempPath)
      } catch (_: IOException) {
        // Ignore
      }
      onAbort()
    }

    override fun close() {
      if (!committed && !aborted) {
        // Abort if not explicitly committed
        try {
          fileSystem.delete(tempPath)
        } catch (_: IOException) {
          // Ignore
        }
      }
    }
  }

  public companion object {
    /**
     * Creates a new [DiskLruCache] instance.
     *
     * @param directory The cache directory.
     * @param maxSize Maximum cache size in bytes.
     * @param fileSystem The file system to use.
     */
    public fun create(
      directory: Path,
      maxSize: Long,
      fileSystem: FileSystem,
    ): DiskLruCache = DiskLruCache(directory, maxSize, fileSystem)
  }
}
