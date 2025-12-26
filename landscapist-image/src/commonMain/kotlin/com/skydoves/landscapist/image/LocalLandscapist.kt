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
package com.skydoves.landscapist.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.skydoves.landscapist.core.Landscapist

/**
 * CompositionLocal for providing a [Landscapist] instance throughout the composition tree.
 */
public val LocalLandscapist: ProvidableCompositionLocal<Landscapist?> =
  staticCompositionLocalOf { null }

/**
 * Gets the current [Landscapist] instance from the composition.
 * On Android, this automatically creates an instance with disk cache using LocalContext.
 * On other platforms, falls back to the default instance.
 */
@Composable
public expect fun getLandscapist(): Landscapist
