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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.LocalImageComponent
import com.skydoves.landscapist.components.imageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.transformation.blur.BlurTransformationPlugin
import com.skydoves.landscapist.zoomable.ZoomablePlugin

/**
 * Benchmark host. Renders one tab per image library; selecting a tab shows a scrolling
 * [androidx.compose.foundation.lazy.LazyColumn] of distinct images for that library. The
 * Macrobenchmark driver navigates between tabs (matched by `By.text`) and scrolls the list
 * (matched by `By.scrollable(true)`), measuring frame timing per library. `testTagsAsResourceId`
 * surfaces each item's `testTag` as `By.res(packageName, "<Library>Image")`.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val imageComponent = imageComponent {
        +PlaceholderPlugin.Loading(painterResource(id = R.drawable.poster))
        +PlaceholderPlugin.Failure(painterResource(id = R.drawable.poster))
        +ShimmerPlugin()
        +ZoomablePlugin()
        +CrossfadePlugin()
        +CircularRevealPlugin()
        +BlurTransformationPlugin()
        +PalettePlugin()
      }

      val urls = remember { BenchmarkImages.urls() }
      var selectedTab by remember { mutableStateOf(TABS.first()) }

      CompositionLocalProvider(LocalImageComponent provides imageComponent) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        ) {
          Row(modifier = Modifier.fillMaxWidth()) {
            TABS.forEach { tab ->
              BasicText(
                text = tab,
                modifier = Modifier
                  .weight(1f)
                  .clickable { selectedTab = tab }
                  .padding(vertical = 16.dp),
              )
            }
          }

          val listModifier = Modifier.weight(1f)
          when (selectedTab) {
            "Landscapist" -> LandscapistImageList(urls, listModifier)
            "Coil" -> Coil3ImageList(urls, listModifier)
            "Glide" -> GlideImageList(urls, listModifier)
            "Fresco" -> FrescoImageList(urls, listModifier)
          }
        }
      }
    }
  }

  companion object {
    private val TABS = listOf("Landscapist", "Coil", "Glide", "Fresco")
  }
}
