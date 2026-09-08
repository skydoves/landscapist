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
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Canvas as SkiaCanvas

/**
 * The two engines, wired so they do the same work.
 *
 * Both are given a fetcher that hands back an already decoded image, which takes each library's own
 * decoder out of the picture. That matters because on the JVM Coil decodes through Skia and
 * landscapist-core decodes through ImageIO, so an end to end number would be comparing decoders
 * rather than loaders. Decoding is measured separately, and labelled as such.
 */
/**
 * A real decoded bitmap, sized to what the request asked for.
 *
 * It has to be real and it has to be painted by both sides. An opaque stand in that neither
 * library's painter recognises makes one of them draw nothing, and a fixed size one makes Coil
 * reject its own cache entry on every near miss. Either way the harness would be measuring the stub
 * rather than the loader.
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
      image = decodedBitmap(width, height).asImage(),
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

internal fun newCoil(
  counter: FetchCounter,
  latencyMs: Long = 0,
  configure: ImageLoader.Builder.() -> Unit = {},
): ImageLoader =
  ImageLoader.Builder(PlatformContext.INSTANCE)
    .apply(configure)
    .components { add(CoilStubFetcher.Factory(counter, latencyMs)) }
    .memoryCache { CoilMemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
    .diskCache(null)
    .build()

internal fun coilRequest(model: String, size: Int = 512): CoilRequest =
  CoilRequest.Builder(PlatformContext.INSTANCE)
    .data(model)
    .diskCachePolicy(CoilCachePolicy.DISABLED)
    // AsyncImagePainter forces INEXACT whenever precision is undefined, so this is what every Coil
    // Compose user actually runs. Leaving the EXACT default would flatter us on size reuse.
    .precision(Precision.INEXACT)
    .size(CoilSize(size, size))
    .build()
