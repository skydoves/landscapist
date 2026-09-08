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
  public fun load(request: ImageRequest): Flow<ImageResult> {
    val flow = flow { emitLoad(request) }
    // Progressive streams its previews straight from that body, doing disk and network work inline,
    // so it needs a dispatcher of its own. The standard path does its fetching and decoding on this
    // loader's scope already, so imposing a dispatcher on it only added a round trip to every call,
    // memory cache hits included, and those are most of what a scrolling list does.
    return if (request.progressiveEnabled) flow.flowOn(dispatcher) else flow
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
   * The comparison is between the box this entry was decoded for and the box being asked for now,
   * not between pixel counts. How a decoder turns a box into an image is its own business: most fit
   * the image inside it and keep its shape, so a 4000x3000 photo asked for at 360x360 comes back as
   * 360x270, while an SVG renderer fills the box exactly and would come back 360x360. Reasoning
   * from the pixels an entry happens to have means picking one of those and being wrong about the
   * other. A box no larger than the one already decoded for cannot yield more, whichever it is.
   *
   * An entry far larger than the request is refused even so. Drawing a 1080 pixel bitmap into a 360
   * pixel slot costs memory bandwidth on every frame, and a painter plugin that works on the source
   * pixels, a blur for instance, pays for all of them.
   */
  private fun CachedImage.coversRequestedSize(
    cachedKey: CacheKey,
    request: ImageRequest,
  ): Boolean {
    val targetWidth = request.targetWidth.asPixelBound()
    val targetHeight = request.targetHeight.asPixelBound()
    // With no bound on either axis there is nothing to check against, and the cached variant could
    // be a thumbnail from a plugin. Only an exact key match is reused then.
    if (targetWidth == null && targetHeight == null) return false
    if (originalWidth <= 0 || originalHeight <= 0) return false
    // A transformation is free to resize what it is handed, and the size recorded for an entry is
    // the size the decoder produced, not the size the transformation left behind. There is nothing
    // dependable to compare, so only an exact key match is reused.
    if (request.transformations.isNotEmpty()) return false
    // A pixel of tolerance on each axis absorbs layouts that measure to fractional sizes.
    if (!axisCovered(targetWidth, cachedKey.width.asPixelBound())) return false
    if (!axisCovered(targetHeight, cachedKey.height.asPixelBound())) return false
    return !isWastefullyLargerThan(targetWidth, targetHeight)
  }

  /**
   * Whether an entry decoded for [decodedFor] on one axis can serve a request for [requested].
   *
   * Null means the axis was left open, which is the largest a request can be: an entry decoded that
   * way covers any bound, and a request made that way is covered by nothing narrower.
   */
  private fun axisCovered(requested: Int?, decodedFor: Int?): Boolean = when {
    decodedFor == null -> true
    requested == null -> false
    else -> requested <= decodedFor + 1
  }

  /** Whether this entry holds more than twice the pixels per axis that the request can draw. */
  private fun CachedImage.isWastefullyLargerThan(targetWidth: Int?, targetHeight: Int?): Boolean {
    val widthLimit = targetWidth?.let { it.toLong() * 2 } ?: Long.MAX_VALUE
    val heightLimit = targetHeight?.let { it.toLong() * 2 } ?: Long.MAX_VALUE
    return originalWidth > widthLimit && originalHeight > heightLimit
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
    model = model,
    // Most requests carry no transformations, and map() would allocate a list to say so.
    transformationKeys = if (transformations.isEmpty()) {
      emptyList()
    } else {
      transformations.map { it.key }
    },
    width = targetWidth,
    height = targetHeight,
  )

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
    val (entry, owner) = synchronized(inFlightLock) {
      val existing = inFlightRequests[memoryKey]
      if (existing != null) {
        existing.waiters++
        existing to false
      } else {
        val created = InFlightLoad(scope.async { computeStandardTerminal(request, cacheKey) })
        created.waiters = 1
        inFlightRequests[memoryKey] = created
        created to true
      }
    }

    if (owner) {
      // Registered outside inFlightLock on purpose: the handler re-enters the lock and atomicfu's
      // synchronized is not reentrant on native. It removes the entry once the work finishes so a
      // later load (after a cache eviction) starts fresh.
      entry.deferred.invokeOnCompletion {
        synchronized(inFlightLock) {
          if (inFlightRequests[memoryKey] === entry) inFlightRequests.remove(memoryKey)
        }
      }
    }

    try {
      return entry.deferred.await()
    } finally {
      val abandoned = synchronized(inFlightLock) {
        entry.waiters--
        if (entry.waiters == 0 && !entry.deferred.isCompleted) {
          if (inFlightRequests[memoryKey] === entry) inFlightRequests.remove(memoryKey)
          entry.deferred
        } else {
          null
        }
      }
      // Cancel outside the lock; the completion handler re-enters inFlightLock.
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

      // Network fetch.
      return when (val fetchResult = fetcher.fetch(request)) {
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

    /** Sets a custom memory cache. */
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
