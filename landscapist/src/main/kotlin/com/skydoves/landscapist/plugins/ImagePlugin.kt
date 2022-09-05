/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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
package com.skydoves.landscapist.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter

/**
 * A pluggable compose interface that will be executed for loading images.
 */
@Immutable
public sealed interface ImagePlugin {

  /**
   * A pinter plugin interface to be composed with the given [Painter].
   */
  public interface PainterPlugin : ImagePlugin {

    /**
     * Compose the given [painter] with an [imageBitmap].
     */
    @Composable
    public fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter
  }

  public interface LoadingPlugin : ImagePlugin

  public interface SuccessPlugin : ImagePlugin

  public interface FailurePlugin : ImagePlugin
}

/**
 * Compose a list of [imagePlugins] and [imageBitmap] to the given [Painter].
 *
 * @param imagePlugins A list of [imagePlugins] to be executed on the given [Painter].
 * @param imageBitmap A target [imageBitmap] to be composed of the given [Painter]
 */
@Composable
public fun Painter.composePlugins(
  imagePlugins: List<ImagePlugin>,
  imageBitmap: ImageBitmap
): Painter {
  var painter: Painter = this
  imagePlugins
    .filterIsInstance<ImagePlugin.PainterPlugin>()
    .forEach { bitmapImagePlugin ->
      painter = bitmapImagePlugin.compose(imageBitmap = imageBitmap, painter = painter)
    }
  return painter
}
