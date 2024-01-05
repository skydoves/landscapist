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
package com.skydoves.landscapist.placeholder.shimmer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
public fun Shimmer(
  modifier: Modifier,
  baseColor: Color,
  highlightColor: Color,
  shimmerType: ShimmerType = ShimmerType.RESONATE,
) {
  Box(modifier = modifier) {
    Box(
      modifier = Modifier.matchParentSize()
        .placeholder(
          visible = true,
          color = baseColor,
          highlight = if (shimmerType == ShimmerType.RESONATE) {
            PlaceholderHighlight.shimmer(
              highlightColor = highlightColor,
            )
          } else {
            PlaceholderHighlight.fade(
              highlightColor = highlightColor,
            )
          },
        ),
    )
  }
}
