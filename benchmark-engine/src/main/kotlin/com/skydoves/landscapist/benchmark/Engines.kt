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

import coil3.Image
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import coil3.decode.DataSource as CoilDataSource
import coil3.fetch.FetchResult as CoilFetchResult
import coil3.memory.MemoryCache as CoilMemoryCache
import coil3.request.CachePolicy as CoilCachePolicy
import coil3.request.ImageRequest as CoilRequest
import coil3.size.Size as CoilSize

/**
 * The two engines, wired so they do the same work.
 *
 * Both are given a fetcher that hands back an already decoded image, which takes each library's own
 * decoder out of the picture. That matters because on the JVM Coil decodes through Skia and
 * landscapist-core decodes through ImageIO, so an end to end number would be comparing decoders
 * rather than loaders. Decoding is measured separately, and labelled as such.
 */
internal const val PIXELS_PER_IMAGE = 512 * 512 * 4L

/**
 * A decoded image stand in, sized to what the request asked for.
 *
 * Returning a fixed size instead would make Coil reject its own cache entry on every near miss,
 * which measures the stub rather than the loader.
 */
internal class StubImage(
  override val width: Int = 512,
  override val height: Int = 512,
) : Image {
  override val size: Long = width.toLong() * height.toLong() * 4L
  override val shareable: Boolean = true
  override fun draw(canvas: org.jetbrains.skia.Canvas) = Unit
}

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
      image = StubImage(width, height),
      dataSource = DataSource.NETWORK,
      width = width,
      height = height,
    )
  }
}

internal fun newLandscapist(counter: FetchCounter, latencyMs: Long = 0): Landscapist =
  Landscapist.builder()
    .config(LandscapistConfig(memoryCacheSize = 64L * 1024 * 1024))
    .fetcher(LandscapistStubFetcher(counter, latencyMs))
    .build()

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
      image = StubImage(width, height),
      isSampled = false,
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

internal fun newCoil(counter: FetchCounter, latencyMs: Long = 0): ImageLoader =
  ImageLoader.Builder(PlatformContext.INSTANCE)
    .components { add(CoilStubFetcher.Factory(counter, latencyMs)) }
    .memoryCache { CoilMemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
    .diskCache(null)
    .build()

internal fun coilRequest(model: String, size: Int = 512): CoilRequest =
  CoilRequest.Builder(PlatformContext.INSTANCE)
    .data(model)
    .diskCachePolicy(CoilCachePolicy.DISABLED)
    .size(CoilSize(size, size))
    .build()
