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
package com.skydoves.landscapist.fresco

import androidx.compose.ui.graphics.ImageBitmap
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.imagepipeline.image.CloseableImage
import com.skydoves.landscapist.ImageLoadState

/** FrescoImageState is a state of the fresco image requesting. */
public sealed class FrescoImageState {

  /** Request not started. */
  public object None : FrescoImageState()

  /** Request is currently in progress. */
  public data class Loading(val progress: Float) : FrescoImageState()

  /** Request is completed successfully and ready to use an [ImageBitmap]. */
  public data class Success(val imageBitmap: ImageBitmap?) : FrescoImageState()

  /** Request failed. */
  public data class Failure(val dataSource: DataSource<CloseableReference<CloseableImage>>?) :
    FrescoImageState()
}

/** casts an [ImageLoadState] type to a [FrescoImageState]. */
@Suppress("UNCHECKED_CAST")
public fun ImageLoadState.toFrescoImageState(): FrescoImageState {
  return when (this) {
    is ImageLoadState.None -> FrescoImageState.None
    is ImageLoadState.Loading -> FrescoImageState.Loading(progress)
    is ImageLoadState.Success -> FrescoImageState.Success(data as? ImageBitmap)
    is ImageLoadState.Failure -> FrescoImageState.Failure(
      data as? DataSource<CloseableReference<CloseableImage>>
    )
  }
}
