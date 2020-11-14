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

@file:JvmName("ShimmerParamsAmbient")
@file:JvmMultifileClass

package com.skydoves.landscapist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ambientOf
import androidx.compose.ui.graphics.Color

/**
 * Ambient containing the preferred [ShimmerParams] for providing the same instance
 * in the composable hierarchy.
 */
val ShimmerParamsAmbient = ambientOf<ShimmerParams?> { null }

/** A provider for taking the ambient instances related to the [Shimmer]. */
object ShimmerAmbientProvider {

  /** Returns the current or default [ShimmerParams] for the `shimmerParams` parameter. */
  @Composable
  fun getShimmerParams(): ShimmerParams {
    return ShimmerParamsAmbient.current
      ?: ShimmerParams(baseColor = Color.DarkGray, highlightColor = Color.LightGray)
  }
}
