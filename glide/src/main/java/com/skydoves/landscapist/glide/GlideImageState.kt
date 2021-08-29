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
import androidx.compose.ui.graphics.ImageBitmap
import com.skydoves.landscapist.ImageLoadState

/** GlideImageState is a state of the glide image requesting. */
public sealed class GlideImageState {

  /** Request not started. */
  public object None : GlideImageState()

  /** Request is currently in progress. */
  public data class Loading(val progress: Float) : GlideImageState()

  /** Request is completed successfully and ready to use an [ImageBitmap]. */
  public data class Success(val imageBitmap: ImageBitmap?) : GlideImageState()

  /** Request failed. */
  public data class Failure(val errorDrawable: Drawable?) : GlideImageState()
}

/** casts an [ImageLoadState] type to a [GlideImageState]. */
public fun ImageLoadState.toGlideImageState(): GlideImageState {
  return when (this) {
    is ImageLoadState.None -> GlideImageState.None
    is ImageLoadState.Loading -> GlideImageState.Loading(progress)
    is ImageLoadState.Success -> GlideImageState.Success(imageBitmap)
    is ImageLoadState.Failure -> GlideImageState.Failure(data as? Drawable)
  }
}
