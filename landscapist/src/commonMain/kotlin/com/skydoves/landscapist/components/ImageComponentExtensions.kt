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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
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
  imageOptions: ImageOptions,
  executor: @Composable (IntSize) -> Unit,
) {
  val plugins = imagePlugins
  var seen = 0
  for (index in plugins.indices) {
    val plugin = plugins[index]
    if (plugin is ImagePlugin.LoadingStatePlugin) {
      // Keyed on the plugin and on how many equal ones came before it. The plugin alone would let
      // a component holding the same plugin twice hand both of them the same key, and neither
      // would keep what it remembered; the position alone would move every plugin after one of
      // another kind was added ahead of it.
      key(plugin, seen++) {
        plugin.compose(modifier = modifier, imageOptions = imageOptions, executor = executor)
      }
    }
  }
}

/** Runs image plugins from the given [ImageComponent] that will be run in a success state. */
@Composable
@InternalLandscapistApi
public fun ImageComponent.ComposeSuccessStatePlugins(
  modifier: Modifier,
  imageModel: Any?,
  imageOptions: ImageOptions,
  imageBitmap: ImageBitmap?,
) {
  val plugins = imagePlugins
  var seen = 0
  for (index in plugins.indices) {
    val plugin = plugins[index]
    if (plugin is ImagePlugin.SuccessStatePlugin) {
      key(plugin, seen++) {
        plugin.compose(
          modifier = modifier,
          imageModel = imageModel,
          imageOptions = imageOptions,
          imageBitmap = imageBitmap,
        )
      }
    }
  }
}

/** Runs image plugins from the given [ImageComponent] that will be run in a failure state. */
@Composable
@InternalLandscapistApi
public fun ImageComponent.ComposeFailureStatePlugins(
  modifier: Modifier,
  imageOptions: ImageOptions,
  reason: Throwable?,
) {
  val plugins = imagePlugins
  var seen = 0
  for (index in plugins.indices) {
    val plugin = plugins[index]
    if (plugin is ImagePlugin.FailureStatePlugin) {
      // Keyed on the plugin and on how many equal ones came before it. The plugin alone would let
      // a component holding the same plugin twice hand both of them the same key, and neither
      // would keep what it remembered; the position alone would move every plugin after one of
      // another kind was added ahead of it.
      key(plugin, seen++) {
        plugin.compose(modifier = modifier, imageOptions = imageOptions, reason = reason)
      }
    }
  }
}

/**
 * Wraps the content with composable plugins from the given [ImageComponent].
 * Composable plugins are applied in order, with each plugin wrapping the previous content.
 *
 * @param content The content to wrap with composable plugins.
 */
@Composable
@InternalLandscapistApi
public fun ImageComponent.ComposeWithComposablePlugins(
  content: @Composable () -> Unit,
) {
  val plugins = imagePlugins
  // Built in one pass, and only when there is something to build: most components have no
  // composable plugin at all, and scanning for one and then filtering for it did the work twice.
  var wrappers: MutableList<ImagePlugin.ComposablePlugin>? = null
  for (index in plugins.indices) {
    val plugin = plugins[index]
    if (plugin is ImagePlugin.ComposablePlugin) {
      (wrappers ?: mutableListOf<ImagePlugin.ComposablePlugin>().also { wrappers = it }).add(plugin)
    }
  }
  val composablePlugins = wrappers
  if (composablePlugins == null) {
    content()
  } else {
    // Wrap content with each plugin, innermost first
    composablePlugins.fold(content) { acc, plugin ->
      {
        key(plugin) { plugin.compose(content = acc) }
      }
    }.invoke()
  }
}
