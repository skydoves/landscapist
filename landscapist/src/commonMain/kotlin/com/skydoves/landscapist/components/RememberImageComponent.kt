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
import androidx.compose.runtime.remember

/**
 * Create and remember a new instance of [ImageComponent] that implements [ImagePluginComponent]
 * on the memory.
 *
 * The component keeps its identity for as long as [block] produces the same plugins, and is
 * replaced when it does not. Both halves matter: images are skippable and take the component as a
 * stable parameter, so a component held across a plugin change is one the image never looks at
 * again, and a component rebuilt on every composition is one no image can ever skip.
 *
 * Same means equal, so a plugin has to be comparable. Every plugin this library ships is. One that
 * is not is a new value on each composition, which rebuilds the component around it and costs the
 * image its skipping, so give a custom [com.skydoves.landscapist.plugins.ImagePlugin] value
 * equality: a data class, or `equals` and `hashCode` over whatever configures it.
 *
 * @param block The receiver of an instance of [ImagePluginComponent].
 */
@Composable
@LandscapistImagePluginComponentDSL
public fun rememberImageComponent(
  block: @Composable ImagePluginComponent.() -> Unit,
): ImagePluginComponent {
  val built = imageComponent(block)
  // Keyed on the plugins rather than on nothing. Mutating the remembered component in place kept
  // its identity and so kept the change from ever reaching an image.
  return remember(built.plugins) { built }
}
