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
package com.skydoves.landscapist.constraints

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.skydoves.landscapist.InternalLandscapistApi

@InternalLandscapistApi
public fun Modifier.constraint(boxConstraints: BoxWithConstraintsScope): Modifier {
  val hasValidWidth = boxConstraints.maxWidth.isFinite()
  val hasValidHeight = boxConstraints.maxHeight.isFinite()

  return this
    .then(
      if (hasValidWidth) {
        Modifier.widthIn(min = boxConstraints.minWidth, max = boxConstraints.maxWidth)
      } else {
        Modifier
      },
    )
    .then(
      if (hasValidHeight) {
        Modifier.heightIn(min = boxConstraints.minHeight, max = boxConstraints.maxHeight)
      } else {
        Modifier
      },
    )
}

private fun Dp.isFinite(): Boolean = this != Dp.Infinity && this != Dp.Unspecified
