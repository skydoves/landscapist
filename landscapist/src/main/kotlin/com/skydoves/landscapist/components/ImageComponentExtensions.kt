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
package com.skydoves.landscapist.components

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
