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
package com.skydoves.benchmark.landscapist.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.transformation.blur.BlurTransformationPlugin
import com.skydoves.landscapist.zoomable.ZoomablePlugin

/**
 * No plugins, no state slots and no drawable model, which is the only shape `canOwnNode` accepts.
 * This is the row that exercises the single layout node the branch adds.
 */
@Composable
internal fun LandscapistImageList(urls: List<String>, tag: String, modifier: Modifier = Modifier) {
  BenchmarkList(urls, tag, modifier) { url, itemModifier, onLoaded ->
    LandscapistImage(
      imageModel = { url },
      modifier = itemModifier,
      onImageStateChanged = { if (it is LandscapistImageState.Success) onLoaded() },
    )
  }
}

/**
 * The same composable with eight plugins installed, which is a different measurement rather than a
 * slower version of the one above: a plugin set makes `canOwnNode` false, so this row is the
 * composed path. It is kept separate so it can never be mistaken for the node path or for Coil.
 */
@Composable
internal fun LandscapistPluginImageList(
  urls: List<String>,
  tag: String,
  modifier: Modifier = Modifier,
) {
  val component = rememberImageComponent {
    +PlaceholderPlugin.Loading(painterResource(id = R.drawable.poster))
    +PlaceholderPlugin.Failure(painterResource(id = R.drawable.poster))
    +ShimmerPlugin()
    +ZoomablePlugin()
    +CrossfadePlugin()
    +CircularRevealPlugin()
    +BlurTransformationPlugin()
    +PalettePlugin()
  }
  BenchmarkList(urls, tag, modifier) { url, itemModifier, onLoaded ->
    LandscapistImage(
      imageModel = { url },
      component = component,
      modifier = itemModifier,
      onImageStateChanged = { if (it is LandscapistImageState.Success) onLoaded() },
    )
  }
}
