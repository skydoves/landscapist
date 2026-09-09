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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The list every variant is measured through: the same rows, the same height and the same tags, so
 * what is left between two rows is the image composable and nothing else.
 *
 * Item zero carries `<tag>First` and the rest carry `<tag>Image`. That is how the driver tells a
 * list that scrolled from one that never moved, which used to pass silently.
 */
@Composable
internal fun BenchmarkList(
  urls: List<String>,
  tag: String,
  modifier: Modifier,
  content: @Composable (url: String, itemModifier: Modifier) -> Unit,
) {
  val firstTag = remember(tag) { "${tag}First" }
  val itemTag = remember(tag) { "${tag}Image" }
  LazyColumn(modifier = modifier.fillMaxSize()) {
    itemsIndexed(urls) { index, url ->
      content(
        url,
        Modifier
          .fillMaxWidth()
          .height(BenchmarkImages.ITEM_HEIGHT_DP.dp)
          .testTag(if (index == 0) firstTag else itemTag),
      )
    }
  }
}
