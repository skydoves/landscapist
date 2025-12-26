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
