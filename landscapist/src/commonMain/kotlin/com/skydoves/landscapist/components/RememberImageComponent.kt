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
 * The component keeps its identity for as long as the composable does, and its plugins follow
 * [block]. A `remember` with no keys returned the component built on the first composition, so a
 * plugin set that depends on state was frozen at whatever it was then and a plugin added, removed
 * or reconfigured afterwards never reached the image.
 *
 * @param block The receiver of an instance of [ImagePluginComponent].
 */
@Composable
@LandscapistImagePluginComponentDSL
public fun rememberImageComponent(
  block: @Composable ImagePluginComponent.() -> Unit,
): ImagePluginComponent {
  val built = imageComponent(block)
  val component = remember { built }
  // Contents rather than identity, because a caller may hold the component across recompositions
  // and a new one each time would rebuild whatever they keyed on it.
  if (component !== built && component.plugins != built.plugins) {
    component.mutablePlugins.clear()
    component.mutablePlugins.addAll(built.plugins)
  }
  return component
}
