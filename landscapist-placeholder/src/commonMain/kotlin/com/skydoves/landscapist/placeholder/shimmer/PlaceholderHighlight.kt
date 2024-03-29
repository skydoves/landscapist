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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.max

/**
 * Originated from https://github.com/google/accompanist/blob/main/placeholder/src/main/java/com/google/accompanist/placeholder/PlaceholderHighlight.kt
 *
 * All rights reserved to Google LLC.
 *
 * A class which provides a brush to paint placeholder based on progress.
 */
@Stable
internal interface PlaceholderHighlight {
  /**
   * The optional [AnimationSpec] to use when running the animation for this highlight.
   */
  val animationSpec: InfiniteRepeatableSpec<Float>?

  /**
   * Return a [Brush] to draw for the given [progress] and [size].
   *
   * @param progress the current animated progress in the range of 0f..1f.
   * @param size The size of the current layout to draw in.
   */
  fun brush(
    progress: Float,
    size: Size,
  ): Brush

  /**
   * Return the desired alpha value used for drawing the [Brush] returned from [brush].
   *
   * @param progress the current animated progress in the range of 0f..1f.
   */
  fun alpha(progress: Float): Float

  companion object
}

/**
 * Creates a [Fade] brush with the given initial and target colors.
 *
 * @sample com.google.accompanist.sample.placeholder.DocSample_Foundation_PlaceholderFade
 *
 * @param highlightColor the color of the highlight which is faded in/out.
 * @param animationSpec the [AnimationSpec] to configure the animation.
 */
internal fun PlaceholderHighlight.Companion.fade(
  highlightColor: Color,
  duration: Int,
): PlaceholderHighlight = Fade(
  highlightColor = highlightColor,
  animationSpec = infiniteRepeatable(
    animation = tween(delayMillis = 0, durationMillis = duration),
    repeatMode = RepeatMode.Reverse,
  ),
)

/**
 * Creates a [PlaceholderHighlight] which 'shimmers', using the given [highlightColor].
 *
 * The highlight starts at the top-start, and then grows to the bottom-end during the animation.
 * During that time it is also faded in, from 0f..progressForMaxAlpha, and then faded out from
 * progressForMaxAlpha..1f.
 *
 * @sample com.google.accompanist.sample.placeholder.DocSample_Foundation_PlaceholderShimmer
 *
 * @param highlightColor the color of the highlight 'shimmer'.
 * @param animationSpec the [AnimationSpec] to configure the animation.
 * @param progressForMaxAlpha The progress where the shimmer should be at it's peak opacity.
 * Defaults to 0.6f.
 */
internal fun PlaceholderHighlight.Companion.shimmer(
  highlightColor: Color,
  duration: Int,
  progressForMaxAlpha: Float = 0.6f,
): PlaceholderHighlight = Placeholder(
  highlightColor = highlightColor,
  animationSpec = infiniteRepeatable(
    animation = tween(delayMillis = 0, durationMillis = duration),
    repeatMode = RepeatMode.Restart,
  ),
  progressForMaxAlpha = progressForMaxAlpha,
)

private data class Fade(
  private val highlightColor: Color,
  override val animationSpec: InfiniteRepeatableSpec<Float>,
) : PlaceholderHighlight {
  private val brush = SolidColor(highlightColor)

  override fun brush(progress: Float, size: Size): Brush = brush
  override fun alpha(progress: Float): Float = progress
}

private data class Placeholder(
  private val highlightColor: Color,
  override val animationSpec: InfiniteRepeatableSpec<Float>,
  private val progressForMaxAlpha: Float = 0.6f,
) : PlaceholderHighlight {
  override fun brush(
    progress: Float,
    size: Size,
  ): Brush = Brush.radialGradient(
    colors = listOf(
      highlightColor.copy(alpha = 0f),
      highlightColor,
      highlightColor.copy(alpha = 0f),
    ),
    center = Offset(x = 0f, y = 0f),
    radius = (max(size.width, size.height) * progress * 2).coerceAtLeast(0.01f),
  )

  override fun alpha(progress: Float): Float = when {
    // From 0f...ProgressForOpaqueAlpha we animate from 0..1
    progress <= progressForMaxAlpha -> {
      lerp(
        start = 0f,
        stop = 1f,
        fraction = progress / progressForMaxAlpha,
      )
    }
    // From ProgressForOpaqueAlpha..1f we animate from 1..0
    else -> {
      lerp(
        start = 1f,
        stop = 0f,
        fraction = (progress - progressForMaxAlpha) / (1f - progressForMaxAlpha),
      )
    }
  }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
  return (1 - fraction) * start + fraction * stop
}

internal const val DefaultProgressForMaxAlpha = 0.6f

internal const val DefaultResonateDuration = 1000

internal const val DefaultFadeDuration = 600
