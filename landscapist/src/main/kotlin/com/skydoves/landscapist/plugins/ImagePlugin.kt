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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageOptions

/**
 * A pluggable compose interface that will be executed for loading images.
 *
 * You can implement your own image plugin that will be composed with Image Composable functions
 * by implementing one of [ImagePlugin.PainterPlugin], [ImagePlugin.LoadingStatePlugin],
 * [ImagePlugin.SuccessStatePlugin], or [ImagePlugin.FailureStatePlugin]
 */
@Immutable
public sealed interface ImagePlugin {

  /** A pinter plugin interface to be composed with the given [Painter]. */
  public interface PainterPlugin : ImagePlugin {

    /** Compose the given [painter] with an [imageBitmap]. */
    @Composable
    public fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter
  }

  /** A pluggable image loading state plugin that will be composed when the state is [ImageLoadState.Loading]. */
  public interface LoadingStatePlugin : ImagePlugin {

    /** A composable that will be executed depending on the loading states. */
    @Composable
    public fun compose(
      modifier: Modifier,
      imageOptions: ImageOptions?
    ): ImagePlugin
  }

  /** A pluggable image loading state plugin that will be composed when the state is [ImageLoadState.Success]. */
  public interface SuccessStatePlugin : ImagePlugin {

    /** A composable that will be executed depending on the loading states. */
    @Composable
    public fun compose(
      modifier: Modifier,
      imageOptions: ImageOptions?
    ): ImagePlugin
  }

  /** A pluggable image loading state plugin that will be composed when the state is [ImageLoadState.Failure]. */
  public interface FailureStatePlugin : ImagePlugin {

    /** A composable that will be executed depending on the loading states. */
    @Composable
    public fun compose(
      modifier: Modifier,
      imageOptions: ImageOptions?
    ): ImagePlugin
  }
}

/**
 * Compose a list of [imagePlugins] and [imageBitmap] to the given [Painter].
 *
 * @param imagePlugins A list of [imagePlugins] to be executed on the given [Painter].
 * @param imageBitmap A target [imageBitmap] to be composed of the given [Painter]
 */
@Composable
internal fun Painter.composePlugins(
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
