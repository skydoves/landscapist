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

import com.skydoves.landscapist.core.cache.CacheKey
import com.skydoves.landscapist.core.cache.CachedImage
import com.skydoves.landscapist.core.cache.DiskCache
import com.skydoves.landscapist.core.cache.LruMemoryCache
import com.skydoves.landscapist.core.cache.MemoryCache
import com.skydoves.landscapist.core.cache.TwoTierMemoryCache
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.decoder.createPlatformDecoder
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
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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
public class Landscapist private constructor(
  public val config: LandscapistConfig,
  private val memoryCache: MemoryCache,
  private val diskCache: DiskCache?,
  private val fetcher: ImageFetcher,
  private val decoder: ImageDecoder,
  private val dispatcher: CoroutineDispatcher,
  public val requestManager: RequestManager = RequestManager(),
  public val memoryPressureManager: MemoryPressureManager = MemoryPressureManager(),
) {

  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

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
   * @param request The image request.
   * @return A flow emitting [ImageResult] states.
   */
  public fun load(request: ImageRequest): Flow<ImageResult> = flow {
    if (request.model == null) {
      emit(ImageResult.Failure(NullPointerException("Image model is null")))
      return@flow
    }

    emit(ImageResult.Loading)

    val cacheKey = CacheKey.create(
      model = request.model,
      transformationKeys = request.transformations.map { it.key },
      width = request.targetWidth,
      height = request.targetHeight,
    )

    // 1. Check memory cache
    if (request.memoryCachePolicy.readEnabled) {
      memoryCache[cacheKey]?.let { cached ->
        emit(
          ImageResult.Success(
            data = cached.data,
            dataSource = DataSource.MEMORY,
          ),
        )
        return@flow
      }
    }

    // 2. Check disk cache
    if (request.diskCachePolicy.readEnabled && diskCache != null) {
      diskCache.get(cacheKey)?.use { snapshot ->
        val bytes = snapshot.data().buffer().readByteArray()
        val diskPath = snapshot.dataPath.toString()
        val decodeResult = decoder.decode(
          data = bytes,
          mimeType = null,
          targetWidth = request.targetWidth,
          targetHeight = request.targetHeight,
          config = config,
        )

        when (decodeResult) {
          is DecodeResult.Success -> {
            val bitmap = applyTransformations(decodeResult.bitmap, request)

            // Update memory cache
            if (request.memoryCachePolicy.writeEnabled) {
              memoryCache[cacheKey] = CachedImage(
                data = bitmap,
                dataSource = DataSource.DISK,
                sizeBytes = estimateBitmapSize(decodeResult.width, decodeResult.height),
              )
            }

            emit(
              ImageResult.Success(
                data = bitmap,
                dataSource = DataSource.DISK,
                originalWidth = decodeResult.width,
                originalHeight = decodeResult.height,
                rawData = bytes,
                diskCachePath = diskPath,
              ),
            )
            return@flow
          }
          is DecodeResult.Error -> {
            // Disk cache corrupted, continue to network
            diskCache.remove(cacheKey)
          }
        }
      }
    }

    // 3. Fetch from network
    val fetchResult = fetcher.fetch(request)

    when (fetchResult) {
      is FetchResult.Success -> {
        var diskPath: String? = null

        // Save to disk cache first
        if (request.diskCachePolicy.writeEnabled && diskCache != null) {
          diskCache.edit(cacheKey)?.use { editor ->
            diskCache.fileSystem.sink(editor.dataPath).buffer().use { sink ->
              sink.write(fetchResult.data)
            }
            editor.commit()
            diskPath = (diskCache.directory / cacheKey.diskKey).toString()
          }
        }

        // Decode
        val decodeResult = decoder.decode(
          data = fetchResult.data,
          mimeType = fetchResult.mimeType,
          targetWidth = request.targetWidth,
          targetHeight = request.targetHeight,
          config = config,
        )

        when (decodeResult) {
          is DecodeResult.Success -> {
            val bitmap = applyTransformations(decodeResult.bitmap, request)

            // Update memory cache
            if (request.memoryCachePolicy.writeEnabled) {
              memoryCache[cacheKey] = CachedImage(
                data = bitmap,
                dataSource = DataSource.NETWORK,
                sizeBytes = estimateBitmapSize(decodeResult.width, decodeResult.height),
              )
            }

            emit(
              ImageResult.Success(
                data = bitmap,
                dataSource = DataSource.NETWORK,
                originalWidth = decodeResult.width,
                originalHeight = decodeResult.height,
                rawData = fetchResult.data,
                diskCachePath = diskPath,
              ),
            )
          }
          is DecodeResult.Error -> {
            emit(ImageResult.Failure(decodeResult.throwable))
          }
        }
      }

      is FetchResult.Error -> {
        emit(ImageResult.Failure(fetchResult.throwable))
      }
    }
  }.flowOn(dispatcher)

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

    return withContext(dispatcher) {
      request.transformations.fold(bitmap) { current, transformation ->
        transformation.transform(current)
      }
    }
  }

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
    private var memoryCache: MemoryCache? = null
    private var diskCache: DiskCache? = null
    private var fetcher: ImageFetcher? = null
    private var decoder: ImageDecoder? = null
    private var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /** Sets the configuration. */
    public fun config(config: LandscapistConfig): Builder = apply {
      this.config = config
    }

    /** Sets a custom memory cache. */
    public fun memoryCache(cache: MemoryCache): Builder = apply {
      this.memoryCache = cache
    }

    /** Sets a custom disk cache. */
    public fun diskCache(cache: DiskCache): Builder = apply {
      this.diskCache = cache
    }

    /** Sets a custom network fetcher. */
    public fun fetcher(fetcher: ImageFetcher): Builder = apply {
      this.fetcher = fetcher
    }

    /** Sets a custom image decoder. */
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

      val finalDiskCache = diskCache
        ?: config.diskCache
        ?: createDefaultDiskCache(config.diskCacheSize)

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
