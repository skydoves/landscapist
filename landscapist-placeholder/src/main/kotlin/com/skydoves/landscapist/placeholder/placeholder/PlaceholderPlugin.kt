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
package com.skydoves.landscapist.placeholder.placeholder

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.skydoves.landscapist.ImageBySource
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.plugins.ImagePlugin

/** A pluggable placeholder that will be rendered depending on the image loading states. */
public sealed class PlaceholderPlugin {

  /**
   * A pluggable loading placeholder that will be rendered depending on the image loading states is [ImageLoadState.Loading].
   *
   * @property source One of source of [ImageBitmap], [ImageVector], or [Painter] to render [Image] Composable.
   */
  public data class Loading(val source: Any?) :
    PlaceholderPlugin(),
    ImagePlugin.LoadingStatePlugin {

    @Composable
    override fun compose(
      modifier: Modifier,
      imageOptions: ImageOptions?,
    ): ImagePlugin = apply {
      if (source != null && imageOptions != null) {
        ImageBySource(
          source = source,
          modifier = modifier,
          alignment = imageOptions.alignment,
          contentDescription = imageOptions.contentDescription,
          contentScale = imageOptions.contentScale,
          colorFilter = imageOptions.colorFilter,
          alpha = imageOptions.alpha,
        )
      }
    }
  }

  /**
   * A pluggable failure placeholder that will be rendered depending on the image loading states is [ImageLoadState.Failure].
   *
   * @property source One of source of [ImageBitmap], [ImageVector], or [Painter] to render [Image] Composable.
   */
  public data class Failure(val source: Any?) :
    PlaceholderPlugin(),
    ImagePlugin.FailureStatePlugin {

    @Composable
    override fun compose(
      modifier: Modifier,
      imageOptions: ImageOptions?,
      reason: Throwable?,
    ): ImagePlugin = apply {
      if (source != null && imageOptions != null) {
        ImageBySource(
          source = source,
          modifier = modifier,
          alignment = imageOptions.alignment,
          contentDescription = imageOptions.contentDescription,
          contentScale = imageOptions.contentScale,
          colorFilter = imageOptions.colorFilter,
          alpha = imageOptions.alpha,
        )
      }
    }
  }
}
