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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.github.skydoves.landscapistdemo.web.theme.LandscapistDemoTheme
import com.github.skydoves.landscapistdemo.web.theme.purple200

private const val REPOSITORY_URL: String = "https://github.com/skydoves/landscapist"
private const val DOCUMENTATION_URL: String = "https://skydoves.github.io/landscapist/"

/**
 * The whole demo. One screen, deliberately: the point is the controls, not navigation.
 */
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

  LandscapistDemoTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
      ) {
        // Capped, because the browser window can be far wider than any phone and a playground
        // stretched across a desktop monitor puts every control a mouse journey apart.
        //
        // The scroll lives here rather than inside the playground so the header and the footer
        // travel with it.
        Column(
          modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        ) {
          DemoHeader()
          PlaygroundScreen()
          DemoFooter()
        }
      }
    }
  }
}

@Composable
private fun DemoHeader() {
  Text(
    text = "Landscapist playground",
    style = MaterialTheme.typography.h1,
    fontSize = 22.sp,
    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp),
  )
  LinkRow(
    links = listOf(
      "GitHub" to REPOSITORY_URL,
      "Documentation" to DOCUMENTATION_URL,
    ),
  )
  HintText(
    "Every control below drives one real load through landscapist-image, compiled to " +
      "WebAssembly and running in this tab. The readout is what the loader reported.",
  )
}

@Composable
private fun DemoFooter() {
  SectionHeader("Landscapist")
  HintText(
    "The source of this page is the :demo-web module. It is a port of the Android sample's " +
      "playground, so the two read alike without being kept in step.",
  )
  LinkRow(
    links = listOf(
      "github.com/skydoves/landscapist" to REPOSITORY_URL,
      "Documentation" to DOCUMENTATION_URL,
    ),
  )
  Spacer(modifier = Modifier.height(32.dp))
}

/** Underlined text that opens a url in a new tab, which on web is what the platform does. */
@Composable
private fun LinkRow(links: List<Pair<String, String>>) {
  val uriHandler = LocalUriHandler.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    links.forEach { (label, url) ->
      Text(
        text = label,
        color = purple200,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { uriHandler.openUri(url) },
      )
    }
  }
}
