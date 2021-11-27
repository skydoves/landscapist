/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
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

@file:JvmName("LocalShimmerParams")
@file:JvmMultifileClass
@file:Suppress("unused")

package com.skydoves.landscapist

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.skydoves.landscapist.LocalShimmerProvider.defaultShimmerParams

/**
 * Local containing the preferred [ShimmerParams] for providing the same instance
 * in the composable hierarchy.
 */
public val LocalShimmerParams: ProvidableCompositionLocal<ShimmerParams> =
  compositionLocalOf { defaultShimmerParams() }

/** A provider for taking the local instances related to the [Shimmer]. */
internal object LocalShimmerProvider {

  /** Returns the current or default [ShimmerParams] for the `shimmerParams` parameter. */
  fun defaultShimmerParams(): ShimmerParams {
    return ShimmerParams(baseColor = Color.DarkGray, highlightColor = Color.LightGray)
  }
}
