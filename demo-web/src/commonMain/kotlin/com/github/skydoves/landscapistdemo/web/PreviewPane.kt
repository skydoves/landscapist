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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.skydoves.landscapistdemo.web.design.DemoColors
import com.github.skydoves.landscapistdemo.web.design.DemoType
import com.github.skydoves.landscapistdemo.web.design.Divider
import com.github.skydoves.landscapistdemo.web.design.Panel
import com.github.skydoves.landscapistdemo.web.design.SectionTitle
import com.github.skydoves.landscapistdemo.web.design.StatRow
import com.github.skydoves.landscapistdemo.web.design.StatusPill
import com.github.skydoves.landscapistdemo.web.design.VSpace
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.placeholder.blurhash.BlurHashPlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.progressive.ProgressiveLoadingPlugin
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.placeholder.thumbhash.ThumbHashPlugin
import com.skydoves.landscapist.placeholder.thumbnail.ThumbnailPlugin
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import com.skydoves.landscapist.zoomable.rememberZoomableState

/** The image under test, on a checkerboard, with the loader's own report underneath. */
@Composable
internal fun PreviewPane(state: PlaygroundState, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Panel(modifier = Modifier.fillMaxWidth()) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          SectionTitle("Preview")
          StatusPill(text = state.readout.state, color = state.readout.statusColor)
        }
        Divider()
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .checkerboard()
            .padding(20.dp),
          contentAlignment = Alignment.Center,
        ) {
          Subject(state)
        }
        Divider()
        Column(modifier = Modifier.padding(14.dp)) {
          Readout(state)
        }
      }
    }
  }
}

/** The image itself, sized by the current mode and outlined so the box it fills is visible. */
@Composable
private fun Subject(state: PlaygroundState) {
  val zoomableState = rememberZoomableState(
    config = ZoomableConfig(maxZoom = 8f, doubleTapZoom = 3f),
    resetKey = state.url,
  )
  val thumbHashPlugin = remember { ThumbHashPlugin.fromBase64(SAMPLE_THUMB_HASH) }
  val toggles = state.toggles

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
      if (toggles.zoomable) {
        +ZoomablePlugin(state = zoomableState)
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
        // Flat colours rather than the Android sample's material icons: material-icons-core is
        // not published for Compose Multiplatform 1.12, so there is no icon set to draw from.
        +PlaceholderPlugin.Loading(ColorPainter(Color(0xFF3A3A3A)))
        +PlaceholderPlugin.Failure(ColorPainter(Color(0xFF7A1F1F)))
      }
      if (toggles.progressive) {
        +ProgressiveLoadingPlugin()
      }
    }
  }

  val requestBuilder = remember(state.source, toggles.progressive) {
    playgroundRequestBuilder(
      bypassCaches = state.source == LoadSource.Network,
      progressive = toggles.progressive,
    )
  }
  val imageOptions = remember(state.scaleMode) { imageOptionsFor(state.contentScale) }

  val sizeModifier = when (state.sizeMode) {
    SizeMode.Fixed -> Modifier.size(200.dp)
    SizeMode.FillWidth -> Modifier.fillMaxWidth()
    SizeMode.AspectRatio -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    SizeMode.Unsized -> Modifier
  }

  key(state.reloadNonce) {
    LandscapistImage(
      imageModel = { state.url },
      modifier = sizeModifier.border(1.dp, DemoColors.AccentBorder),
      landscapist = state.landscapist,
      requestBuilder = requestBuilder,
      component = component,
      imageOptions = imageOptions,
      onImageStateChanged = state::onStateChanged,
    )
  }
}

@Composable
private fun Readout(state: PlaygroundState) {
  val readout = state.readout
  StatRow(
    label = "decoded",
    value = if (readout.width > 0 || readout.height > 0) {
      "${readout.width} x ${readout.height}"
    } else {
      "-"
    },
  )
  StatRow(
    label = "source",
    value = readout.dataSource,
    valueColor = if (readout.dataSource == "MEMORY") DemoColors.Success else DemoColors.Text,
  )
  StatRow(
    label = "elapsed",
    value = if (readout.elapsedMs >= 0) "${readout.elapsedMs} ms" else "-",
  )
  if (readout.failure != null) {
    StatRow(label = "failure", value = readout.failure, valueColor = DemoColors.Danger)
  }
  VSpace(6)
  Text(
    text = state.url,
    style = DemoType.Mono,
    color = DemoColors.TextFaint,
  )
}

private val ImageReadout.statusColor: Color
  get() = when (state) {
    "Success" -> DemoColors.Success
    "Failure" -> DemoColors.Danger
    "Loading" -> DemoColors.Pending
    else -> DemoColors.TextFaint
  }

/**
 * The two tone grid behind the preview.
 *
 * An image library's demo needs the edges of the box the image was given to be visible, otherwise
 * `Crop`, `Fit` and `Inside` are hard to tell apart against a flat background.
 */
private fun Modifier.checkerboard(square: Float = 12f): Modifier = this
  .clip(RoundedCornerShape(0.dp))
  .background(DemoColors.CheckerDark)
  .drawBehind {
    val columns = (size.width / square).toInt() + 1
    val rows = (size.height / square).toInt() + 1
    for (row in 0 until rows) {
      for (column in 0 until columns) {
        if ((row + column) % 2 == 0) {
          drawRect(
            color = DemoColors.CheckerLight,
            topLeft = Offset(column * square, row * square),
            size = Size(square, square),
          )
        }
      }
    }
  }
