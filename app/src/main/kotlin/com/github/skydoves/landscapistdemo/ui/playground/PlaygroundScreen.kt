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
package com.github.skydoves.landscapistdemo.ui.playground

import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.skydoves.landscapistdemo.ui.paletteColorList
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.image.getLandscapist
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.placeholder.blurhash.BlurHashPlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.progressive.ProgressiveLoadingPlugin
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.placeholder.thumbhash.ThumbHashPlugin
import com.skydoves.landscapist.placeholder.thumbnail.ThumbnailPlugin
import com.skydoves.landscapist.transformation.blur.BlurTransformationPlugin
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import com.skydoves.landscapist.zoomable.rememberZoomableState
import kotlinx.coroutines.launch

/**
 * Exercises every plugin, sizing mode and loading source against a real network image on a real
 * device, with a readout of what the loader reported. Driven only by public API.
 */
@Composable
fun PlaygroundScreen(paddingValues: PaddingValues) {
  val landscapist = getLandscapist()
  val scope = rememberCoroutineScope()

  var toggles by remember { mutableStateOf(PluginToggles()) }
  var sizeMode by remember { mutableStateOf(SizeMode.Fixed) }
  var scaleMode by remember { mutableStateOf(ScaleMode.Crop) }
  var source by remember { mutableStateOf(LoadSource.Memory) }
  var urlIndex by remember { mutableIntStateOf(0) }
  var reloadNonce by remember { mutableIntStateOf(0) }
  var requestStartedAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
  var readout by remember { mutableStateOf(ImageReadout()) }
  var paletteColors by remember { mutableStateOf(emptyList<Int>()) }

  val url = if (source == LoadSource.Failure) {
    FAILING_IMAGE_URL
  } else {
    playgroundImageUrls[urlIndex]
  }

  // Restarts the load. The image lives inside `key(reloadNonce)`, so bumping it drops the whole
  // subtree and its remembered request, which is the only way to ask for the same url twice.
  val startLoad: (Boolean) -> Unit = { clearMemory ->
    if (clearMemory) {
      landscapist.clearMemoryCache()
    }
    paletteColors = emptyList()
    readout = ImageReadout()
    requestStartedAt = SystemClock.elapsedRealtime()
    reloadNonce++
  }

  // Cache policies and progressive streaming are request-level, not plugin-level, so they go
  // through the request builder rather than the component.
  val requestBuilder = remember(source, toggles.progressive) {
    playgroundRequestBuilder(
      bypassCaches = source == LoadSource.Network,
      progressive = toggles.progressive,
    )
  }

  val zoomableState = rememberZoomableState(
    config = ZoomableConfig(maxZoom = 8f, doubleTapZoom = 3f),
    resetKey = url,
  )
  val thumbHashPlugin = remember { ThumbHashPlugin.fromBase64(SAMPLE_THUMB_HASH) }

  // `rememberImageComponent` remembers without keys, so the component it hands back is the one
  // built on the first composition and no switch flipped afterwards would ever reach the image.
  // Keying the whole call on the toggles is what makes them take effect.
  val component = key(toggles) {
    rememberImageComponent {
      if (toggles.crossfade) {
        +CrossfadePlugin(duration = 450)
      }
      if (toggles.circularReveal) {
        +CircularRevealPlugin(duration = 600)
      }
      if (toggles.shimmer) {
        +ShimmerPlugin(
          shimmer = Shimmer.Resonate(
            baseColor = Color.DarkGray,
            highlightColor = Color.LightGray,
          ),
        )
      }
      if (toggles.palette) {
        +PalettePlugin { palette -> paletteColors = palette.paletteColorList() }
      }
      if (toggles.zoomable) {
        +ZoomablePlugin(state = zoomableState)
      }
      if (toggles.blur) {
        +BlurTransformationPlugin(radius = toggles.blurRadius)
      }
      if (toggles.blurHash) {
        +BlurHashPlugin(blurHash = SAMPLE_BLUR_HASH, width = 32, height = 32)
      }
      if (toggles.thumbHash && thumbHashPlugin != null) {
        +thumbHashPlugin
      }
      if (toggles.thumbnail) {
        +ThumbnailPlugin(requestSize = IntSize(20, 20))
      }
      if (toggles.placeholder) {
        +PlaceholderPlugin.Loading(Icons.Filled.Image)
        +PlaceholderPlugin.Failure(Icons.Filled.BrokenImage)
      }
      if (toggles.progressive) {
        +ProgressiveLoadingPlugin()
      }
    }
  }

  val imageOptions = remember(scaleMode) {
    ImageOptions(contentScale = scaleMode.contentScale)
  }

  val cacheActions: List<Pair<String, () -> Unit>> = listOf(
    "Reload" to { startLoad(source == LoadSource.Disk) },
    "Clear memory" to { startLoad(true) },
    "Clear disk" to {
      scope.launch {
        landscapist.clearDiskCache()
        startLoad(true)
      }
    },
    "Clear all" to {
      scope.launch {
        landscapist.clearCaches()
        startLoad(false)
      }
    },
  )

  val sizeModifier = when (sizeMode) {
    SizeMode.Fixed -> Modifier.size(200.dp)
    SizeMode.FillWidth -> Modifier.fillMaxWidth()
    SizeMode.AspectRatio ->
      Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
    SizeMode.Unsized -> Modifier
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(paddingValues),
  ) {
    SectionHeader("Subject")

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
    ) {
      key(reloadNonce) {
        LandscapistImage(
          imageModel = { url },
          modifier = sizeModifier.border(width = 1.dp, color = Color(0xFF9E9E9E)),
          landscapist = landscapist,
          requestBuilder = requestBuilder,
          component = component,
          imageOptions = imageOptions,
          onImageStateChanged = { state ->
            readout = state.toReadout(SystemClock.elapsedRealtime() - requestStartedAt)
          },
        )
      }
    }

    ReadoutPanel(readout = readout, url = url)

    if (paletteColors.isNotEmpty()) {
      PaletteStrip(paletteColors)
    }

    SectionHeader("Loading source")
    ChoiceRow(
      options = LoadSource.entries,
      selected = source,
      label = { it.label },
      onSelected = {
        source = it
        startLoad(it == LoadSource.Disk)
      },
    )
    HintText(source.hint)
    ActionRow(actions = cacheActions)

    SectionHeader("Image")
    ChoiceRow(
      options = playgroundImageUrls.indices.toList(),
      selected = urlIndex,
      label = { "Image ${it + 1}" },
      onSelected = {
        urlIndex = it
        startLoad(source == LoadSource.Disk)
      },
    )
    HintText("Switching to an image that was never loaded is the only way to see a cold start.")

    SectionHeader("Sizing")
    ChoiceRow(
      options = SizeMode.entries,
      selected = sizeMode,
      label = { it.label },
      onSelected = { sizeMode = it },
    )
    HintText(
      "The screen scrolls vertically, so \"Fill width\" and \"No size\" are measured with an " +
        "unbounded height.",
    )

    SectionHeader("Content scale")
    ChoiceRow(
      options = ScaleMode.entries,
      selected = scaleMode,
      label = { it.label },
      onSelected = { scaleMode = it },
    )

    SectionHeader("Plugins")
    HintText(
      "Loading placeholders only show while a load is actually running. Pick the Network " +
        "source, which disables both caches, to make one happen every time.",
    )
    PluginToggleList(
      toggles = toggles,
      onTogglesChanged = { toggles = it },
    )

    PlaygroundComparison(url = url, contentScale = scaleMode.contentScale)

    Spacer(modifier = Modifier.height(32.dp))
  }
}

