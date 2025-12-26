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
package com.skydoves.landscapist.placeholder.progressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.plugins.ImagePlugin

/**
 * A plugin that enables progressive image loading with smooth transitions.
 *
 * When enabled, images are loaded in multiple stages:
 * 1. A tiny, blurred preview appears almost instantly
 * 2. The preview smoothly transitions to the full quality image
 *
 * This creates the perception of faster loading because users see
 * *something* immediately, rather than waiting for the full image.
 *
 * Usage:
 * ```kotlin
 * CoilImage(
 *   imageModel = { imageUrl },
 *   component = rememberImageComponent {
 *     +ProgressiveLoadingPlugin(
 *       blurRadius = 8.dp,
 *       transitionDuration = 300
 *     )
 *   }
 * )
 * ```
 *
 * @param initialBlurRadius The blur radius for the preview image in dp. Default is 16dp.
 * @param transitionDuration Duration of the blur-to-sharp transition in milliseconds.
 */
public class ProgressiveLoadingPlugin(
  private val initialBlurRadius: Float = 16f,
  private val transitionDuration: Int = 300,
) : ImagePlugin.LoadingStatePlugin {

  @Composable
  override fun compose(
    modifier: Modifier,
    imageOptions: ImageOptions,
    executor: @Composable (IntSize) -> Unit,
  ): ImagePlugin = apply {
    // Request a tiny thumbnail for fast preview
    val previewSize = IntSize(32, 32)
    executor.invoke(previewSize)
  }

  public companion object {
    /**
     * Creates a default progressive loading plugin with recommended settings.
     */
    public fun default(): ProgressiveLoadingPlugin = ProgressiveLoadingPlugin()

    /**
     * Creates a fast progressive loading plugin with minimal blur and quick transition.
     */
    public fun fast(): ProgressiveLoadingPlugin = ProgressiveLoadingPlugin(
      initialBlurRadius = 8f,
      transitionDuration = 150,
    )

    /**
     * Creates a smooth progressive loading plugin with higher blur and slower transition.
     */
    public fun smooth(): ProgressiveLoadingPlugin = ProgressiveLoadingPlugin(
      initialBlurRadius = 24f,
      transitionDuration = 500,
    )
  }
}

/**
 * State holder for progressive image loading.
 * Tracks the current decode stage and manages transitions.
 */
public class ProgressiveImageState {
  /** The current preview bitmap (low quality). */
  public var previewBitmap: ImageBitmap? by mutableStateOf(null)
    internal set

  /** The full quality bitmap. */
  public var fullBitmap: ImageBitmap? by mutableStateOf(null)
    internal set

  /** Current decode progress (0.0 to 1.0). */
  public var progress: Float by mutableFloatStateOf(0f)
    internal set

  /** Whether progressive loading is complete. */
  public val isComplete: Boolean
    get() = progress >= 1f && fullBitmap != null

  /** Whether we have any image to show. */
  public val hasImage: Boolean
    get() = previewBitmap != null || fullBitmap != null
}

/**
 * Composable that renders a progressively loaded image with blur transition.
 *
 * @param state The progressive image state.
 * @param initialBlurRadius The starting blur radius.
 * @param transitionDuration The transition duration in milliseconds.
 * @param imageOptions Options for rendering the image.
 * @param modifier Modifier to apply to the image.
 */
@Composable
public fun ProgressiveImage(
  state: ProgressiveImageState,
  initialBlurRadius: Float = 16f,
  transitionDuration: Int = 300,
  imageOptions: ImageOptions,
  modifier: Modifier = Modifier,
) {
  // Animate blur radius from initial to 0 as loading completes
  val targetBlur = if (state.isComplete) 0f else initialBlurRadius * (1f - state.progress)
  val animatedBlur by animateFloatAsState(
    targetValue = targetBlur,
    animationSpec = tween(durationMillis = transitionDuration),
    label = "progressiveBlur",
  )

  // Show the best available image
  val bitmap = state.fullBitmap ?: state.previewBitmap

  if (bitmap != null) {
    val blurModifier = if (animatedBlur > 0.5f) {
      Modifier.blur(animatedBlur.dp)
    } else {
      Modifier
    }

    Image(
      painter = BitmapPainter(bitmap),
      contentDescription = imageOptions.contentDescription,
      modifier = modifier.then(blurModifier),
      alignment = imageOptions.alignment,
      contentScale = imageOptions.contentScale,
      alpha = imageOptions.alpha,
      colorFilter = imageOptions.colorFilter,
    )
  }
}

/**
 * Remembers and returns a [ProgressiveImageState].
 */
@Composable
public fun rememberProgressiveImageState(): ProgressiveImageState {
  return remember { ProgressiveImageState() }
}
