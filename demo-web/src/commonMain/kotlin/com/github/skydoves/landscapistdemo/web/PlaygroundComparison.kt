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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.github.skydoves.landscapistdemo.web.design.DemoColors
import com.github.skydoves.landscapistdemo.web.design.DemoType
import com.github.skydoves.landscapistdemo.web.design.Divider
import com.github.skydoves.landscapistdemo.web.design.Hint
import com.github.skydoves.landscapistdemo.web.design.Panel
import com.github.skydoves.landscapistdemo.web.design.SectionTitle
import com.github.skydoves.landscapistdemo.web.design.VSpace
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState

/**
 * The same url, size and content scale through Landscapist and through Coil. Neither side has a
 * plugin or placeholder, so what differs is the loader.
 */
@Composable
internal fun PlaygroundComparison(
  url: String,
  contentScale: ContentScale,
  modifier: Modifier = Modifier,
) {
  var landscapistState by remember { mutableStateOf("None") }
  var coilState by remember { mutableStateOf<AsyncImagePainter.State?>(null) }

  val imageOptions = remember(contentScale) { imageOptionsFor(contentScale) }
  val boxModifier = Modifier.fillMaxWidth().height(170.dp)

  Panel(modifier = modifier.fillMaxWidth()) {
    Column {
      Column(modifier = Modifier.padding(14.dp)) {
        SectionTitle("Side by side with Coil")
        VSpace(6)
        Hint("The same url in the same box, with no plugins on either side.")
      }
      Divider()
      Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        ComparisonColumn(
          modifier = Modifier.weight(1f),
          title = "Landscapist",
          state = landscapistState,
        ) {
          LandscapistImage(
            imageModel = { url },
            modifier = boxModifier,
            imageOptions = imageOptions,
            onImageStateChanged = { landscapistState = it.label() },
          )
        }
        Box(
          modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(DemoColors.Border),
        )
        ComparisonColumn(
          modifier = Modifier.weight(1f),
          title = "Coil",
          state = coilState.label(),
        ) {
          AsyncImage(
            model = url,
            contentDescription = null,
            modifier = boxModifier,
            onState = { coilState = it },
            contentScale = contentScale,
          )
        }
      }
    }
  }
}

@Composable
private fun ComparisonColumn(
  modifier: Modifier,
  title: String,
  state: String,
  image: @Composable () -> Unit,
) {
  Column(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = title, style = DemoType.Label, color = DemoColors.Text)
      Text(text = state, style = DemoType.Mono, color = DemoColors.TextMuted)
    }
    image()
  }
}

private fun LandscapistImageState.label(): String = when (this) {
  is LandscapistImageState.None -> "None"
  is LandscapistImageState.Loading -> "Loading"
  is LandscapistImageState.Success -> dataSource.name
  is LandscapistImageState.Failure -> "Failure"
}

private fun AsyncImagePainter.State?.label(): String = when (this) {
  is AsyncImagePainter.State.Loading -> "Loading"
  is AsyncImagePainter.State.Success -> result.dataSource.name
  is AsyncImagePainter.State.Error -> "Error"
  else -> "Empty"
}
