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
package com.skydoves.landscapist.gallery

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/**
 * Configures shared element transitions between [ImageGallery] items and [ImageViewer] pages.
 *
 * Pass the **same** config (or configs that produce the same keys via [keyProvider]) to both
 * [ImageGallery] and [ImageViewer] so the framework can match items across destinations and
 * animate their shared bounds.
 *
 * **Typical usage:**
 * ```kotlin
 * SharedTransitionLayout {
 *   NavHost(...) {
 *     composable<Screen.Gallery> {
 *       ImageGallery(
 *         images = urls,
 *         sharedTransition = ImageSharedTransitionConfig(
 *           sharedTransitionScope = this@SharedTransitionLayout,
 *           animatedContentScope = this,
 *         ),
 *         onImageClick = { index, _ -> nav.navigate(Screen.Viewer(index)) },
 *       )
 *     }
 *     composable<Screen.Viewer> { entry ->
 *       ImageViewer(
 *         images = urls,
 *         state = rememberImageViewerState(
 *           initialPage = entry.toRoute<Screen.Viewer>().index,
 *           pageCount = { urls.size },
 *         ),
 *         sharedTransition = ImageSharedTransitionConfig(
 *           sharedTransitionScope = this@SharedTransitionLayout,
 *           animatedContentScope = this,
 *         ),
 *       )
 *     }
 *   }
 * }
 * ```
 *
 * @param sharedTransitionScope The enclosing [SharedTransitionScope] (usually the receiver of
 *   a [androidx.compose.animation.SharedTransitionLayout]).
 * @param animatedContentScope The [AnimatedContentScope] for this destination
 *   (typically provided by NavHost / AnimatedContent).
 * @param keyProvider Produces a stable shared content key per item. The default combines the
 *   item's index and model: `"landscapist-shared-$index-$imageModel"`. Override when gallery
 *   index differs from viewer page, when items are duplicated, or when you need to namespace
 *   keys across multiple galleries.
 * @param enabled Toggle the transition at runtime without restructuring the composable tree.
 *   When `false`, the config is ignored and items render without shared bounds.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Immutable
public class ImageSharedTransitionConfig(
  public val sharedTransitionScope: SharedTransitionScope,
  public val animatedContentScope: AnimatedContentScope,
  public val keyProvider: (index: Int, imageModel: Any) -> Any = DefaultKeyProvider,
  public val enabled: Boolean = true,
) {
  public companion object {
    /**
     * Default key provider: `"landscapist-shared-$index-$imageModel"`.
     *
     * Including the index guarantees uniqueness even when the same image model appears multiple
     * times in the list. Override if gallery indices differ from viewer pages.
     */
    public val DefaultKeyProvider: (Int, Any) -> Any = { index, imageModel ->
      "landscapist-shared-$index-$imageModel"
    }
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun rememberSharedBoundsModifier(
  config: ImageSharedTransitionConfig?,
  index: Int,
  imageModel: Any,
): Modifier {
  if (config == null || !config.enabled) return Modifier
  return with(config.sharedTransitionScope) {
    Modifier.sharedBounds(
      sharedContentState = rememberSharedContentState(
        key = config.keyProvider(index, imageModel),
      ),
      animatedVisibilityScope = config.animatedContentScope,
    )
  }
}
