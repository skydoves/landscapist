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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.skydoves.landscapist.placeholder.shimmer.Shimmer.Fade
import com.skydoves.landscapist.placeholder.shimmer.Shimmer.Flash
import com.skydoves.landscapist.placeholder.shimmer.Shimmer.Resonate

/**
 * This is a representation of Shimmer to be used with [ShimmerPlugin].
 *
 * There are three different type of shimmering effect, [Flash], [Resonate], and [Fade].
 *
 * @property shimmerBaseColor base background color of this composable.
 * @property shimmerHighlightColor highlight shimmering effect color of this composable.
 */
@Stable
public sealed class Shimmer(
  public val shimmerBaseColor: Color,
  public val shimmerHighlightColor: Color,
  public val shimmerDuration: Int,
) {

  /**
   * This is the most commonly used effect, popularized by platforms like Facebook and Twitter.
   * It features a quick, bright flash that moves across the content, giving the impression of dynamic loading.
   *
   * @property baseColor base background color of this composable.
   * @property highlightColor highlight shimmering effect color of this composable.
   * @property width the width size of the shimmer.
   * @property intensity controls the brightness of the highlight at the center.
   * @property dropOff controls the size of the fading edge of the highlight.
   * @property tilt angle at which the highlight is tilted, measured in degrees.
   * @property duration animation duration of the shimmering start to end.
   */
  @Immutable
  public data class Flash(
    val baseColor: Color,
    val highlightColor: Color,
    val width: Dp? = null,
    val intensity: Float = DefaultShimmerIntensity,
    val dropOff: Float = DefaultShimmerDropOff,
    val tilt: Float = DefaultShimmerTilt,
    val duration: Int = DefaultDurationMillis,
  ) : Shimmer(
    shimmerBaseColor = baseColor,
    shimmerHighlightColor = highlightColor,
    shimmerDuration = duration,
  )

  /**
   * Characterized by its smooth and glowing appearance, this effect creates a more subtle and elegant visual.
   *
   * The highlight starts at the top-start, and then grows to the bottom-end during the animation.
   * During that time it is also faded in, from 0f..progressForMaxAlpha, and then faded out from
   * progressForMaxAlpha..1f.
   *
   * @property baseColor base background color of this composable.
   * @property highlightColor highlight shimmering effect color of this composable.
   * @property duration duration of the animation.
   * @property progressForMaxAlpha The progress where the shimmer should be at it's peak opacity.
   */
  @Immutable
  public data class Resonate(
    val baseColor: Color,
    val highlightColor: Color,
    val duration: Int = DefaultResonateDuration,
    val progressForMaxAlpha: Float = DefaultProgressForMaxAlpha,
  ) : Shimmer(
    shimmerBaseColor = baseColor,
    shimmerHighlightColor = highlightColor,
    shimmerDuration = duration,
  )

  /**
   * This effect offers a fading transition between the [baseColor] and [highlightColor].
   *
   * @property baseColor base background color of this composable.
   * @property highlightColor highlight shimmering effect color of this composable.
   * @property duration duration of the animation.
   */
  @Immutable
  public data class Fade(
    val baseColor: Color,
    val highlightColor: Color,
    val duration: Int = DefaultFadeDuration,
  ) : Shimmer(
    shimmerBaseColor = baseColor,
    shimmerHighlightColor = highlightColor,
    shimmerDuration = duration,
  )
}
