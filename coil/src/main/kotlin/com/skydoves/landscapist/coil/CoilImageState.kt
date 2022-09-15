/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.skydoves.landscapist.DataSource
import com.skydoves.landscapist.ImageLoadState
import com.skydoves.landscapist.ImageState

/** GlideImageState represents the image loading states for Coil. */
@Immutable
public sealed class CoilImageState : ImageState {

  /** Request not started. */
  @Immutable
  public object None : CoilImageState()

  /** Request is currently in progress. */
  @Immutable
  public object Loading : CoilImageState()

  /** Request is completed successfully and ready to use an [ImageBitmap]. */
  @Immutable
  public data class Success(val drawable: Drawable?, val dataSource: DataSource) : CoilImageState()

  /** Request failed. */
  @Immutable
  public data class Failure(val errorDrawable: Drawable?, val reason: Throwable?) : CoilImageState()
}

/** casts an [ImageLoadState] type to a [CoilImageState]. */
public fun ImageLoadState.toCoilImageState(): CoilImageState {
  return when (this) {
    is ImageLoadState.None -> CoilImageState.None
    is ImageLoadState.Loading -> CoilImageState.Loading
    is ImageLoadState.Success -> CoilImageState.Success(
      drawable = data as? Drawable,
      dataSource = dataSource
    )
    is ImageLoadState.Failure -> CoilImageState.Failure(
      errorDrawable = data as? Drawable,
      reason = reason
    )
  }
}
