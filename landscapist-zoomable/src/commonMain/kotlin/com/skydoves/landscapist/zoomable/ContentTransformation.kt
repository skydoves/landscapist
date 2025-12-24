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
package com.skydoves.landscapist.zoomable

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ScaleFactor

/**
 * Represents the current transformation state of zoomable content.
 *
 * @property scale The current scale factor applied to the content.
 * @property offset The current translation offset applied to the content.
 * @property rotationZ The current rotation in degrees around the Z-axis (reserved for future use).
 */
@Immutable
public data class ContentTransformation(
  public val scale: ScaleFactor = ScaleFactor(1f, 1f),
  public val offset: Offset = Offset.Zero,
  public val rotationZ: Float = 0f,
) {
  /**
   * Returns true if this transformation is at the default state (no zoom, no pan, no rotation).
   */
  public val isIdentity: Boolean
    get() = scale == ScaleFactor(1f, 1f) && offset == Offset.Zero && rotationZ == 0f

  /**
   * Returns the uniform scale value (average of scaleX and scaleY).
   */
  public val scaleValue: Float
    get() = (scale.scaleX + scale.scaleY) / 2f

  public companion object {
    /**
     * The default identity transformation with no zoom, pan, or rotation.
     */
    public val Identity: ContentTransformation = ContentTransformation()
  }
}
