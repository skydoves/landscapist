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
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.skydoves.landscapist.coil3.CoilImage

/**
 * Coil's own composable, so the row labelled Coil is Coil. It used to be [CoilWrapperImageList],
 * which is this library built on `BoxWithConstraints`, so the row compared landscapist to itself.
 *
 * The content scale is stated rather than defaulted: `AsyncImage` defaults to `ContentScale.Fit`
 * and `ImageOptions` to `Crop`, and a letterboxed row draws fewer pixels than a cropped one.
 */
@Composable
internal fun CoilAsyncImageList(urls: List<String>, tag: String, modifier: Modifier = Modifier) {
  BenchmarkList(urls, tag, modifier) { url, itemModifier ->
    AsyncImage(
      model = url,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = itemModifier,
    )
  }
}

/** landscapist-coil3, which is this library's Compose layer over the same Coil engine. */
@Composable
internal fun CoilWrapperImageList(urls: List<String>, tag: String, modifier: Modifier = Modifier) {
  BenchmarkList(urls, tag, modifier) { url, itemModifier ->
    CoilImage(imageModel = { url }, modifier = itemModifier)
  }
}