/** Every plugin switch, plus the blur radius slider the blur plugin needs. */
@Composable
private fun PluginToggleList(
  toggles: PluginToggles,
  onTogglesChanged: (PluginToggles) -> Unit,
) {
  ToggleRow("Crossfade (450ms)", toggles.crossfade) {
    onTogglesChanged(toggles.copy(crossfade = it))
  }
  ToggleRow("CircularReveal (600ms)", toggles.circularReveal) {
    onTogglesChanged(toggles.copy(circularReveal = it))
  }
  ToggleRow("Shimmer (while loading)", toggles.shimmer) {
    onTogglesChanged(toggles.copy(shimmer = it))
  }
  ToggleRow("Palette (swatches above)", toggles.palette) {
    onTogglesChanged(toggles.copy(palette = it))
  }
  ToggleRow("Zoomable (pinch, double tap)", toggles.zoomable) {
    onTogglesChanged(toggles.copy(zoomable = it))
  }
  if (toggles.zoomable) {
    HintText(
      "The zoom detector consumes the pointer down, so dragging over the image no longer " +
        "scrolls the page.",
    )
  }
  ToggleRow("BlurTransformation", toggles.blur) {
    onTogglesChanged(toggles.copy(blur = it))
  }
  if (toggles.blur) {
    SliderRow(
      label = "Blur radius",
      value = toggles.blurRadius,
      range = 1f..25f,
      steps = 23,
    ) {
      onTogglesChanged(toggles.copy(blurRadius = it))
    }
  }
  ToggleRow("BlurHash placeholder", toggles.blurHash) {
    onTogglesChanged(toggles.copy(blurHash = it))
  }
  ToggleRow("ThumbHash placeholder", toggles.thumbHash) {
    onTogglesChanged(toggles.copy(thumbHash = it))
  }
  ToggleRow("Thumbnail (20x20 preview)", toggles.thumbnail) {
    onTogglesChanged(toggles.copy(thumbnail = it))
  }
  ToggleRow("Placeholder (loading + failure)", toggles.placeholder) {
    onTogglesChanged(toggles.copy(placeholder = it))
  }
  ToggleRow("ProgressiveLoading", toggles.progressive) {
    onTogglesChanged(toggles.copy(progressive = it))
  }
  HintText(
    "ProgressiveLoading also turns on the request's progressive streaming, so the readout " +
      "updates once per decode pass.",
  )
}

/** Turns a loader state into the numbers the readout panel shows. */
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

/**
 * The request customisations that are not plugins. Built outside composition and remembered, so
 * the request is stable and the load does not restart every frame.
 */
private fun playgroundRequestBuilder(
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
