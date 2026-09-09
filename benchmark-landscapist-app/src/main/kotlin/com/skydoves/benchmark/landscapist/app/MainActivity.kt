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
@file:OptIn(ExperimentalComposeUiApi::class)

package com.skydoves.benchmark.landscapist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

/**
 * Benchmark host. Selecting a tab shows a scrolling [androidx.compose.foundation.lazy.LazyColumn]
 * of distinct images for that variant, and the Macrobenchmark driver navigates by `By.text` and
 * scrolls `By.scrollable(true)` while recording frame timing. `testTagsAsResourceId` surfaces each
 * item's `testTag` as `By.res(packageName, "<Tab>Image")`, with item zero as `"<Tab>First"`.
 *
 * Nothing is selected until a tab is clicked. Defaulting to the first tab meant that under
 * `StartupMode.WARM` the Landscapist list composed and fetched inside every measured block,
 * including Coil's, which is a head start no other variant had.
 *
 * The six tabs say what they are. [LANDSCAPIST] and [COIL] are the pair to compare: the first is
 * `landscapist-image` with no plugins, which is the only shape that takes the node path, and the
 * second is Coil's own `AsyncImage`. [PLUGINS] is the same landscapist composable with eight
 * plugins, which forces the composed path and is a separate measurement rather than a slower one.
 * The three wrapper tabs are this library's Compose layers over the Coil, Glide and Fresco engines.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val urls = remember { BenchmarkImages.urls() }
      val rows = remember { TABS.chunked(TABS_PER_ROW) }
      var selectedTab by remember { mutableStateOf<String?>(null) }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .semantics { testTagsAsResourceId = true },
      ) {
        rows.forEach { row ->
          Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { tab ->
              BasicText(
                text = tab,
                modifier = Modifier
                  .weight(1f)
                  .clickable { selectedTab = tab }
                  .padding(vertical = 16.dp),
              )
            }
          }
        }

        val listModifier = Modifier.weight(1f)
        when (selectedTab) {
          LANDSCAPIST -> LandscapistImageList(urls, LANDSCAPIST, listModifier)
          COIL -> CoilAsyncImageList(urls, COIL, listModifier)
          PLUGINS -> LandscapistPluginImageList(urls, PLUGINS, listModifier)
          COIL_WRAPPER -> CoilWrapperImageList(urls, COIL_WRAPPER, listModifier)
          GLIDE_WRAPPER -> GlideWrapperImageList(urls, GLIDE_WRAPPER, listModifier)
          FRESCO_WRAPPER -> FrescoWrapperImageList(urls, FRESCO_WRAPPER, listModifier)
        }
      }
    }
  }

  companion object {
    const val LANDSCAPIST = "Landscapist"
    const val COIL = "Coil"
    const val PLUGINS = "Plugins"
    const val COIL_WRAPPER = "CoilWrapper"
    const val GLIDE_WRAPPER = "GlideWrapper"
    const val FRESCO_WRAPPER = "FrescoWrapper"

    private const val TABS_PER_ROW = 3

    private val TABS = listOf(
      LANDSCAPIST,
      COIL,
      PLUGINS,
      COIL_WRAPPER,
      GLIDE_WRAPPER,
      FRESCO_WRAPPER,
    )
  }
}
