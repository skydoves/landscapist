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

package com.skydoves.landscapist

import androidx.compose.ui.graphics.Color

/**
 * Shimmer params holds attributes of the [Shimmer] composable.
 */
data class ShimmerParams(
  val baseColor: Color,
  val highlightColor: Color,
  val intensity: Float = DefaultShimmerIntensity,
  val dropOff: Float = DefaultShimmerDropOff,
  val tilt: Float = DefaultShimmerTilt,
  val durationMillis: Int = DefaultDurationMillis
)

internal const val DefaultShimmerIntensity = 0f

internal const val DefaultShimmerDropOff = 0.5f

internal const val DefaultShimmerTilt = 20f

internal const val DefaultDurationMillis = 650
