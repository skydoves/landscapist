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
package com.skydoves.landscapist.palette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.palette.graphics.Palette

/**
 * Create a mutable state and remember the new instance of [Palette] that is wrapped by [MutableState]
 * on the memory.
 *
 * @param value The initial value for the [MutableState].
 * @param policy A policy to controls how changes are handled in mutable snapshots.
 */
@Composable
public fun rememberPaletteState(
  value: Palette?,
  policy: SnapshotMutationPolicy<Palette?> = structuralEqualityPolicy()
): MutableState<Palette?> = remember { mutableStateOf(value = value, policy = policy) }
