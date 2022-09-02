/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Shimmer params holds attributes of the [Shimmer] composable.
 *
 * @param baseColor the base color of the content.
 * @param highlightColor the shimmer's highlight color.
 * @param intensity adjust the density of the highlight at the center.
 * @param dropOff adjust the size of the fading edge of the highlight.
 * @param tilt adjust an angle at which the highlight is tilted and measured in degrees.
 * @param durationMillis a milli-second time to move the simmering effect from start to finish animation.
 */
@Immutable
public data class ShimmerParams(
  val baseColor: Color,
  val highlightColor: Color,
  val intensity: Float = DefaultShimmerIntensity,
  val dropOff: Float = DefaultShimmerDropOff,
  val tilt: Float = DefaultShimmerTilt,
  val durationMillis: Int = DefaultDurationMillis
)

/** A definition of the default intensity. */
internal const val DefaultShimmerIntensity = 0f

/** A definition of the default dropOff. */
internal const val DefaultShimmerDropOff = 0.5f

/** A definition of the default tilt. */
internal const val DefaultShimmerTilt = 20f

/** A definition of the default duration. */
internal const val DefaultDurationMillis = 650
