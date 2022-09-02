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
package com.skydoves.landscapist.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.imageLoader

/**
 * Local containing the preferred [ImageLoader] for providing the same instance
 * in our composable hierarchy.
 */
public val LocalCoilImageLoader: ProvidableCompositionLocal<ImageLoader?> =
  staticCompositionLocalOf { null }

/** A provider for taking the local instances related to the `CoilImage`. */
internal object LocalCoilProvider {

  /** Returns the current or default [ImageLoader] for the `ColiImage` parameter. */
  @Composable
  fun getCoilImageLoader(): ImageLoader {
    return LocalCoilImageLoader.current ?: LocalContext.current.imageLoader
  }
}
