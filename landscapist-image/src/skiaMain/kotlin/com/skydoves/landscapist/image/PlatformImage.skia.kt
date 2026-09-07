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
import androidx.compose.ui.unit.IntSize

/**
 * Bridges the decoded image type a single skia target adds on top of the shared ones.
 *
 * The skia source set covers desktop as well as Apple and Web, and desktop decodes through ImageIO
 * to a `java.awt.image.BufferedImage`, which the other targets have no notion of. Naming it here
 * would not compile for them, so each target answers for its own type.
 *
 * @return The Compose bitmap for [data], or null when it is not this target's image type.
 */
internal expect fun platformImageBitmapOrNull(data: Any?): ImageBitmap?

/**
 * The pixel size of [data] when it is this target's image type, or null.
 *
 * Kept separate from [platformImageBitmapOrNull] because reading a size is free, while converting
 * is a pixel copy, and the callers that only need the size run on every composition.
 */
internal expect fun platformImageSizeOrNull(data: Any?): IntSize?
