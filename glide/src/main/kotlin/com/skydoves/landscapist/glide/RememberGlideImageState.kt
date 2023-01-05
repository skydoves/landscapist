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
package com.skydoves.landscapist.glide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Create and remember [GlideImageState].
 *
 * @param initialState The initial state of [GlideImageState].
 * @param key The key that may trigger recomposition.
 */
@Composable
public fun rememberGlideImageState(
  initialState: GlideImageState = GlideImageState.None,
  key: Any? = null
): MutableState<GlideImageState> = remember(key1 = key) { mutableStateOf(initialState) }
