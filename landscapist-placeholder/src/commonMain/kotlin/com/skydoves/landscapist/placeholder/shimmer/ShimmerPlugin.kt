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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.plugins.ImagePlugin

/**
 * Shimmer params holds attributes of the [Shimmer] composable.
 *
 * @param baseColor the base color of the content.
 * @param highlightColor the shimmer's highlight color.
 */
@Immutable
public data class ShimmerPlugin(
  val baseColor: Color,
  val highlightColor: Color,
  val shimmerType: ShimmerType = ShimmerType.RESONATE,
) : ImagePlugin.LoadingStatePlugin {

  @Composable
  override fun compose(
    modifier: Modifier,
    imageOptions: ImageOptions,
    executor: @Composable (IntSize) -> Unit,
  ): ImagePlugin = apply {
    Shimmer(
      modifier = modifier,
      baseColor = baseColor,
      highlightColor = highlightColor,
      shimmerType = shimmerType,
    )
  }
}
