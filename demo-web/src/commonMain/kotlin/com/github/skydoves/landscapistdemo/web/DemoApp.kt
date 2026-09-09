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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.github.skydoves.landscapistdemo.web.design.DemoColors
import com.github.skydoves.landscapistdemo.web.design.DemoType
import com.github.skydoves.landscapistdemo.web.design.Divider
import com.github.skydoves.landscapistdemo.web.design.Hint
import com.github.skydoves.landscapistdemo.web.design.LinkText
import com.github.skydoves.landscapistdemo.web.design.VSpace

private const val REPOSITORY_URL: String = "https://github.com/skydoves/landscapist"
private const val DOCUMENTATION_URL: String = "https://skydoves.github.io/landscapist/"

/** Wider than this and the preview sits beside the controls instead of above them. */
private const val TwoColumnWidthDp = 900

@Composable
internal fun DemoApp() {
  // Coil has no network fetcher of its own on wasm, so the comparison column would report Error on
  // every url without this. Landscapist needs no equivalent: landscapist-core carries the Ktor js
  // engine for this target itself.
  setSingletonImageLoaderFactory { context ->
    ImageLoader.Builder(context)
      .components { add(KtorNetworkFetcherFactory()) }
      .build()
  }

  val state = rememberPlaygroundState()

  Column(modifier = Modifier.fillMaxSize().background(DemoColors.Background)) {
    TopBar()
    Divider()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
      val twoColumn = maxWidth >= TwoColumnWidthDp.dp
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(modifier = Modifier.widthIn(max = 1120.dp).fillMaxWidth().padding(20.dp)) {
          Masthead()
          VSpace(20)
          if (twoColumn) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
              Column(modifier = Modifier.weight(1.15f)) {
                PreviewPane(state)
                VSpace(20)
                CodePane(state)
              }
              ControlPane(state, modifier = Modifier.width(360.dp))
            }
          } else {
            PreviewPane(state)
            VSpace(12)
            ControlPane(state)
            VSpace(12)
            CodePane(state)
          }
          VSpace(20)
          PlaygroundComparison(url = state.url, contentScale = state.contentScale)
          VSpace(28)
          Footer()
          VSpace(24)
        }
      }
    }
  }
}

@Composable
private fun TopBar() {
  val uriHandler = LocalUriHandler.current
  Box(
    modifier = Modifier.fillMaxWidth().background(DemoColors.Surface),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier
        .widthIn(max = 1120.dp)
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 13.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Landscapist", style = DemoType.Wordmark, color = DemoColors.Text)
        Text(
          text = "  /  playground",
          style = DemoType.Wordmark,
          color = DemoColors.Accent,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LinkText("Docs", onClick = { uriHandler.openUri(DOCUMENTATION_URL) })
        LinkText(
          label = "GitHub",
          color = DemoColors.Accent,
          onClick = { uriHandler.openUri(REPOSITORY_URL) },
        )
      }
    }
  }
}

@Composable
private fun Masthead() {
  Text(
    text = "Every option, on one page",
    style = DemoType.Title,
    color = DemoColors.Text,
  )
  VSpace(8)
  Hint(
    "This is landscapist-image compiled to WebAssembly and running in this tab. Change the size, " +
      "the content scale, the plugins or where it loads from, and the panel under the image is " +
      "what the loader reported back. The code for whatever you land on is below it.",
    modifier = Modifier.widthIn(max = 680.dp),
  )
}

@Composable
private fun Footer() {
  val uriHandler = LocalUriHandler.current
  Divider()
  VSpace(14)
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "Built from the :demo-web module, a port of the Android sample's playground.",
      style = DemoType.Hint,
      color = DemoColors.TextFaint,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      LinkText("Documentation", onClick = { uriHandler.openUri(DOCUMENTATION_URL) })
      LinkText(
        label = "github.com/skydoves/landscapist",
        color = DemoColors.Accent,
        onClick = { uriHandler.openUri(REPOSITORY_URL) },
      )
    }
  }
}
