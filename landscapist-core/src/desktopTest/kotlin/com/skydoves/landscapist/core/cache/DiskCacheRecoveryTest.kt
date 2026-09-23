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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.buffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the disk cache does when something goes wrong while it is trimming or committing.
 *
 * A cache that is over its limit, or whose directory it cannot fully read, has to degrade into a
 * smaller cache. It must never become one that throws for the rest of the process, because
 * `Landscapist.load` reads the disk cache before it calls the fetcher: a throw here stops the
 * request being made at all, so no image loads and nothing reaches the network.
 *
 * Reported in #1014, where the throw was a `ConcurrentModificationException` raised on Kotlin
 * Native by reading a map entry after the map was modified. That trigger is platform specific and
 * cannot be reproduced on the JVM, which is why it went unnoticed. What these tests cover is the
 * part that made it unrecoverable rather than a one off failure, and that is the same everywhere.
 */
class DiskCacheRecoveryTest {

  private val dirs = mutableListOf<Path>()

  /**
   * Fails the delete of a cache entry, which is the one eviction performs.
   *
   * Scoped to entries rather than every delete, so the temp file housekeeping that `edit` does is
   * left working. The point is a trim that cannot finish, not a file system that is entirely gone.
   */
  private class FailingEvict(
    delegate: FileSystem,
    private val failure: () -> Throwable,
  ) : ForwardingFileSystem(delegate) {
    override fun delete(path: Path, mustExist: Boolean) {
      if (!path.name.endsWith(".tmp")) throw failure()
      super.delete(path, mustExist)
    }
  }

  /** Fails every [atomicMove], which is how a commit fails after the bytes are written. */
  private class FailingMove(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    override fun atomicMove(source: Path, target: Path) {
      throw IOException("move refused")
    }
  }

  private fun newDirectory(): Path {
    val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "landscapist-recovery-${counter.incrementAndGet()}"
    FileSystem.SYSTEM.deleteRecursively(dir)
    FileSystem.SYSTEM.createDirectories(dir)
    dirs.add(dir)
    return dir
  }

  /** Puts [count] files of [bytes] each straight into the directory, as a warm cache would. */
  private fun fill(dir: Path, count: Int, bytes: Int) {
    repeat(count) { index ->
      FileSystem.SYSTEM.sink(dir / "entry$index").buffer().use { it.write(ByteArray(bytes)) }
    }
  }

  @AfterTest
  fun cleanup() {
    dirs.forEach { runCatching { FileSystem.SYSTEM.deleteRecursively(it) } }
    dirs.clear()
  }

  @Test
  fun `a cache that cannot trim still answers`() {
    runBlocking {
      val dir = newDirectory()
      fill(dir, count = 4, bytes = 1_000)

      // Not an IOException, so it models the Native throw rather than a disk error: it escapes
      // the catch that initialize() has, and before the fix left the cache uninitialized forever.
      val failing = FailingEvict(FileSystem.SYSTEM) { IllegalStateException("entry gone") }
      val cache = DiskLruCache(
        directory = dir,
        maxSize = 1_500,
        fileSystem = failing,
        dispatcher = Dispatchers.Unconfined,
      )

      cache.get(CacheKey.create("https://example.com/a.jpg"))
      cache.get(CacheKey.create("https://example.com/b.jpg"))
      assertNotNull(
        cache.edit(CacheKey.create("https://example.com/c.jpg")),
        "the cache must still take writes after a trim it could not carry out",
      )
    }
  }

  @Test
  fun `a cache over its limit trims down to it`() {
    runBlocking {
      val dir = newDirectory()
      fill(dir, count = 10, bytes = 1_000)

      val cache = DiskLruCache(
        directory = dir,
        maxSize = 3_000,
        fileSystem = FileSystem.SYSTEM,
        dispatcher = Dispatchers.Unconfined,
      )

      cache.get(CacheKey.create("https://example.com/a.jpg"))

      assertTrue(cache.size <= 3_000, "the cache reports ${cache.size}, over its 3000 limit")
      val remaining = FileSystem.SYSTEM.list(dir).count()
      assertEquals(
        (cache.size / 1_000).toInt(),
        remaining,
        "the size it reports and the files it left disagree",
      )
    }
  }

  @Test
  fun `a commit that cannot move leaves nothing behind`() {
    runBlocking {
      val dir = newDirectory()
      val key = CacheKey.create("https://example.com/a.jpg")
      val cache = DiskLruCache(
        directory = dir,
        maxSize = 10_000,
        fileSystem = FailingMove(FileSystem.SYSTEM),
        dispatcher = Dispatchers.Unconfined,
      )

      val editor = assertNotNull(cache.edit(key), "the first edit should open an editor")
      FileSystem.SYSTEM.sink(editor.dataPath).buffer().use { it.write(ByteArray(64)) }
      editor.commit()

      assertFalse(
        FileSystem.SYSTEM.exists(editor.dataPath),
        "the temp file outlived a commit that failed",
      )
      assertNotNull(
        cache.edit(key),
        "the key is still locked for editing after a commit that failed",
      )
    }
  }

  private companion object {
    val counter = atomic(0)
  }
}
