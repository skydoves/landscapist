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
package com.skydoves.landscapist.coil3

import androidx.compose.ui.unit.Constraints
import coil3.size.Dimension
import coil3.size.Size
import com.skydoves.landscapist.ZeroConstraints
import com.skydoves.landscapist.constraints.Constrainable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

internal class ConstraintsSizeResolver : Constrainable, coil3.size.SizeResolver {

  private val _constraints = MutableStateFlow(ZeroConstraints)

  override suspend fun size() = _constraints.mapNotNull(Constraints::inferredCoilSize).first()

  override fun setConstraints(constraints: Constraints) {
    _constraints.value = constraints
  }
}

private fun Constraints.inferredCoilSize(): Size? = when {
  isZero -> null
  else -> Size(
    width = if (hasBoundedWidth) Dimension(maxWidth) else Dimension.Undefined,
    height = if (hasBoundedHeight) Dimension(maxHeight) else Dimension.Undefined,
  )
}
