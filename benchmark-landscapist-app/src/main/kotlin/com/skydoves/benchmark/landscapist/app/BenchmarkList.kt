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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The list every variant is measured through: the same rows, the same height and the same tags, so
 * what is left between two rows is the image composable and nothing else.
 *
 * Item zero carries `<tag>First` and the rest carry `<tag>Image`. That is how the driver tells a
 * list that scrolled from one that never moved, which used to pass silently.
 *
 * `<tag>Loaded` appears once [ROWS_BEFORE_READY] rows have reported an image. An item tag says a
 * row composed and nothing more, so a variant whose images all failed would have scrolled past
 * every check and reported the frame timing of an empty list.
 */
@Composable
internal fun BenchmarkList(
  urls: List<String>,
  tag: String,
  modifier: Modifier,
  content: @Composable (url: String, itemModifier: Modifier, onLoaded: () -> Unit) -> Unit,
) {
  val firstTag = remember(tag) { "${tag}First" }
  val itemTag = remember(tag) { "${tag}Image" }
  val loadedTag = remember(tag) { "${tag}Loaded" }

  // A plain set, written from whichever thread a library reports on and never read by the
  // composition. Snapshot state here would recompose the list while it is being measured, and a
  // library that reports during composition would write to state that had already been read.
  val loaded = remember(tag) { mutableSetOf<String>() }
  var ready by remember(tag) { mutableStateOf(false) }
  LaunchedEffect(tag) {
    // Polled on the frame clock and only until it flips, so the cost is bounded by how long the
    // images take and is gone before the scroll that gets measured.
    while (!ready) {
      withFrameNanos { }
      ready = synchronized(loaded) { loaded.size } >= ROWS_BEFORE_READY
    }
  }

  Box(modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize()) {
      itemsIndexed(urls) { index, url ->
        content(
          url,
          Modifier
            .fillMaxWidth()
            .height(BenchmarkImages.ITEM_HEIGHT_DP.dp)
            .testTag(if (index == 0) firstTag else itemTag),
        ) { synchronized(loaded) { loaded.add(url) } }
      }
    }
    if (ready) {
      Spacer(Modifier.size(1.dp).testTag(loadedTag))
    }
  }
}

/** Enough rows to prove the variant loads, and few enough that a small screen still reaches it. */
private const val ROWS_BEFORE_READY = 3
