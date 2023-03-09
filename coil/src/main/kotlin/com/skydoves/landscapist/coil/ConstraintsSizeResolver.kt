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
package com.skydoves.landscapist.coil

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Constraints
import coil.size.Dimension
import coil.size.SizeResolver
import coil.size.Size as CoilSize

internal class ConstraintsSizeResolver constructor(
  private val constraints: Constraints
) : SizeResolver {

  override suspend fun size(): coil.size.Size = constraints.toSizeCoilSize()
}

@Stable
private fun Constraints.toSizeCoilSize() = when {
  isZero -> coil.size.Size.ORIGINAL
  else -> CoilSize(
    width = if (hasBoundedWidth) Dimension(maxWidth) else Dimension.Undefined,
    height = if (hasBoundedHeight) Dimension(maxHeight) else Dimension.Undefined
  )
}
