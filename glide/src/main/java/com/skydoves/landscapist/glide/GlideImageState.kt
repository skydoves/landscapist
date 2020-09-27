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

package com.skydoves.landscapist.glide

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageAsset
import com.skydoves.landscapist.ImageLoadState

/** GlideImageState is a state of the glide image requesting. */
sealed class GlideImageState {

  /** Request not started. */
  object None : GlideImageState()

  /** Request is currently in progress. */
  data class Loading(val progress: Float) : GlideImageState()

  /** Request is completed successfully amd ready to use an [ImageAsset]. */
  data class Success(val imageAsset: ImageAsset?) : GlideImageState()

  /** Request failed. */
  data class Failure(val errorDrawable: Drawable?) : GlideImageState()
}

/** casts an [ImageLoadState] type to a [GlideImageState]. */
@Suppress("UNCHECKED_CAST")
fun ImageLoadState.toGlideImageState(): GlideImageState {
  return when (this) {
    is ImageLoadState.None -> GlideImageState.None
    is ImageLoadState.Loading -> GlideImageState.Loading(progress)
    is ImageLoadState.Success -> GlideImageState.Success(imageAsset)
    is ImageLoadState.Failure -> GlideImageState.Failure(data as? Drawable)
  }
}
