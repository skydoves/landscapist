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
package com.skydoves.landscapist.core.event

/**
 * Listener for image loading events.
 * Implement this interface to monitor and debug image loading operations.
 */
public interface EventListener {

  /**
   * Called when an image loading event occurs.
   *
   * @param event The event that occurred.
   */
  public fun onEvent(event: ImageLoadEvent)

  /**
   * Factory for creating [EventListener] instances.
   * This allows creating listeners with per-request context.
   */
  public fun interface Factory {
    /**
     * Creates an [EventListener] for a specific request.
     *
     * @param model The image model being loaded.
     * @return An EventListener instance, or null to skip listening.
     */
    public fun create(model: Any?): EventListener?
  }

  public companion object {
    /**
     * A no-op event listener that does nothing.
     */
    public val NONE: EventListener = object : EventListener {
      override fun onEvent(event: ImageLoadEvent) {}
    }

    /**
     * A factory that always returns [NONE].
     */
    public val NONE_FACTORY: Factory = Factory { NONE }
  }
}

/**
 * A simple event listener that logs events using a provided logging function.
 */
public class LoggingEventListener(
  private val tag: String = "Landscapist",
  private val logger: (String) -> Unit = { println(it) },
) : EventListener {

  override fun onEvent(event: ImageLoadEvent) {
    val message = when (event) {
      is ImageLoadEvent.Started ->
        "[$tag] Started: ${event.model}"
      is ImageLoadEvent.MemoryCacheHit ->
        "[$tag] Memory cache hit: ${event.model} (${event.sizeBytes} bytes)"
      is ImageLoadEvent.DiskCacheHit ->
        "[$tag] Disk cache hit: ${event.model} (${event.sizeBytes} bytes)"
      is ImageLoadEvent.FetchStarted ->
        "[$tag] Fetch started: ${event.url}"
      is ImageLoadEvent.FetchCompleted ->
        "[$tag] Fetch completed: ${event.model} (${event.sizeBytes}b/${event.durationMs}ms)"
      is ImageLoadEvent.DecodeStarted ->
        "[$tag] Decode started: ${event.model}"
      is ImageLoadEvent.DecodeCompleted ->
        "[$tag] Decode: ${event.model} (${event.width}x${event.height}/${event.durationMs}ms)"
      is ImageLoadEvent.Success ->
        "[$tag] Success: ${event.model} from ${event.dataSource} (${event.totalDurationMs}ms)"
      is ImageLoadEvent.Failure ->
        "[$tag] Failure: ${event.model} - ${event.throwable.message}"
      is ImageLoadEvent.Cancelled ->
        "[$tag] Cancelled: ${event.model}"
      is ImageLoadEvent.MemoryCacheWrite ->
        "[$tag] Memory cache write: ${event.model} (${event.sizeBytes} bytes)"
      is ImageLoadEvent.DiskCacheWrite ->
        "[$tag] Disk cache write: ${event.model} (${event.sizeBytes} bytes)"
    }
    logger(message)
  }

  public companion object {
    /**
     * Creates a factory that produces [LoggingEventListener] instances.
     */
    public fun factory(
      tag: String = "Landscapist",
      logger: (String) -> Unit = { println(it) },
    ): EventListener.Factory = EventListener.Factory { LoggingEventListener(tag, logger) }
  }
}

/**
 * Combines multiple event listeners into one.
 */
public class CompositeEventListener(
  private val listeners: List<EventListener>,
) : EventListener {

  override fun onEvent(event: ImageLoadEvent) {
    listeners.forEach { it.onEvent(event) }
  }

  public companion object {
    /**
     * Creates a factory that combines multiple listener factories.
     */
    public fun factory(vararg factories: EventListener.Factory): EventListener.Factory =
      EventListener.Factory { model ->
        val listeners = factories.mapNotNull { it.create(model) }
        if (listeners.isEmpty()) null else CompositeEventListener(listeners)
      }
  }
}

/**
 * Event listener that collects performance metrics.
 */
public class MetricsEventListener : EventListener {

  private var startTime: Long = 0
  private var fetchStartTime: Long = 0
  private var decodeStartTime: Long = 0

  /** Total duration of the last completed request in milliseconds. */
  public var totalDurationMs: Long = 0
    private set

  /** Fetch duration of the last completed request in milliseconds. */
  public var fetchDurationMs: Long = 0
    private set

  /** Decode duration of the last completed request in milliseconds. */
  public var decodeDurationMs: Long = 0
    private set

  /** Whether the last request hit the memory cache. */
  public var wasMemoryCacheHit: Boolean = false
    private set

  /** Whether the last request hit the disk cache. */
  public var wasDiskCacheHit: Boolean = false
    private set

  /** The data source of the last completed request. */
  public var lastDataSource: com.skydoves.landscapist.core.model.DataSource? = null
    private set

  override fun onEvent(event: ImageLoadEvent) {
    when (event) {
      is ImageLoadEvent.Started -> {
        startTime = event.timestamp
        wasMemoryCacheHit = false
        wasDiskCacheHit = false
      }
      is ImageLoadEvent.MemoryCacheHit -> wasMemoryCacheHit = true
      is ImageLoadEvent.DiskCacheHit -> wasDiskCacheHit = true
      is ImageLoadEvent.FetchStarted -> fetchStartTime = event.timestamp
      is ImageLoadEvent.FetchCompleted -> fetchDurationMs = event.durationMs
      is ImageLoadEvent.DecodeStarted -> decodeStartTime = event.timestamp
      is ImageLoadEvent.DecodeCompleted -> decodeDurationMs = event.durationMs
      is ImageLoadEvent.Success -> {
        totalDurationMs = event.totalDurationMs
        lastDataSource = event.dataSource
      }
      else -> {}
    }
  }
}
