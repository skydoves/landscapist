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

import androidx.compose.runtime.Stable
import com.skydoves.landscapist.core.cache.CacheKey
import com.skydoves.landscapist.core.cache.CachedImage
import com.skydoves.landscapist.core.cache.DiskCache
import com.skydoves.landscapist.core.cache.LruMemoryCache
import com.skydoves.landscapist.core.cache.MemoryCache
import com.skydoves.landscapist.core.cache.TwoTierMemoryCache
import com.skydoves.landscapist.core.decoder.AnimatedImageDetector
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.decoder.ProgressiveDecodeResult
import com.skydoves.landscapist.core.decoder.RawImageData
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
import com.skydoves.landscapist.core.decoder.createProgressiveDecoder
import com.skydoves.landscapist.core.decoder.isSvg
import com.skydoves.landscapist.core.memory.MemoryPressureLevel
import com.skydoves.landscapist.core.memory.MemoryPressureListener
import com.skydoves.landscapist.core.memory.MemoryPressureManager
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.core.network.KtorImageFetcher
import com.skydoves.landscapist.core.request.Disposable
import com.skydoves.landscapist.core.request.ImmediateDisposable
import com.skydoves.landscapist.core.request.RequestManager
import com.skydoves.landscapist.core.scheduler.DecodeScheduler
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.use

/**
 * The main entry point for image loading in Landscapist.
 *
 * Orchestrates fetching, caching, decoding, and transformation of images.
 *
 * @property config The configuration for this loader.
 * @property memoryCache The memory cache instance.
 * @property diskCache The disk cache instance.
 * @property fetcher The network fetcher.
 * @property decoder The image decoder.
 * @property dispatcher The dispatcher for I/O operations.
 * @property requestManager The manager for tracking and cancelling requests.
 */
