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

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import java.awt.image.BufferedImage

/** Desktop decodes through ImageIO, so its decoded images arrive as [BufferedImage]. */
internal actual fun platformImageBitmapOrNull(data: Any?): ImageBitmap? =
  (data as? BufferedImage)?.toComposeImageBitmap()

internal actual fun platformImageSizeOrNull(data: Any?): IntSize? =
  (data as? BufferedImage)?.let { IntSize(it.width, it.height) }
