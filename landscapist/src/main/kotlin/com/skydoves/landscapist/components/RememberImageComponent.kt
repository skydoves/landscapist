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
 * @param block The receiver of an instance of [ImagePluginComponent].
 */
@Composable
@LandscapistImagePluginComponentDSL
public fun rememberImageComponent(
  block: @Composable ImagePluginComponent.() -> Unit,
): ImagePluginComponent {
  val imageComponent = imageComponent(block)
  return remember { imageComponent }
}
