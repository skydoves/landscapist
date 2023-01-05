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
package com.skydoves.landscapist.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.InternalLandscapistApi
import com.skydoves.landscapist.plugins.ImagePlugin

/**
 * Returns a list of [ImagePlugin] from the given [ImageComponent].
 * It will return an empty list of if it's not an instance of [ImagePluginComponent].
 */
public inline val ImageComponent.imagePlugins: List<ImagePlugin>
  get() = if (this is ImagePluginComponent) {
    plugins
  } else {
    emptyList()
  }

/** Runs image plugins from the given [ImageComponent] that will be run in a loading state. */
@Composable
@InternalLandscapistApi
public fun ImageComponent.ComposeLoadingStatePlugins(
  modifier: Modifier,
  imageOptions: ImageOptions
) {
  imagePlugins.filterIsInstance<ImagePlugin.LoadingStatePlugin>().forEach { plugin ->
    plugin.compose(modifier = modifier, imageOptions = imageOptions)
  }
}

/** Runs image plugins from the given [ImageComponent] that will be run in a success state. */
@Composable
@InternalLandscapistApi
public fun ImageComponent.ComposeSuccessStatePlugins(
  modifier: Modifier,
  imageModel: Any?,
  imageOptions: ImageOptions,
  imageBitmap: ImageBitmap?
) {
  imagePlugins.filterIsInstance<ImagePlugin.SuccessStatePlugin>().forEach { plugin ->
    plugin.compose(
      modifier = modifier,
      imageModel = imageModel,
      imageOptions = imageOptions,
      imageBitmap = imageBitmap
    )
  }
}

/** Runs image plugins from the given [ImageComponent] that will be run in a failure state. */
@Composable
@InternalLandscapistApi
public fun ImageComponent.ComposeFailureStatePlugins(
  modifier: Modifier,
  imageOptions: ImageOptions,
  reason: Throwable?
) {
  imagePlugins.filterIsInstance<ImagePlugin.FailureStatePlugin>().forEach { plugin ->
    plugin.compose(modifier = modifier, imageOptions = imageOptions, reason = reason)
  }
}
