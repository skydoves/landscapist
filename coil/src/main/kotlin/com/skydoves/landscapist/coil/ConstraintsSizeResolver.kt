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
import com.skydoves.landscapist.ZeroConstraints
import com.skydoves.landscapist.constraints.Constrainable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import coil.size.Size as CoilSize

internal class ConstraintsSizeResolver : Constrainable, SizeResolver {

  private val _constraints = MutableStateFlow(ZeroConstraints)

  override suspend fun size() = _constraints.mapNotNull(Constraints::toSizeOrNull).first()

  override fun setConstraints(constraints: Constraints) {
    _constraints.value = constraints
  }
}

@Stable
private fun Constraints.toSizeOrNull() = when {
  isZero -> null
  else -> CoilSize(
    width = if (hasBoundedWidth) Dimension(maxWidth) else Dimension.Undefined,
    height = if (hasBoundedHeight) Dimension(maxHeight) else Dimension.Undefined
  )
}

internal fun SizeResolver.setConstraints(constraints: Constraints) {
  if (this is ConstraintsSizeResolver) {
    setConstraints(constraints)
  }
}
