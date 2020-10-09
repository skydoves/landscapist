/*
 * Designed and developed by 2020 skydoves (Jaewoong Eum)
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

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageAsset
import com.skydoves.landscapist.ImageLoadState

/** CoilImageState is a state of the coil image requesting. */
sealed class CoilImageState {

  /** Request not started. */
  object None : CoilImageState()

  /** Request is currently in progress. */
  data class Loading(val progress: Float) : CoilImageState()

  /** Request is completed successfully amd ready to use an [ImageAsset]. */
  data class Success(val imageAsset: ImageAsset?) : CoilImageState()

  /** Request failed. */
  data class Failure(val errorDrawable: Drawable?) : CoilImageState()
}

/** casts an [ImageLoadState] type to a [CoilImageState]. */
@Suppress("UNCHECKED_CAST")
fun ImageLoadState.toCoilImageState(): CoilImageState {
  return when (this) {
    is ImageLoadState.None -> CoilImageState.None
    is ImageLoadState.Loading -> CoilImageState.Loading(progress)
    is ImageLoadState.Success -> CoilImageState.Success(imageAsset)
    is ImageLoadState.Failure -> CoilImageState.Failure(data as? Drawable)
  }
}
