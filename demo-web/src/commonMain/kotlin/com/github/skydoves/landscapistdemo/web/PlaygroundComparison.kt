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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState

/**
 * The same url, size and content scale through Landscapist and through Coil. Neither side has a
 * plugin or placeholder, so what differs is the loader.
 */
@Composable
internal fun PlaygroundComparison(url: String, contentScale: ContentScale) {
  var landscapistState by remember { mutableStateOf("None") }
  var coilState by remember { mutableStateOf<AsyncImagePainter.State?>(null) }

  val imageOptions = remember(contentScale) { ImageOptions(contentScale = contentScale) }
  val boxModifier = Modifier
    .fillMaxWidth()
    .height(150.dp)
    .border(width = 1.dp, color = Color(0xFF9E9E9E))

  SectionHeader("LandscapistImage vs Coil AsyncImage")
  HintText("The same url in the same 150dp box, with no plugins on either side.")

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
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

@Composable
private fun ComparisonColumn(
  modifier: Modifier,
  title: String,
  state: String,
  image: @Composable () -> Unit,
) {
  Column(modifier = modifier) {
    Text(
      text = title,
      style = MaterialTheme.typography.body1,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
    )
    image()
    Text(
      text = state,
      style = MaterialTheme.typography.body2,
      fontSize = 11.sp,
    )
  }
}

private fun LandscapistImageState.label(): String = when (this) {
  is LandscapistImageState.None -> "None"
  is LandscapistImageState.Loading -> "Loading"
  is LandscapistImageState.Success -> "Success (${dataSource.name})"
  is LandscapistImageState.Failure -> "Failure"
}

private fun AsyncImagePainter.State?.label(): String = when (this) {
  is AsyncImagePainter.State.Loading -> "Loading"
  is AsyncImagePainter.State.Success -> "Success (${result.dataSource.name})"
  is AsyncImagePainter.State.Error -> "Error"
  else -> "Empty"
}
