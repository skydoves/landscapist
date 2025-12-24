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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.zoomable.internal.zoomGestures

/**
 * A plugin that enables zoomable functionality for images.
 *
 * This plugin wraps the image content with zoom and pan gesture handling,
 * allowing users to pinch-to-zoom, pan, and double-tap to zoom images.
 *
 * **Usage:**
 * ```kotlin
 * GlideImage(
 *   imageModel = { imageUrl },
 *   component = rememberImageComponent {
 *     +ZoomablePlugin()
 *   },
 * )
 * ```
 *
 * **With custom configuration:**
 * ```kotlin
 * val zoomableState = rememberZoomableState()
 *
 * GlideImage(
 *   imageModel = { imageUrl },
 *   component = rememberImageComponent {
 *     +ZoomablePlugin(
 *       state = zoomableState,
 *       config = ZoomableConfig(
 *         maxZoom = 8f,
 *         doubleTapZoom = 3f,
 *       ),
 *     )
 *   },
 * )
 *
 * // Programmatic control
 * Button(onClick = { scope.launch { zoomableState.resetZoom() } }) {
 *   Text("Reset")
 * }
 * ```
 *
 * @property state The [ZoomableState] that manages zoom and pan transformations.
 *   If null, a new state will be created internally.
 * @property config The [ZoomableConfig] for customizing zoom behavior.
 * @property enabled Whether zoom gestures are enabled.
 * @property onTransformChanged Callback invoked when the transformation changes.
 */
@Immutable
public data class ZoomablePlugin(
  public val state: ZoomableState? = null,
  public val config: ZoomableConfig = ZoomableConfig(),
  public val enabled: Boolean = true,
  public val onTransformChanged: ((ContentTransformation) -> Unit)? = null,
) : ImagePlugin.ComposablePlugin {

  /**
   * Wraps the image content with zoomable behavior.
   *
   * @param content The image content to wrap.
   */
  @Composable
  override fun compose(content: @Composable () -> Unit) {
    val zoomableState = state ?: rememberZoomableState(config)

    // Notify callback when transformation changes
    val transformation = zoomableState.transformation
    onTransformChanged?.invoke(transformation)

    Box(
      modifier = Modifier
        // Clip content to container bounds so zoomed image doesn't overflow
        .clipToBounds()
        // Track the container size for pan constraints
        .onSizeChanged { size ->
          zoomableState.setLayoutSize(size)
        }
        .then(
          if (enabled) {
            Modifier.zoomGestures(
              state = zoomableState,
              config = config,
            )
          } else {
            Modifier
          },
        )
        .graphicsLayer {
          scaleX = transformation.scale.scaleX
          scaleY = transformation.scale.scaleY
          translationX = transformation.offset.x
          translationY = transformation.offset.y
          rotationZ = transformation.rotationZ
        },
      contentAlignment = Alignment.Center,
    ) {
      content()
    }
  }
}
