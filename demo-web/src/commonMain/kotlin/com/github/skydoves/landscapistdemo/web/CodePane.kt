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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.skydoves.landscapistdemo.web.design.ActionButton
import com.github.skydoves.landscapistdemo.web.design.DemoColors
import com.github.skydoves.landscapistdemo.web.design.DemoType
import com.github.skydoves.landscapistdemo.web.design.Divider
import com.github.skydoves.landscapistdemo.web.design.Panel
import com.github.skydoves.landscapistdemo.web.design.SectionTitle
import kotlinx.coroutines.launch

/**
 * The call that would produce what the preview is showing, with a button to take it away.
 *
 * This is the part that makes the page worth opening twice: the controls describe a configuration,
 * and this is that configuration in a form that compiles.
 */
@Composable
internal fun CodePane(state: PlaygroundState, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  var copyState by remember { mutableStateOf(CopyState.Idle) }
  val snippet = remember(
    state.url,
    state.sizeMode,
    state.scaleMode,
    state.source,
    state.toggles,
  ) {
    buildSnippet(
      url = state.url,
      sizeMode = state.sizeMode,
      scaleMode = state.scaleMode,
      source = state.source,
      toggles = state.toggles,
    )
  }

  Panel(modifier = modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        SectionTitle("The code for this")
        ActionButton(
          label = copyState.label,
          onClick = {
            scope.launch {
              copyState = if (writeToClipboard(snippet)) CopyState.Copied else CopyState.Refused
            }
          },
        )
      }
      Divider()
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(DemoColors.SurfaceSunken)
          .horizontalScroll(rememberScrollState())
          .padding(14.dp),
      ) {
        Text(text = snippet, style = DemoType.Mono, color = DemoColors.Text)
      }
      Divider()
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = "Every branch above mirrors one in the preview, so what you copy is what you see.",
          style = DemoType.Hint,
          color = DemoColors.TextMuted,
        )
      }
    }
  }

  // The button goes back to "Copy" as soon as the configuration moves on, so it never claims a
  // copy of something the page is no longer showing.
  LaunchedEffect(snippet) { copyState = CopyState.Idle }
}

/** What the copy button last did. A refusal is shown rather than swallowed. */
private enum class CopyState(val label: String) {
  Idle("Copy"),
  Copied("Copied"),
  Refused("Blocked by the browser"),
}
