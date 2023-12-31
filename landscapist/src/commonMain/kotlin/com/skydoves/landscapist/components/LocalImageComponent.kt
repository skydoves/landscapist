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

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Local containing the preferred [ImageComponent] for providing the same instance
 * in the composable hierarchy.
 */
public val LocalImageComponent: ProvidableCompositionLocal<ImageComponent> =
  compositionLocalOf { LocalImageComponentProvider.defaultImageComponent() }

/** A provider for taking the local instances related to the [ImageComponent]. */
internal object LocalImageComponentProvider {

  /** Returns the current or default [ImageComponent] for the `shimmerParams` parameter. */
  fun defaultImageComponent(): ImageComponent {
    return ImagePluginComponent()
  }
}
