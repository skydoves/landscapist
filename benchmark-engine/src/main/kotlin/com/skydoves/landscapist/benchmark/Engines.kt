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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Precision
import coil3.size.pxOrElse
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.cache.CacheKey
import com.skydoves.landscapist.core.cache.DiskCache
import com.skydoves.landscapist.core.cache.MemoryCache
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.delay
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.util.concurrent.atomic.AtomicInteger
import coil3.decode.DataSource as CoilDataSource
import coil3.fetch.FetchResult as CoilFetchResult
import coil3.memory.MemoryCache as CoilMemoryCache
import coil3.request.CachePolicy as CoilCachePolicy
import coil3.request.ImageRequest as CoilRequest
import coil3.size.Size as CoilSize
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Canvas as SkiaCanvas

/**
 * A real decoded bitmap, sized to what the request asked for. Both libraries' painters have to
 * recognise it, and a fixed size one makes Coil reject its own cache entry on every near miss.
 */
internal fun decodedBitmap(width: Int, height: Int): SkiaBitmap {
  val bitmap = SkiaBitmap()
  bitmap.allocN32Pixels(width, height)
  SkiaCanvas(bitmap).clear(0xFF6750A4.toInt())
  bitmap.setImmutable()
  return bitmap
}

/** The same pixels as a Compose image, which is what the landscapist painter draws. */
internal fun composeBitmap(width: Int, height: Int): ImageBitmap =
  decodedBitmap(width, height).asComposeImageBitmap()

/** Counts how many times a loader actually reached the network for a given model. */
internal class FetchCounter {
  val count = AtomicInteger(0)
  fun reset() = count.set(0)
}

// ---------------------------------------------------------------------------------------------
// landscapist-core
// ---------------------------------------------------------------------------------------------

internal class LandscapistStubFetcher(
  private val counter: FetchCounter,
  private val latencyMs: Long,
) : ImageFetcher {
  override fun canHandle(model: Any?): Boolean = true
  override suspend fun fetch(request: ImageRequest): FetchResult {
    counter.count.incrementAndGet()
    if (latencyMs > 0) delay(latencyMs)
    val width = request.targetWidth?.takeIf { it in 1 until Int.MAX_VALUE } ?: 512
    val height = request.targetHeight?.takeIf { it in 1 until Int.MAX_VALUE } ?: 512
    return FetchResult.Decoded(
      image = composeBitmap(width, height),
      dataSource = DataSource.NETWORK,
      width = width,
      height = height,
    )
  }
}

internal fun newLandscapist(
  counter: FetchCounter,
  latencyMs: Long = 0,
  memoryCacheBytes: Long = DEFAULT_MEMORY_CACHE,
  memoryCache: MemoryCache? = null,
  fetcher: ImageFetcher = LandscapistStubFetcher(counter, latencyMs),
): Landscapist = Landscapist.builder()
  .config(
    LandscapistConfig(
      memoryCacheSize = memoryCacheBytes,
      memoryCache = memoryCache,
      diskCache = NoDiskCache,
    ),
  )
  .fetcher(fetcher)
  .build()

/**
 * The memory cache both loaders get unless a scenario says otherwise. Nothing here is ever
 * evicted at this size, so eviction is measured only where a scenario sets its own.
 */
internal const val DEFAULT_MEMORY_CACHE: Long = 64L * 1024 * 1024

/**
 * Stands in for the disk cache neither side is supposed to have. A null config `diskCache` falls
 * through to `createDefaultDiskCache` and would read the user's real cache directory.
 */
internal object NoDiskCache : DiskCache {
  override val directory: Path = "/nonexistent/landscapist-benchmark".toPath()
  override val maxSize: Long = 0
  override val size: Long = 0
  override val fileSystem: FileSystem = FileSystem.SYSTEM
  override suspend fun get(key: CacheKey): DiskCache.Snapshot? = null
  override suspend fun edit(key: CacheKey): DiskCache.Editor? = null
  override suspend fun remove(key: CacheKey): Boolean = false
  override suspend fun clear() = Unit
}

/** Fails every fetch, so the failure path can be measured rather than assumed to be free. */
internal class LandscapistFailingFetcher(private val latencyMs: Long = 0) : ImageFetcher {
  override fun canHandle(model: Any?): Boolean = true
  override suspend fun fetch(request: ImageRequest): FetchResult {
    if (latencyMs > 0) delay(latencyMs)
    return FetchResult.Error(IllegalStateException("benchmark failure"))
  }
}

internal fun landscapistRequest(model: String, size: Int = 512): ImageRequest =
  ImageRequest.builder()
    .model(model)
    .diskCachePolicy(CachePolicy.DISABLED)
    .size(size, size)
    .build()

// ---------------------------------------------------------------------------------------------
// Coil 3
// ---------------------------------------------------------------------------------------------

internal class CoilStubFetcher(
  private val counter: FetchCounter,
  private val latencyMs: Long,
  private val width: Int,
  private val height: Int,
) : Fetcher {
  override suspend fun fetch(): CoilFetchResult {
    counter.count.incrementAndGet()
    if (latencyMs > 0) delay(latencyMs)
    return ImageFetchResult(
      image = decodedBitmap(width, height).asImage(),
      // The stub hands back an image scaled to the box that was asked for, which is what sampled
      // means. Claiming otherwise short circuits isCacheValueValidForSize before any size is
      // compared, and Coil would serve a thumbnail to a full size request.
      isSampled = true,
      dataSource = CoilDataSource.NETWORK,
    )
  }

  class Factory(
    private val counter: FetchCounter,
    private val latencyMs: Long,
  ) : Fetcher.Factory<Any> {
    override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher =
      CoilStubFetcher(
        counter = counter,
        latencyMs = latencyMs,
        width = options.size.width.pxOrElse { 512 },
        height = options.size.height.pxOrElse { 512 },
      )
  }
}

internal fun newCoil(
  counter: FetchCounter,
  latencyMs: Long = 0,
  memoryCacheBytes: Long = DEFAULT_MEMORY_CACHE,
  failing: Boolean = false,
  configure: ImageLoader.Builder.() -> Unit = {},
): ImageLoader =
  ImageLoader.Builder(PlatformContext.INSTANCE)
    .apply(configure)
    .components {
      if (failing) {
        add(CoilFailingFetcher.Factory(latencyMs))
      } else {
        add(CoilStubFetcher.Factory(counter, latencyMs))
      }
    }
    .memoryCache { CoilMemoryCache.Builder().maxSizeBytes(memoryCacheBytes).build() }
    .diskCache(null)
    .build()

/** Coil's half of the failure path. */
internal class CoilFailingFetcher(private val latencyMs: Long) : Fetcher {
  override suspend fun fetch(): CoilFetchResult {
    if (latencyMs > 0) delay(latencyMs)
    throw IllegalStateException("benchmark failure")
  }

  class Factory(private val latencyMs: Long) : Fetcher.Factory<Any> {
    override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher =
      CoilFailingFetcher(latencyMs)
  }
}

internal fun coilRequest(model: String, size: Int = 512): CoilRequest =
  coilRequest(model, size, size)

internal fun coilRequest(model: String, width: Int, height: Int): CoilRequest =
  CoilRequest.Builder(PlatformContext.INSTANCE)
    .data(model)
    .diskCachePolicy(CoilCachePolicy.DISABLED)
    // AsyncImagePainter forces INEXACT when precision is undefined, so this is what users run.
    .precision(Precision.INEXACT)
    .size(CoilSize(width, height))
    .build()
