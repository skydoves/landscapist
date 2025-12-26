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
package com.skydoves.landscapist

import androidx.compose.foundation.Image
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize

/**
 * Represents parameters to load generic [Image] Composable.
 *
 * @property alignment The alignment parameter used to place the loaded [ImageBitmap] in the image container.
 * @property contentDescription The content description used to provide accessibility to describe the image.
 * @property contentScale The scale parameter used to determine the aspect ratio scaling to be
 * used for the loaded [ImageBitmap].
 * @property colorFilter The colorFilter parameter used to apply for the image when it is rendered onscreen.
 * @property alpha The alpha parameter used to apply for the image when it is rendered onscreen.
 * @property requestSize The [IntSize] that will be used to request remote images.
 * @property tag An optional tag feature can be utilized either to trigger recomposition or to attach a test tag to your image.
 * @property placeholderAspectRatio The aspect ratio (width/height) to use for the container before the image loads.
 * This prevents layout shifts by reserving space immediately. For example, use 16f/9f for 16:9 images,
 * 4f/3f for 4:3 images, or 1f for square images. If null, no aspect ratio constraint is applied.
 */
@Immutable
public data class ImageOptions(
  public val alignment: Alignment = Alignment.Center,
  public val contentDescription: String? = null,
  public val contentScale: ContentScale = ContentScale.Crop,
  public val colorFilter: ColorFilter? = null,
  public val alpha: Float = DefaultAlpha,
  public val requestSize: IntSize = IntSize(DEFAULT_IMAGE_SIZE, DEFAULT_IMAGE_SIZE),
  public val tag: String = "",
  public val placeholderAspectRatio: Float? = null,
) {
  /** Returns true if the [requestSize] is valid. */
  public val isValidSize: Boolean
    inline get() = requestSize.width > 0 && requestSize.height > 0

  /**
   * Returns a key that represents only the loading-related properties.
   * This key should be used for image loading decisions to prevent unnecessary reloads
   * when only rendering properties (colorFilter, alpha, alignment, contentScale, contentDescription) change.
   */
  @InternalLandscapistApi
  public val loadingOptionsKey: Any
    get() = LoadingOptionsKey(requestSize = requestSize, tag = tag)

  private companion object {
    const val DEFAULT_IMAGE_SIZE: Int = -1
  }
}

/**
 * Internal data class that holds only the loading-related properties of [ImageOptions].
 * Used as a stable key for image loading operations to prevent unnecessary reloads
 * when only rendering properties change.
 */
@InternalLandscapistApi
@Immutable
public data class LoadingOptionsKey(
  public val requestSize: IntSize,
  public val tag: String,
)
