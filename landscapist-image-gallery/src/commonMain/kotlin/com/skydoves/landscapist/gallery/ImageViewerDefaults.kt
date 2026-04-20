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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Default values for [ImageViewer].
 */
public object ImageViewerDefaults {

  /** Default background color of the viewer. */
  public val BackgroundColor: Color = Color.Black

  /** Default spacing between pages in the pager. */
  public val PageSpacing: Dp = 16.dp

  /** Default number of pages to preload beyond the visible viewport. */
  public const val BeyondViewportPageCount: Int = 1

  /** Default threshold (0.0 ~ 1.0) of vertical drag distance relative to screen height to trigger dismiss. */
  public const val DismissThreshold: Float = 0.25f

  /** Default velocity threshold for fling-to-dismiss. */
  public const val DismissVelocityThreshold: Float = 1000f
}
