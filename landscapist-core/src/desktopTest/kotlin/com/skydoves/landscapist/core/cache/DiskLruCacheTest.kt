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
import okio.Path
import okio.buffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the per-key edit exclusion of [DiskLruCache]: only one editor may be open for a given
 * disk key at a time, so two concurrent writes can never share (and clobber) the same temp file.
 */
class DiskLruCacheTest {

  private val fileSystem = FileSystem.SYSTEM
  private val dirs = mutableListOf<Path>()

  private fun newCache(): DiskLruCache {
    val name = "landscapist-disk-test-${counter.incrementAndGet()}"
    val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / name
    fileSystem.deleteRecursively(dir)
    fileSystem.createDirectories(dir)
    dirs.add(dir)
    return DiskLruCache(
      directory = dir,
      maxSize = 10_000_000,
      fileSystem = fileSystem,
      dispatcher = Dispatchers.Unconfined,
    )
  }

  @AfterTest
  fun cleanup() {
    dirs.forEach { runCatching { fileSystem.deleteRecursively(it) } }
    dirs.clear()
  }

  @Test
  fun concurrentEditOfSameKeyReturnsNull() {
    runBlocking {
      val cache = newCache()
      val key = CacheKey.create("https://example.com/a.jpg")

      val first = cache.edit(key)
      assertNotNull(first, "the first edit should open an editor")
      assertNull(
        cache.edit(key),
        "a second edit of the same key must return null while one is open",
      )

      first.abort()
      val reopened = cache.edit(key)
      assertNotNull(reopened, "the key should be editable again once released")
      reopened.abort()
    }
  }

  @Test
  fun commitReleasesKeyAndPersistsEntry() {
    runBlocking {
      val cache = newCache()
      val key = CacheKey.create("https://example.com/b.jpg")

      val editor = cache.edit(key)
      assertNotNull(editor)
      fileSystem.sink(editor.dataPath).buffer().use { it.write("hello".encodeToByteArray()) }
      editor.commit()

      assertNotNull(cache.get(key), "the committed entry should be readable")
      val reopened = cache.edit(key)
      assertNotNull(reopened, "the key should be editable again after commit")
      reopened.abort()
    }
  }

  @Test
  fun differentKeysEditConcurrently() {
    runBlocking {
      val cache = newCache()

      val a = cache.edit(CacheKey.create("https://example.com/x.jpg"))
      val b = cache.edit(CacheKey.create("https://example.com/y.jpg"))
      assertNotNull(a, "first key should open")
      assertNotNull(b, "a different key must be editable concurrently")

      a.abort()
      b.abort()
    }
  }

  private companion object {
    private val counter = atomic(0)
  }
}
