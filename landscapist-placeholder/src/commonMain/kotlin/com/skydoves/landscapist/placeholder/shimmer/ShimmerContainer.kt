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

@Composable
public fun ShimmerContainer(
  modifier: Modifier,
  shimmer: Shimmer,
) {
  Box(modifier = modifier) {
    if (shimmer is Shimmer.Flash) {
      Shimmer(
        modifier = Modifier.matchParentSize(),
        baseColor = shimmer.shimmerBaseColor,
        highlightColor = shimmer.shimmerHighlightColor,
        durationMillis = shimmer.shimmerDuration,
        shimmerWidth = shimmer.width,
        intensity = shimmer.intensity,
        dropOff = shimmer.dropOff,
        tilt = shimmer.tilt,
      )
    } else {
      Box(
        modifier = Modifier.matchParentSize()
          .placeholder(
            visible = true,
            color = shimmer.shimmerBaseColor,
            highlight = if (shimmer is Shimmer.Resonate) {
              PlaceholderHighlight.shimmer(
                highlightColor = shimmer.shimmerHighlightColor,
                duration = shimmer.shimmerDuration,
                progressForMaxAlpha = shimmer.progressForMaxAlpha,
              )
            } else {
              PlaceholderHighlight.fade(
                highlightColor = shimmer.shimmerHighlightColor,
                duration = shimmer.shimmerDuration,
              )
            },
          ),
      )
    }
  }
}
