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
package com.github.skydoves.landscapistdemo.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.image.getLandscapist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Everything the playground lets you change, and what the last load reported back.
 *
 * It lives above the panes rather than inside one, because on a wide window the preview and the
 * controls are two columns side by side and both read the same state.
 */
@Stable
internal class PlaygroundState(
  val landscapist: Landscapist,
  private val scope: CoroutineScope,
) {
  var toggles: PluginToggles by mutableStateOf(PluginToggles())
  var sizeMode: SizeMode by mutableStateOf(SizeMode.AspectRatio)
  var scaleMode: ScaleMode by mutableStateOf(ScaleMode.Crop)
  var source: LoadSource by mutableStateOf(LoadSource.Memory)
  var urlIndex: Int by mutableIntStateOf(0)

  /**
   * Bumped to restart a load. The image lives inside `key(reloadNonce)`, so changing it drops the
   * whole subtree and its remembered request, which is the only way to ask for the same url twice.
   */
  var reloadNonce: Int by mutableIntStateOf(0)
    private set

  var readout: ImageReadout by mutableStateOf(ImageReadout())
    private set

  private var startedAt by mutableStateOf(TimeSource.Monotonic.markNow())

  val url: String
    get() = if (source == LoadSource.Failure) FAILING_IMAGE_URL else playgroundImageUrls[urlIndex]

  val contentScale: ContentScale
    get() = scaleMode.contentScale

  fun onStateChanged(state: LandscapistImageState) {
    readout = state.toReadout(startedAt.elapsedNow().inWholeMilliseconds)
  }

  fun reload(clearMemory: Boolean = false) {
    if (clearMemory) {
      landscapist.clearMemoryCache()
    }
    readout = ImageReadout()
    startedAt = TimeSource.Monotonic.markNow()
    reloadNonce++
  }

  fun clearEverything() {
    scope.launch {
      landscapist.clearCaches()
      reload()
    }
  }

  fun select(source: LoadSource) {
    this.source = source
    reload()
  }

  fun select(index: Int) {
    urlIndex = index
    reload()
  }
}

@Composable
internal fun rememberPlaygroundState(): PlaygroundState {
  val landscapist = getLandscapist()
  val scope = rememberCoroutineScope()
  return remember(landscapist, scope) { PlaygroundState(landscapist, scope) }
}

/**
 * The request customisations that are not plugins. Built outside composition and remembered, so
 * the request is stable and the load does not restart every frame.
 */
internal fun playgroundRequestBuilder(
  bypassCaches: Boolean,
  progressive: Boolean,
): (ImageRequest.Builder.() -> Unit)? {
  if (!bypassCaches && !progressive) return null
  return {
    if (bypassCaches) {
      memoryCachePolicy(CachePolicy.DISABLED)
      diskCachePolicy(CachePolicy.DISABLED)
    }
    if (progressive) {
      progressiveEnabled(true)
    }
  }
}

internal fun imageOptionsFor(scale: ContentScale): ImageOptions = ImageOptions(contentScale = scale)

/** Turns a loader state into the numbers the readout shows. */
private fun LandscapistImageState.toReadout(elapsedMs: Long): ImageReadout = when (this) {
  is LandscapistImageState.None -> ImageReadout(state = "None")
  is LandscapistImageState.Loading -> ImageReadout(state = "Loading")
  is LandscapistImageState.Success -> ImageReadout(
    state = "Success",
    width = originalWidth,
    height = originalHeight,
    dataSource = dataSource.name,
    elapsedMs = elapsedMs,
  )
  is LandscapistImageState.Failure -> ImageReadout(
    state = "Failure",
    elapsedMs = elapsedMs,
    failure = reason?.message ?: reason?.let { it::class.simpleName } ?: "unknown",
  )
}
