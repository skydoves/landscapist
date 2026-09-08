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
package com.skydoves.landscapist.benchmark

import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.cache.CacheKey
import com.skydoves.landscapist.core.cache.DiskLruCache
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * What the disk cache does when the same image is asked for at more than one size, measured in
 * encoded bytes: a disk miss is a download, not a decode. Only landscapist's half is measured;
 * Coil's disk cache lives inside the network fetcher this harness replaces.
 */
internal fun diskKeyComparison() {
  val sizes = listOf(360, 359, 361, 720)
  val model = "https://example.com/disk-key.jpg"

  val directory = java.nio.file.Files.createTempDirectory("landscapist-benchmark-disk")
  val disk = DiskLruCache.create(
    directory = directory.toString().toPath(),
    maxSize = 64L * 1024 * 1024,
    fileSystem = FileSystem.SYSTEM,
  )
  val counter = FetchCounter()
  val bytes = smallJpeg()
  val landscapist = Landscapist.builder()
    .config(LandscapistConfig(memoryCacheSize = 8L * 1024 * 1024))
    .diskCache(disk)
    .fetcher(BytesFetcher(counter, bytes))
    .build()

  runBlocking {
    for (size in sizes) {
      // Cleared between sizes, or the memory cache answers and the disk cache is never asked.
      landscapist.clearMemoryCache()
      val request = ImageRequest.builder()
        .model(model)
        .diskCachePolicy(CachePolicy.ENABLED)
        .size(size, size)
        .build()
      val result = landscapist.load(request).first { it is ImageResult.Success }
      check(result is ImageResult.Success)
      if (result.dataSource == DataSource.NETWORK) {
        // The write is launched off the critical path, so wait rather than sleep and race it.
        awaitEntry(directory, CacheKey.create(model, width = size, height = size).diskKey)
      }
    }
  }

  val files = FileSystem.SYSTEM.list(directory.toString().toPath())
    .filterNot { it.name.endsWith(".tmp") }
  val onDisk = files.sumOf { FileSystem.SYSTEM.metadata(it).size ?: 0L }
  val distinctKeys = sizes
    .map { CacheKey.create(model, width = it, height = it).diskKey }
    .distinct()

  println("one image downloaded, then asked for at $sizes with the memory cache cleared between")
  println(
    "  ${"downloads".padEnd(38)}landscapist ${counter.count.get()}" +
      "   coil 1 (by construction)",
  )
  println(
    "  ${"disk keys for the four requests".padEnd(38)}landscapist ${distinctKeys.size}" +
      "   coil 1 (by construction)",
  )
  println(
    "  ${"copies of the same file on disk".padEnd(38)}landscapist ${files.size}, " +
      "${onDisk.formatBytes()} for a ${bytes.size.toLong().formatBytes()} image",
  )
  println(
    "    Both key the disk cache on the url alone, so one download answers every size. It used " +
      "to fold the size and the transformations in, which stored a copy of the same file per " +
      "size and went back to the network for each of them. The Coil column is read from its " +
      "source rather than measured, because this harness replaces the fetcher its disk cache " +
      "lives in: `NetworkFetcher` uses `options.diskCacheKey ?: url`, and no size reaches it.",
  )
  println()

  FileSystem.SYSTEM.deleteRecursively(directory.toString().toPath())
}

/** Hands back encoded bytes rather than a decoded image, so the disk cache has work to do. */
private class BytesFetcher(
  private val counter: FetchCounter,
  private val bytes: ByteArray,
) : ImageFetcher {
  override fun canHandle(model: Any?): Boolean = true
  override suspend fun fetch(request: ImageRequest): FetchResult {
    counter.count.incrementAndGet()
    return FetchResult.Success(data = bytes, mimeType = "image/jpeg")
  }
}

/** Small, because this row is about how often the bytes are stored, not how long they decode. */
private fun smallJpeg(): ByteArray {
  val image = BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB)
  var seed = 0x9E3779B9.toInt()
  for (y in 0 until 800) {
    for (x in 0 until 800) {
      seed = seed * 1_664_525 + 1_013_904_223
      image.setRGB(x, y, seed and 0xFFFFFF)
    }
  }
  return ByteArrayOutputStream().use { out ->
    ImageIO.write(image, "jpg", out)
    out.toByteArray()
  }
}

/** Waits for [key] to appear in [directory], giving up after a second. */
private fun awaitEntry(directory: java.nio.file.Path, key: String) {
  val deadline = System.nanoTime() + 1_000_000_000L
  while (System.nanoTime() < deadline) {
    val present = FileSystem.SYSTEM.list(directory.toString().toPath())
      .any { it.name.startsWith(key) && !it.name.endsWith(".tmp") }
    if (present) return
    Thread.sleep(5)
  }
}
