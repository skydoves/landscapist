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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.LocalImageComponent
import com.skydoves.landscapist.image.LandscapistImage

@Composable
fun LandscapistImageList(urls: List<String>, modifier: Modifier = Modifier) {
  LazyColumn(modifier = modifier.fillMaxSize()) {
    items(urls) { url ->
      LandscapistImage(
        modifier = Modifier
          .fillMaxWidth()
          .height(BenchmarkImages.ITEM_HEIGHT_DP.dp)
          .testTag("LandscapistImage"),
        imageModel = { url },
        component = LocalImageComponent.current,
        imageOptions = ImageOptions(tag = "LandscapistImage"),
      )
    }
  }
}
