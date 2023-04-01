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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.components.LocalImageComponent
import com.skydoves.landscapist.glide.GlideImage

@Composable
fun GlideImageProfiles() {
  GlideImage(
    modifier = Modifier.size(120.dp).testTag("GlideImage"),
    imageModel = {
      "https://user-images.githubusercontent.com/" +
        "24237865/75087936-5c1d9f80-553e-11ea-81d3-a912634dd8f7.jpg"
    },
    previewPlaceholder = R.drawable.poster,
    component = LocalImageComponent.current,
  )
}
