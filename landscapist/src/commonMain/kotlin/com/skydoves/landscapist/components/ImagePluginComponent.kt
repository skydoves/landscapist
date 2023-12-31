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
import androidx.compose.runtime.Stable
import com.skydoves.landscapist.plugins.ImagePlugin

/** An explicit domain-specific language marker to prevent belonging to another DSL. */
@DslMarker
internal annotation class LandscapistImagePluginComponentDSL

/** A factory extension for creating a new instance of [ImagePluginComponent] and running
 * [ImagePluginComponent.compose] with the [block] receiver. */
@Stable
@Composable
@LandscapistImagePluginComponentDSL
public inline fun imageComponent(
  block: @Composable ImagePluginComponent.() -> Unit,
): ImagePluginComponent = ImagePluginComponent(mutableListOf()).compose { block() }

/**
 * A pluggable image component that extends [ImageComponent] and includes a collection of [ImagePlugin].
 *
 * @property mutablePlugins A mutable list of [ImagePlugin] that could be modified by internal functions.
 */
@Stable
public class ImagePluginComponent(
  @PublishedApi internal val mutablePlugins: MutableList<ImagePlugin> = mutableListOf(),
) : ImageComponent {

  /** A list of [ImagePlugin] that could be composed to load an image. */
  public val plugins: List<ImagePlugin>
    inline get() = mutablePlugins

  /**
   * A domain-specific function that receives [ImagePluginComponent] as a receiver and returns
   * a new instance of [ImagePluginComponent] that contains all of the previously added [plugins].
   *
   * @param block A [ImagePluginComponent] receiver that composes [ImagePlugin]s in the scope.
   */
  @LandscapistImagePluginComponentDSL
  public inline fun compose(block: ImagePluginComponent.() -> Unit): ImagePluginComponent {
    return ImagePluginComponent(plugins.toMutableList()).apply(block)
  }

  /**
   * Add a new [ImagePlugin] to the component.
   *
   * @param imagePlugin A pluggable compose interface that will be executed for loading images.
   */
  public fun add(imagePlugin: ImagePlugin): ImagePluginComponent = apply {
    mutablePlugins.add(imagePlugin)
  }

  /**
   * Add a list of [ImagePlugin] to the component.
   *
   * @param imagePlugins A list of pluggable compose interfaces that will be executed for loading images.
   */
  public fun addPlugins(imagePlugins: List<ImagePlugin>): ImagePluginComponent = apply {
    mutablePlugins.addAll(imagePlugins)
  }

  /**
   * Remove an [ImagePlugin] from the component.
   *
   * @param imagePlugin A pluggable compose interface that will be executed for loading images.
   */
  public fun remove(imagePlugin: ImagePlugin): ImagePluginComponent = apply {
    mutablePlugins.remove(imagePlugin)
  }

  /** Add a new [ImagePlugin] to the component using the unary plus operator to the [ImagePlugin]. */
  public operator fun ImagePlugin.unaryPlus(): ImagePluginComponent = add(this)
}
