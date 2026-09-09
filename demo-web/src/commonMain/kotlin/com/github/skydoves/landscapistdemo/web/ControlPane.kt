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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.skydoves.landscapistdemo.web.design.ActionButton
import com.github.skydoves.landscapistdemo.web.design.Chip
import com.github.skydoves.landscapistdemo.web.design.ChipGroup
import com.github.skydoves.landscapistdemo.web.design.Hint
import com.github.skydoves.landscapistdemo.web.design.Panel
import com.github.skydoves.landscapistdemo.web.design.SectionTitle
import com.github.skydoves.landscapistdemo.web.design.Segmented
import com.github.skydoves.landscapistdemo.web.design.VSpace

/** Every switch and choice, grouped one topic to a panel. */
@Composable
internal fun ControlPane(state: PlaygroundState, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SourcePanel(state)
    ImagePanel(state)
    LayoutPanel(state)
    PluginPanel(state)
  }
}

@Composable
private fun ControlPanel(title: String, content: @Composable () -> Unit) {
  Panel(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(14.dp)) {
      SectionTitle(title)
      VSpace(10)
      content()
    }
  }
}

@Composable
private fun SourcePanel(state: PlaygroundState) {
  ControlPanel("Where it loads from") {
    Segmented(
      options = LoadSource.entries,
      selected = state.source,
      label = { it.label },
      onSelected = state::select,
      modifier = Modifier.fillMaxWidth(),
    )
    VSpace(8)
    Hint(state.source.hint)
    VSpace(10)
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
      ActionButton(label = "Reload", primary = true, onClick = { state.reload() })
      ActionButton(label = "Clear memory", onClick = { state.reload(clearMemory = true) })
      ActionButton(label = "Clear all", onClick = state::clearEverything)
    }
    VSpace(8)
    Hint(
      "There is no disk cache in a browser, so the memory cache is the only one here and " +
        "everything it loses goes back to the network.",
    )
  }
}

@Composable
private fun ImagePanel(state: PlaygroundState) {
  ControlPanel("Image") {
    Segmented(
      options = playgroundImageUrls.indices.toList(),
      selected = state.urlIndex,
      label = { "Image ${it + 1}" },
      onSelected = state::select,
      modifier = Modifier.fillMaxWidth(),
    )
    VSpace(8)
    Hint("Switching to an image that was never loaded is the only way to see a cold start.")
  }
}

@Composable
private fun LayoutPanel(state: PlaygroundState) {
  ControlPanel("Layout") {
    Hint("Size")
    VSpace(6)
    Segmented(
      options = SizeMode.entries,
      selected = state.sizeMode,
      label = { it.label },
      onSelected = { state.sizeMode = it },
      modifier = Modifier.fillMaxWidth(),
    )
    VSpace(12)
    Hint("Content scale")
    VSpace(6)
    Segmented(
      options = ScaleMode.entries,
      selected = state.scaleMode,
      label = { it.label },
      onSelected = { state.scaleMode = it },
      modifier = Modifier.fillMaxWidth(),
    )
    VSpace(10)
    Hint(
      "The wasm decoder hands the whole encoded image to Skia rather than sampling it down, so " +
        "the decoded size stays the source size whatever is picked here. The layout still " +
        "changes; only the decode does not.",
    )
  }
}

@Composable
private fun PluginPanel(state: PlaygroundState) {
  val toggles = state.toggles
  ControlPanel("Plugins") {
    ChipGroup(modifier = Modifier.fillMaxWidth()) {
      Chip("Crossfade", toggles.crossfade) { state.toggles = toggles.copy(crossfade = it) }
      Chip("CircularReveal", toggles.circularReveal) {
        state.toggles = toggles.copy(circularReveal = it)
      }
      Chip("Shimmer", toggles.shimmer) { state.toggles = toggles.copy(shimmer = it) }
      Chip("Zoomable", toggles.zoomable) { state.toggles = toggles.copy(zoomable = it) }
      Chip("BlurHash", toggles.blurHash) { state.toggles = toggles.copy(blurHash = it) }
      Chip("ThumbHash", toggles.thumbHash) { state.toggles = toggles.copy(thumbHash = it) }
      Chip("Thumbnail", toggles.thumbnail) { state.toggles = toggles.copy(thumbnail = it) }
      Chip("Placeholder", toggles.placeholder) { state.toggles = toggles.copy(placeholder = it) }
      Chip("Progressive", toggles.progressive) { state.toggles = toggles.copy(progressive = it) }
    }
    VSpace(10)
    Hint(
      "Loading placeholders only show while a load is actually running. Pick the Network " +
        "source, which disables the cache, to make one happen every time.",
    )
    if (toggles.zoomable) {
      VSpace(6)
      Hint(
        "Zoomable consumes the pointer down, so dragging over the image no longer scrolls the " +
          "page. Tiling is off in a browser, which cannot decode a region, so this is pan and " +
          "zoom over the one decoded bitmap.",
      )
    }
    VSpace(6)
    Hint(
      "Palette and BlurTransformation are missing against the Android sample. kmpalette " +
        "publishes no wasm artifact, and the blur is a native Android library.",
    )
  }
}