@Stable
public class Landscapist private constructor(
  public val config: LandscapistConfig,
  public val memoryCache: MemoryCache,
  private val diskCache: DiskCache?,
  private val fetcher: ImageFetcher,
  private val decoder: ImageDecoder,
  private val dispatcher: CoroutineDispatcher,
  public val requestManager: RequestManager = RequestManager(),
  public val memoryPressureManager: MemoryPressureManager = MemoryPressureManager(),
) {

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  // Coalesces concurrent standard loads that share a memory-cache key, so the same image is fetched
  // and decoded once instead of once per caller. Each entry counts its awaiters so the shared work
  // is cancelled when the last awaiter leaves (e.g. a list item scrolls off screen).
  private val inFlightLock = SynchronizedObject()
  private val inFlightRequests = mutableMapOf<String, InFlightLoad>()

  private class InFlightLoad(val deferred: Deferred<ImageResult>) {
    var waiters: Int = 0
  }

  // Coalesces the download alone, on the key the disk cache uses, which is the url and nothing
  // else. The map above keys on the memory key, and that carries the size the memory cache needs
  // and the network does not, so a screen showing one image as a thumbnail and again at full width
  // downloaded it twice. The bytes are the same at every size; only the decode is not.
  private val inFlightFetchLock = SynchronizedObject()
  private val inFlightFetches = mutableMapOf<String, InFlightFetch>()

  private class InFlightFetch(val deferred: Deferred<FetchResult>) {
    var waiters: Int = 0
  }

  init {
    // Register default memory pressure listener
    memoryPressureManager.addListener(DefaultMemoryPressureListener(memoryCache))
  }

  /**
   * Default memory pressure listener that trims the cache based on pressure level.
   */
  private class DefaultMemoryPressureListener(
    private val cache: MemoryCache,
  ) : MemoryPressureListener {
    override fun onMemoryPressure(level: MemoryPressureLevel) {
      when (level) {
        MemoryPressureLevel.LOW -> {
          // No action needed
        }
        MemoryPressureLevel.MODERATE -> {
          // Trim to 75% of max size
          cache.trimToSize((cache.maxSize * 0.75).toLong())
        }
        MemoryPressureLevel.HIGH -> {
          // Trim to 50% of max size
          cache.trimToSize((cache.maxSize * 0.5).toLong())
        }
        MemoryPressureLevel.CRITICAL -> {
          // Clear the entire cache
          cache.clear()
        }
      }
    }
  }

  /**
   * Loads an image based on the provided request.
   *
   * Pipeline: memory cache, then disk cache, then network fetch, then decode. Concurrent requests
   * for the same image (same memory-cache key) are coalesced into a single fetch and decode.
   *
   * Progressive loading ([ImageRequest.progressiveEnabled]) is an opt-in streaming mode that emits
   * intermediate previews; it is run independently per request and is not coalesced.
   *
   * @param request The image request.
   * @return A flow emitting [ImageResult] states.
   */
  public fun load(request: ImageRequest): Flow<ImageResult> =
    // Progressive streams its previews straight from that body, doing disk and network work inline,
    // so it needs a dispatcher of its own. The standard path does its fetching and decoding on this
    // loader's scope already, so imposing a dispatcher on it only added a round trip to every call,
    // memory cache hits included, and those are most of what a scrolling list does.
    if (request.progressiveEnabled) {
      flow { emitLoad(request) }.flowOn(dispatcher)
    } else {
      LoadFlow(request)
    }

  /**
   * The standard load, as a flow of its own rather than one the `flow { }` builder wraps.
   *
   * The builder exists to hand every collector a `SafeCollector`, which is not needed here: every
   * emission happens in the collector's own context, and progressive goes through `flowOn`. On a
   * memory cache hit the builder is most of what the load costs.
   */
  private inner class LoadFlow(private val request: ImageRequest) : Flow<ImageResult> {
    override suspend fun collect(collector: FlowCollector<ImageResult>): Unit =
      collector.emitLoad(request)
  }

  private suspend fun FlowCollector<ImageResult>.emitLoad(request: ImageRequest) {
    if (request.model == null) {
      emit(ImageResult.Failure(NullPointerException("Image model is null")))
      return
    }

    val cacheKey = request.cacheKey()

    // 1. Memory cache (instant). Checked per call so a hit never waits on coalescing, and checked
    // before any loading state is emitted so an image already in memory never blinks through one.
    if (request.memoryCachePolicy.readEnabled) {
      readMemoryCache(request, cacheKey)?.let { cached ->
        emit(cached.toSuccess())
        return
      }
    }

    emit(ImageResult.Loading)

    // 2. Progressive loading is an opt-in streaming mode and is not coalesced.
    if (request.progressiveEnabled) {
      loadProgressive(request, cacheKey)
      return
    }

    // 3. Standard path: coalesce concurrent identical loads into one fetch and decode.
    emit(dedupedStandardTerminal(cacheKey.memoryKey, request, cacheKey))
  }

  /**
   * Reads [request] out of the memory cache without suspending, or returns null when it is not
   * there.
   *
   * [load] resolves a memory hit through a flow and a dispatcher hop, which costs a frame or two of
   * empty content: long enough to blink, and long enough to be obvious inside a shared element
   * transition. Composables call this during composition instead, so a cached image is on screen in
   * the very first frame.
   *
   * The exact target size is matched first. When there is no entry for it, any already decoded size
   * of the same image and transformations is returned, since a caller that has not been measured
   * yet has no target size to ask for. The correctly sized image replaces it once [load] resolves.
   *
   * @param request The image request to look up.
   * @return The cached result, or null when nothing is cached or reads are disabled for [request].
   */
  public fun peekMemoryCache(request: ImageRequest): ImageResult.Success? {
    if (request.model == null || !request.memoryCachePolicy.readEnabled) return null
    val cacheKey = request.cacheKey()
    // The peek runs before layout, so there is usually no target size to satisfy and any decoded
    // variant will do. The correctly sized one replaces it as soon as the real load resolves.
    val cached = memoryCache[cacheKey] ?: memoryCache.getIgnoringSize(cacheKey) ?: return null
    return cached.toSuccess()
  }

  /**
   * Reads [request] out of the memory cache, reusing a differently sized variant when it is large
   * enough to serve this one.
   *
   * A layout rarely measures to exactly the same pixel twice. A grid whose columns do not divide
   * evenly asks for 359, 360 and 361 wide, and keying strictly on the target size makes those three
   * separate entries and three separate decodes of one image. Accepting an entry that is already at
   * least as large costs nothing in quality, since it is only ever scaled down to draw.
   */
  private fun readMemoryCache(request: ImageRequest, cacheKey: CacheKey): CachedImage? =
    memoryCache.getMatching(cacheKey) { cachedKey, cached ->
      cached.coversRequestedSize(cachedKey, request)
    }

  /**
   * Whether decoding [request] again could produce anything this entry does not already hold.
   *
   * Boxes are compared, not pixel counts: a decoder that fits an image inside the box and one that
   * fills it produce different pixels for the same request, and a box no larger than the one
   * already decoded for cannot yield more either way.
   *
   * An entry far larger than the request is refused even so, since drawing it costs bandwidth on
   * every frame and a painter plugin pays for every source pixel.
   */
  private fun CachedImage.coversRequestedSize(
    cachedKey: CacheKey,
    request: ImageRequest,
  ): Boolean {
    // The recorded size is what the decoder produced, not what the transformation left behind.
    if (request.transformations.isNotEmpty()) return false
    // Apple and wasm keep the encoded bytes and let Skia decode them at draw size, so the entry is
    // the whole source and no box can ask for more. Sizes say nothing here: they are the source's,
    // read from the header, and comparing them to a box would refuse every request but the widest.
    if (data is RawImageData) return true
    val targetWidth = request.targetWidth.asPixelBound()
    val targetHeight = request.targetHeight.asPixelBound()
    // Nothing to check against, and the variant could be a thumbnail. Only an exact match is used.
    if (targetWidth == null && targetHeight == null) return false
    if (originalWidth <= 0 || originalHeight <= 0) return false
    // A pixel of tolerance on each axis absorbs layouts that measure to fractional sizes.
    if (!axisCovered(targetWidth, cachedKey.width.asPixelBound())) return false
    if (!axisCovered(targetHeight, cachedKey.height.asPixelBound())) return false
    return !isWastefullyLargerThan(targetWidth, targetHeight, cachedKey)
  }

  /**
   * Whether an entry decoded for [decodedFor] on one axis can serve a request for [requested].
   *
   * Null is an open axis, the largest a request can be: it covers any bound and is covered by
   * nothing narrower.
   */
  private fun axisCovered(requested: Int?, decodedFor: Int?): Boolean = when {
    decodedFor == null -> true
    requested == null -> false
    else -> requested <= decodedFor + 1
  }

  /**
   * Whether this entry holds more than twice what the request can draw, on either axis.
   *
   * Either, not both: a panorama cached for a wide slot is the right height and eight times the
   * width when a narrow slot asks for it.
   */
  private fun CachedImage.isWastefullyLargerThan(
    targetWidth: Int?,
    targetHeight: Int?,
    cachedKey: CacheKey,
  ): Boolean {
    // Refusing an entry is only worth it when decoding again would produce something smaller.
    if (!couldDecodeSmaller(cachedKey)) return false
    val widthLimit = targetWidth?.let { it.toLong() * 2 } ?: Long.MAX_VALUE
    val heightLimit = targetHeight?.let { it.toLong() * 2 } ?: Long.MAX_VALUE
    return originalWidth > widthLimit || originalHeight > heightLimit
  }

  /**
   * Whether asking the decoder again, with a box this time, could come back with fewer pixels.
   *
   * Nothing was asked for the first time means this entry is the source, at whatever the decoder's
   * own cap allowed. A node measured to nothing on its first pass sends a request like that, which
   * is a collapsed row or a lazy item laid out before its container has room, so an ordinary screen
   * can leave a full sized entry behind. A request that does name a box can be answered with less.
   *
   * A box that was asked for and came back far larger is the other way round: the decoder took no
   * notice of it, which the Apple and wasm ones never do, and asking again returns the same pixels.
   * Refusing there would throw away a hit and decode them twice.
   *
   * The factor of two is a guess, since Android halves until one axis would fall under the box and
   * can stop anywhere in that range. An image whose shape is far from the box's can be sampled and
   * still land past the factor, and this reads that as ignored, so a large entry is kept for a
   * small slot. That costs memory, never correct pixels.
   */
  private fun CachedImage.couldDecodeSmaller(cachedKey: CacheKey): Boolean {
    val decodedForWidth = cachedKey.width.asPixelBound()
    val decodedForHeight = cachedKey.height.asPixelBound()
    if (decodedForWidth == null && decodedForHeight == null) return true
    if (decodedForWidth != null && originalWidth > decodedForWidth.toLong() * 2) return false
    if (decodedForHeight != null && originalHeight > decodedForHeight.toLong() * 2) return false
    return true
  }

  /**
   * Reads [url] out of the memory cache without suspending, or returns null when it is not there.
   *
   * @see peekMemoryCache
   */
  public fun peekMemoryCache(url: String): ImageResult.Success? =
    peekMemoryCache(ImageRequest(model = url))

  /** A target dimension in pixels, or null when the layout left that axis unbounded. */
  private fun Int?.asPixelBound(): Int? = this?.takeIf { it > 0 && it != Int.MAX_VALUE }

  private fun ImageRequest.cacheKey(): CacheKey = CacheKey.create(
    model = identityScopedModel(),
    // Most requests carry no transformations, and map() would allocate a list to say so.
    transformationKeys = if (transformations.isEmpty()) {
      emptyList()
    } else {
      transformations.map { it.key }
    },
    width = targetWidth,
    height = targetHeight,
  )

  /**
   * The model, scoped by the headers the request carries.
   *
   * An Authorization or Cookie header makes it a different viewer's image, and content negotiation
   * makes it different bytes, so they must not share a cache entry or a file on disk. A request
   * with no headers keys exactly as it did before.
   *
   * The whole header map counts, which means a rotating token re-keys every image behind it and
   * leaves the entries under the old one to be evicted. Send a credential that changes on its own
   * schedule through the network configuration rather than per request.
   */
  private fun ImageRequest.identityScopedModel(): Any? {
    if (headers.isEmpty()) return model
    val scope = headers.entries
      .sortedBy { it.key }
      .joinToString(separator = "\n") { "${it.key}: ${it.value}" }
    return "$model#${scope.encodeUtf8().sha256().hex()}"
  }

  private fun CachedImage.toSuccess(): ImageResult.Success = ImageResult.Success(
    data = data,
    dataSource = DataSource.MEMORY,
    originalWidth = originalWidth,
    originalHeight = originalHeight,
    diskCachePath = diskCachePath,
  )

  /**
   * Returns the terminal result of the standard (non-progressive) pipeline, coalescing concurrent
   * loads that share [memoryKey] so the image is fetched and decoded only once.
   *
   * The shared work survives any single caller's cancellation (so a sibling still completes), but is
   * cancelled once the last awaiter leaves, matching the usual "cancel the collector to cancel the
   * load" contract. Coalescing assumes callers that share a [memoryKey] have equivalent request
   * parameters; the first caller's request drives the shared work.
   */
  private suspend fun dedupedStandardTerminal(
    memoryKey: String,
    request: ImageRequest,
    cacheKey: CacheKey,
  ): ImageResult {
    val entry = synchronized(inFlightLock) {
      val existing = inFlightRequests[memoryKey]
      if (existing != null) {
        existing.waiters++
        existing
      } else {
        val created = InFlightLoad(scope.async { computeStandardTerminal(request, cacheKey) })
        created.waiters = 1
        inFlightRequests[memoryKey] = created
        created
      }
    }

    try {
      return entry.deferred.await()
    } finally {
      // The entry lives exactly as long as someone is waiting on it. It used to be dropped by a
      // completion handler instead, which cost an allocation and a second trip through this lock,
      // on the loading thread, for every load. The last caller to leave drops it here whether the
      // work finished or not, and cancels it only if it did not: an entry left behind after the
      // work is done would hand a later load a result the memory cache may since have evicted.
      val abandoned = synchronized(inFlightLock) {
        entry.waiters--
        if (entry.waiters == 0) {
          if (inFlightRequests[memoryKey] === entry) inFlightRequests.remove(memoryKey)
          entry.deferred.takeIf { !it.isCompleted }
        } else {
          null
        }
      }
      abandoned?.cancel()
    }
  }

  /**
   * Fetches the bytes for [request], sharing one trip with everyone asking for the same url.
   *
   * Keyed on the disk key, so two sizes of one image share a download and decode separately. A
   * differing header set already keys apart, since headers are folded into the model the key is
   * built from, so one viewer's bytes are never handed to another's request.
   *
   * The last caller to leave drops the entry, and cancels the work only if it has not finished,
   * which is how [dedupedStandardTerminal] handles the same problem.
   */
  private suspend fun dedupedFetch(request: ImageRequest, cacheKey: CacheKey): FetchResult {
    val key = cacheKey.diskKey
    val entry = synchronized(inFlightFetchLock) {
      val existing = inFlightFetches[key]
      if (existing != null) {
        existing.waiters++
        existing
      } else {
        val created = InFlightFetch(scope.async { fetcher.fetch(request) })
        created.waiters = 1
        inFlightFetches[key] = created
        created
      }
    }

    try {
      return entry.deferred.await()
    } finally {
      val abandoned = synchronized(inFlightFetchLock) {
        entry.waiters--
        if (entry.waiters == 0) {
          if (inFlightFetches[key] === entry) inFlightFetches.remove(key)
          entry.deferred.takeIf { !it.isCompleted }
        } else {
          null
        }
      }
      abandoned?.cancel()
    }
  }

  /**
   * Runs the standard pipeline (disk cache, then network, then decode) and returns the terminal
   * [ImageResult]. Never throws for expected failures; cancellation propagates.
   */
  private suspend fun computeStandardTerminal(
    request: ImageRequest,
    cacheKey: CacheKey,
  ): ImageResult {
    try {
      // Disk cache: a decode error here means the cached bytes are corrupt, so fall through to
      // the network rather than failing.
      if (request.diskCachePolicy.readEnabled && diskCache != null) {
        val diskResult = diskCache.get(cacheKey)?.use { snapshot ->
          val bytes = snapshot.data().buffer().readByteArray()
          val diskPath = snapshot.dataPath.toString()
          when (
            val decodeResult = scheduleDecodeWithPriority(cacheKey, request) {
              decoder.decode(
                data = bytes,
                mimeType = null,
                targetWidth = request.targetWidth,
                targetHeight = request.targetHeight,
                config = decodeConfigFor(request),
              )
            }
          ) {
            is DecodeResult.Success -> {
              val bitmap = applyTransformations(decodeResult.bitmap, request)
              if (request.memoryCachePolicy.writeEnabled) {
                memoryCache[cacheKey] = CachedImage(
                  data = bitmap,
                  dataSource = DataSource.DISK,
                  sizeBytes = estimateBitmapSize(decodeResult.width, decodeResult.height),
                  originalWidth = decodeResult.width,
                  originalHeight = decodeResult.height,
                  diskCachePath = diskPath,
                )
              }
              ImageResult.Success(
                data = bitmap,
                dataSource = DataSource.DISK,
                originalWidth = decodeResult.width,
                originalHeight = decodeResult.height,
                rawData = bytes,
                diskCachePath = diskPath,
              )
            }
            is DecodeResult.Error -> {
              diskCache.remove(cacheKey)
              null
            }
          }
        }
        if (diskResult != null) return diskResult
      }

      // Network fetch, shared with anyone else asking for the same url at another size.
      return when (val fetchResult = dedupedFetch(request, cacheKey)) {
        is FetchResult.Success -> {
          // Write to the disk cache off the critical path so the decode starts immediately. The
          // disk path is deterministic, so it can be reported before the background write finishes.
          val diskPath = if (request.diskCachePolicy.writeEnabled && diskCache != null) {
            val data = fetchResult.data
            scope.launch { runCatching { writeToDiskCache(cacheKey, data) } }
            (diskCache.directory / cacheKey.diskKey).toString()
          } else {
            null
          }
          decodeAndCacheStandard(
            bytes = fetchResult.data,
            mimeType = fetchResult.mimeType,
            request = request,
            cacheKey = cacheKey,
            dataSource = DataSource.NETWORK,
            diskPath = diskPath,
          )
        }

        is FetchResult.Error -> ImageResult.Failure(fetchResult.throwable)

        is FetchResult.Decoded -> finalizePreDecoded(
          image = fetchResult.image,
          dataSource = fetchResult.dataSource,
          width = fetchResult.width,
          height = fetchResult.height,
          request = request,
          cacheKey = cacheKey,
        )
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (throwable: Throwable) {
      return ImageResult.Failure(throwable)
    }
  }

  /**
   * Decodes [bytes] at the request's target size, applies transformations, updates the memory
   * cache, and returns the terminal [ImageResult].
   */
  private suspend fun decodeAndCacheStandard(
    bytes: ByteArray,
    mimeType: String?,
    request: ImageRequest,
    cacheKey: CacheKey,
    dataSource: DataSource,
    diskPath: String?,
  ): ImageResult {
    return when (
      val decodeResult = scheduleDecodeWithPriority(cacheKey, request) {
        decoder.decode(
          data = bytes,
          mimeType = mimeType,
          targetWidth = request.targetWidth,
          targetHeight = request.targetHeight,
          config = decodeConfigFor(request),
        )
      }
    ) {
      is DecodeResult.Success -> {
        val bitmap = applyTransformations(decodeResult.bitmap, request)
        if (request.memoryCachePolicy.writeEnabled) {
          memoryCache[cacheKey] = CachedImage(
            data = bitmap,
            dataSource = dataSource,
            sizeBytes = estimateBitmapSize(decodeResult.width, decodeResult.height),
            originalWidth = decodeResult.width,
            originalHeight = decodeResult.height,
            diskCachePath = diskPath,
          )
        }
        ImageResult.Success(
          data = bitmap,
          dataSource = dataSource,
          originalWidth = decodeResult.width,
          originalHeight = decodeResult.height,
          rawData = bytes,
          diskCachePath = diskPath,
        )
      }
      is DecodeResult.Error -> ImageResult.Failure(decodeResult.throwable)
    }
  }

  /**
   * Applies transformations to an already-decoded image, updates the memory cache, and returns the
   * terminal [ImageResult]. Used for fetchers that return a decoded image directly.
   */
  private suspend fun finalizePreDecoded(
    image: Any,
    dataSource: DataSource,
    width: Int,
    height: Int,
    request: ImageRequest,
    cacheKey: CacheKey,
  ): ImageResult {
    val transformed = applyTransformations(image, request)
    if (request.memoryCachePolicy.writeEnabled) {
      memoryCache[cacheKey] = CachedImage(
        data = transformed,
        dataSource = dataSource,
        sizeBytes = estimateBitmapSize(width, height),
        originalWidth = width,
        originalHeight = height,
      )
    }
    return ImageResult.Success(
      data = transformed,
      dataSource = dataSource,
      originalWidth = width,
      originalHeight = height,
    )
  }

  /**
   * Opt-in progressive pipeline. Streams intermediate previews while decoding; animated images and
   * pre-decoded results fall back to a single standard decode. Not coalesced across requests.
   */
  private suspend fun FlowCollector<ImageResult>.loadProgressive(
    request: ImageRequest,
    cacheKey: CacheKey,
  ) {
    // Disk cache.
    if (request.diskCachePolicy.readEnabled && diskCache != null) {
      val handled = diskCache.get(cacheKey)?.use { snapshot ->
        val bytes = snapshot.data().buffer().readByteArray()
        val diskPath = snapshot.dataPath.toString()
        if (supportsProgressiveDecode(bytes, null)) {
          emitProgressiveDecode(
            bytes = bytes,
            mimeType = null,
            request = request,
            cacheKey = cacheKey,
            dataSource = DataSource.DISK,
            diskPath = diskPath,
          )
        } else {
          emit(
            decodeAndCacheStandard(
              bytes = bytes,
              mimeType = null,
              request = request,
              cacheKey = cacheKey,
              dataSource = DataSource.DISK,
              diskPath = diskPath,
            ),
          )
        }
        true
      } ?: false
      if (handled) return
    }

    // Network fetch.
    when (val fetchResult = fetcher.fetch(request)) {
      is FetchResult.Success -> {
        var diskPath: String? = null
        if (request.diskCachePolicy.writeEnabled && diskCache != null) {
          diskCache.edit(cacheKey)?.use { editor ->
            diskCache.fileSystem.sink(editor.dataPath).buffer().use { sink ->
              sink.write(fetchResult.data)
            }
            editor.commit()
            diskPath = (diskCache.directory / cacheKey.diskKey).toString()
          }
        }

        if (supportsProgressiveDecode(fetchResult.data, fetchResult.mimeType)) {
          emitProgressiveDecode(
            bytes = fetchResult.data,
            mimeType = fetchResult.mimeType,
            request = request,
            cacheKey = cacheKey,
            dataSource = DataSource.NETWORK,
            diskPath = diskPath,
          )
        } else {
          emit(
            decodeAndCacheStandard(
              bytes = fetchResult.data,
              mimeType = fetchResult.mimeType,
              request = request,
              cacheKey = cacheKey,
              dataSource = DataSource.NETWORK,
              diskPath = diskPath,
            ),
          )
        }
      }

      is FetchResult.Error -> emit(ImageResult.Failure(fetchResult.throwable))

      is FetchResult.Decoded -> emit(
        finalizePreDecoded(
          image = fetchResult.image,
          dataSource = fetchResult.dataSource,
          width = fetchResult.width,
          height = fetchResult.height,
          request = request,
          cacheKey = cacheKey,
        ),
      )
    }
  }

  /**
   * The decode config for [request]. Hardware bitmaps are disabled when the request has
   * transformations, because a transformation needs CPU pixel access and would fail on a hardware
   * bitmap (which lives in GPU memory). This mirrors how Coil auto-disables hardware bitmaps for
   * transformed requests.
   */
  private fun decodeConfigFor(request: ImageRequest): LandscapistConfig =
    if (request.transformations.isEmpty()) {
      config
    } else {
      config.copy(bitmapConfig = config.bitmapConfig.copy(allowHardware = false))
    }

  /** Writes the encoded image bytes to the disk cache. Safe to run off the critical path. */
  private suspend fun writeToDiskCache(cacheKey: CacheKey, data: ByteArray) {
    val cache = diskCache ?: return
    cache.edit(cacheKey)?.use { editor ->
      cache.fileSystem.sink(editor.dataPath).buffer().use { sink -> sink.write(data) }
      editor.commit()
    }
  }

  /**
   * Schedules a decode operation with priority.
   */
  /**
   * Runs [decoder] through the shared decode gate.
   *
   * The id is the memory key, which is already built and is genuinely unique. Deriving it from
   * `model.hashCode()` meant two different URLs at the same target size shared an id, and the
   * scheduler's active map would drop one of them.
   */
  private suspend fun <T> scheduleDecodeWithPriority(
    cacheKey: CacheKey,
    request: ImageRequest,
    decoder: suspend () -> T,
  ): T = DecodeScheduler.global().schedule(
    id = cacheKey.memoryKey,
    priority = request.priority,
    tag = request.tag,
    decoder = decoder,
  ).await()

  /**
   * Performs progressive decoding and emits intermediate results.
   */
  private suspend fun FlowCollector<ImageResult>.emitProgressiveDecode(
    bytes: ByteArray,
    mimeType: String?,
    request: ImageRequest,
    cacheKey: CacheKey,
    dataSource: DataSource,
    diskPath: String?,
  ) {
    val progressiveDecoder = createProgressiveDecoder()

    progressiveDecoder.decodeProgressive(
      data = bytes,
      mimeType = mimeType,
      targetWidth = request.targetWidth,
      targetHeight = request.targetHeight,
      config = decodeConfigFor(request),
    ).collect { result ->
      when (result) {
        is ProgressiveDecodeResult.Intermediate -> {
          // Emit intermediate result (blurry preview)
          emit(
            ImageResult.Success(
              data = result.bitmap,
              dataSource = dataSource,
              originalWidth = result.width,
              originalHeight = result.height,
              rawData = bytes,
              diskCachePath = diskPath,
              isIntermediate = true,
              progress = result.progress,
            ),
          )
        }
        is ProgressiveDecodeResult.Complete -> {
          val bitmap = applyTransformations(result.bitmap, request)

          // Update memory cache with final result
          if (request.memoryCachePolicy.writeEnabled) {
            memoryCache[cacheKey] = CachedImage(
              data = bitmap,
              dataSource = dataSource,
              sizeBytes = estimateBitmapSize(result.width, result.height),
              originalWidth = result.width,
              originalHeight = result.height,
              diskCachePath = diskPath,
            )
          }

          emit(
            ImageResult.Success(
              data = bitmap,
              dataSource = dataSource,
              originalWidth = result.width,
              originalHeight = result.height,
              rawData = bytes,
              diskCachePath = diskPath,
            ),
          )
        }
        is ProgressiveDecodeResult.Error -> {
          emit(ImageResult.Failure(result.throwable))
        }
      }
    }
  }

  /**
   * Loads an image from a URL string.
   *
   * @param url The image URL.
   * @return A flow emitting [ImageResult] states.
   */
  public fun load(url: String): Flow<ImageResult> = load(
    ImageRequest.builder()
      .model(url)
      .build(),
  )

  /**
   * Enqueues an image loading request and returns a [Disposable] for cancellation.
   *
   * Unlike [load], this method executes the request asynchronously and allows
   * cancellation via the returned [Disposable].
   *
   * @param request The image request.
   * @param onResult Callback invoked with each [ImageResult] state.
   * @return A [Disposable] that can be used to cancel the request.
   */
  public fun enqueue(
    request: ImageRequest,
    onResult: (ImageResult) -> Unit,
  ): Disposable {
    if (request.model == null) {
      onResult(ImageResult.Failure(NullPointerException("Image model is null")))
      return ImmediateDisposable
    }

    val job = scope.launch {
      load(request).collect { result ->
        onResult(result)
      }
    }

    return requestManager.register(
      job = job,
      tag = request.tag,
      model = request.model,
    )
  }

  /**
   * Enqueues an image loading request from a URL string.
   *
   * @param url The image URL.
   * @param onResult Callback invoked with each [ImageResult] state.
   * @return A [Disposable] that can be used to cancel the request.
   */
  public fun enqueue(
    url: String,
    onResult: (ImageResult) -> Unit,
  ): Disposable = enqueue(
    request = ImageRequest.builder().model(url).build(),
    onResult = onResult,
  )

  /**
   * Cancels all active requests for this Landscapist instance.
   *
   * @return The number of requests cancelled.
   */
  public fun cancelAll(): Int = requestManager.cancelAll()

  /**
   * Cancels all requests with the given tag.
   *
   * @param tag The tag to match.
   * @return The number of requests cancelled.
   */
  public fun cancelByTag(tag: String): Int = requestManager.cancelByTag(tag)

  /**
   * Cancels all requests for the given model.
   *
   * @param model The image model to match.
   * @return The number of requests cancelled.
   */
  public fun cancelByModel(model: Any): Int = requestManager.cancelByModel(model)

  private suspend fun applyTransformations(
    bitmap: Any,
    request: ImageRequest,
  ): Any {
    if (request.transformations.isEmpty()) return bitmap

    // Transformations are CPU-bound, so run them on Default rather than the loader's I/O dispatcher.
    return withContext(Dispatchers.Default) {
      request.transformations.fold(bitmap) { current, transformation ->
        transformation.transform(current)
      }
    }
  }

  /**
   * Whether [bytes] can be streamed as a series of progressively sharper previews.
   *
   * Progressive decoding re-decodes the same raster bytes at several sample sizes. An animated
   * image has frames rather than one image to sample, and SVG is markup with no pixels at all, so
   * both take the standard single decode instead.
   */
  private fun supportsProgressiveDecode(bytes: ByteArray, mimeType: String?): Boolean =
    !AnimatedImageDetector.isAnimated(bytes, mimeType) && !isSvg(bytes, mimeType)

  private fun estimateBitmapSize(width: Int, height: Int): Long {
    // Assume 4 bytes per pixel (ARGB_8888)
    return width.toLong() * height.toLong() * 4L
  }

  /**
   * Clears all caches.
   */
  public suspend fun clearCaches() {
    memoryCache.clear()
    diskCache?.clear()
  }

  /**
   * Clears only the memory cache.
   */
  public fun clearMemoryCache() {
    memoryCache.clear()
  }

  /**
   * Clears only the disk cache.
   */
  public suspend fun clearDiskCache() {
    diskCache?.clear()
  }

  /**
   * Builder for creating [Landscapist] instances.
   */
  public class Builder {
    private var config: LandscapistConfig = LandscapistConfig()
    private var diskCacheDisabled: Boolean = false
    private var memoryCache: MemoryCache? = null
    private var diskCache: DiskCache? = null
    private var fetcher: ImageFetcher? = null
    private var decoder: ImageDecoder? = null
    private var dispatcher: CoroutineDispatcher = ioDispatcher

    /** Sets the configuration. */
    public fun config(config: LandscapistConfig): Builder = apply {
      this.config = config
    }

    /**
     * Sets the memory cache.
     *
     * A cache that does not override [MemoryCache.getMatching] still works, but loses the reuse of
     * an already decoded variant at a different size, so a layout that measures to 359, 360 and 361
     * decodes the same image three times.
     */
    public fun memoryCache(cache: MemoryCache): Builder = apply {
      this.memoryCache = cache
    }

    /** Sets a custom disk cache. */
    public fun diskCache(cache: DiskCache): Builder = apply {
      this.diskCache = cache
      this.diskCacheDisabled = false
    }

    /**
     * Builds a loader with no disk cache at all.
     *
     * Without this a loader always ends up owning one, because leaving it unset falls through to
     * the default on disk. A caller who does not want anything written to disk, or who has their
     * own caching in front of the fetcher, had no way to say so.
     */
    public fun noDiskCache(): Builder = apply {
      this.diskCache = null
      this.diskCacheDisabled = true
    }

    /** Sets a custom network fetcher. */
    public fun fetcher(fetcher: ImageFetcher): Builder = apply {
      this.fetcher = fetcher
    }

    /**
     * Sets a custom image decoder, replacing the default one entirely.
     *
     * The `landscapist-svg` artifact ships `SvgImageDecoder`, which adds SVG on top of any other
     * decoder: `decoder(SvgImageDecoder())`.
     */
    public fun decoder(decoder: ImageDecoder): Builder = apply {
      this.decoder = decoder
    }

    /** Sets the dispatcher for I/O operations. */
    public fun dispatcher(dispatcher: CoroutineDispatcher): Builder = apply {
      this.dispatcher = dispatcher
    }

    /** Builds the [Landscapist] instance. */
    public fun build(): Landscapist {
      val finalMemoryCache = memoryCache
        ?: config.memoryCache
        ?: if (config.weakReferencesEnabled) {
          TwoTierMemoryCache(config.memoryCacheSize, weakReferencesEnabled = true)
        } else {
          LruMemoryCache(config.memoryCacheSize)
        }

      val finalDiskCache = if (diskCacheDisabled) {
        null
      } else {
        diskCache ?: config.diskCache ?: createDefaultDiskCache(config.diskCacheSize)
      }

      val finalFetcher = fetcher
        ?: KtorImageFetcher.create(config.networkConfig)

      val finalDecoder = decoder
        ?: createPlatformDecoder()

      return Landscapist(
        config = config,
        memoryCache = finalMemoryCache,
        diskCache = finalDiskCache,
        fetcher = finalFetcher,
        decoder = finalDecoder,
        dispatcher = dispatcher,
      )
    }
  }

  public companion object {
    private var defaultInstance: Landscapist? = null
    private val namedInstances = mutableMapOf<String, Landscapist>()
    private val lock = SynchronizedObject()

    /** The default instance name. */
    public const val DEFAULT_INSTANCE_NAME: String = "default"

    /**
     * Gets the default [Landscapist] instance.
     * Creates one with default configuration if not already created.
     */
    public fun getInstance(): Landscapist {
      return synchronized(lock) {
        defaultInstance ?: Builder().build().also {
          defaultInstance = it
        }
      }
    }

    /**
     * Sets the default [Landscapist] instance.
     */
    public fun setInstance(landscapist: Landscapist) {
      synchronized(lock) {
        defaultInstance = landscapist
      }
    }

    /**
     * Gets or creates a named [Landscapist] instance.
     *
     * This allows SDK developers to have isolated instances that don't
     * interfere with the host application's default instance.
     *
     * @param name The unique name for this instance.
     * @param builder Optional builder function to configure the instance if it doesn't exist.
     * @return The named Landscapist instance.
     */
    public fun getInstance(name: String, builder: (Builder.() -> Unit)? = null): Landscapist {
      return synchronized(lock) {
        namedInstances.getOrPut(name) {
          Builder().apply { builder?.invoke(this) }.build()
        }
      }
    }

    /**
     * Sets a named [Landscapist] instance.
     *
     * @param name The unique name for this instance.
     * @param landscapist The Landscapist instance to set.
     */
    public fun setInstance(name: String, landscapist: Landscapist) {
      synchronized(lock) {
        namedInstances[name] = landscapist
      }
    }

    /**
     * Removes a named instance.
     *
     * @param name The name of the instance to remove.
     * @return true if an instance was removed, false if no instance existed with that name.
     */
    public fun removeInstance(name: String): Boolean {
      return synchronized(lock) {
        namedInstances.remove(name) != null
      }
    }

    /**
     * Checks if a named instance exists.
     *
     * @param name The name to check.
     * @return true if an instance exists with that name.
     */
    public fun hasInstance(name: String): Boolean {
      return synchronized(lock) {
        namedInstances.containsKey(name)
      }
    }

    /**
     * Returns all named instance names.
     *
     * @return A set of all registered instance names.
     */
    public fun getInstanceNames(): Set<String> {
      return synchronized(lock) {
        namedInstances.keys.toSet()
      }
    }

    /**
     * Clears all named instances.
     * Note: This does not clear the default instance.
     */
    public fun clearNamedInstances() {
      synchronized(lock) {
        namedInstances.clear()
      }
    }

    /**
     * Creates a new [Builder] instance.
     */
    public fun builder(): Builder = Builder()
  }
}

/**
 * Creates a default disk cache for the current platform.
 */
internal expect fun createDefaultDiskCache(maxSize: Long): DiskCache?
