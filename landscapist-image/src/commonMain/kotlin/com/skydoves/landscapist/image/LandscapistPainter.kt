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
package com.skydoves.landscapist.image

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.skydoves.landscapist.ImageOptions

/**
 * Creates and remembers a [Painter] from the loaded image data.
 *
 * @param data The loaded image data (platform-specific bitmap type).
 * @return A [Painter] that can be used to render the image.
 */
@Composable
public expect fun rememberLandscapistPainter(data: Any?): Painter

/**
 * Default content for successfully loaded images.
 *
 * @param modifier The modifier to apply to the image.
 * @param painter The painter to use for rendering.
 * @param imageOptions The image display options.
 */
@Composable
internal fun DefaultSuccessContent(
  modifier: Modifier,
  painter: Painter,
  imageOptions: ImageOptions,
) {
  Image(
    modifier = modifier,
    painter = painter,
    contentDescription = imageOptions.contentDescription,
    contentScale = imageOptions.contentScale,
    alignment = imageOptions.alignment,
    alpha = imageOptions.alpha,
    colorFilter = imageOptions.colorFilter,
  )
}

/**
 * A [Painter] for already decoded pixels, built without composition.
 *
 * The container node runs its own load and has no composition to remember a painter in, so it needs
 * to build one from whatever the loader hands back. Returns null for anything that cannot be
 * painted this way: an Android [android.graphics.drawable.Drawable] animates by reading state as it
 * draws and has a lifecycle to dispatch, which is what [rememberLandscapistPainter] is for. The
 * node hands those back to the composed path rather than drawing them badly.
 *
 * @param data The loaded image data (platform-specific bitmap type).
 * @return A painter for [data], or null when [data] needs a composed painter.
 */
internal expect fun landscapistPainterOrNull(data: Any?): Painter?

/**
 * Whether [landscapistPainterOrNull] can ever answer null on this platform.
 *
 * Only Android's decoder produces one: an animated drawable. Everywhere else every decoded type is
 * still pixels, so the container node can always paint what it is handed, and the composable has no
 * reason to hold a state to hear otherwise. Reading such a state during composition subscribes the
 * image's recompose scope to it, which is not free and would buy nothing on those platforms.
 */
internal expect val ComposedPainterEverNeeded: Boolean
