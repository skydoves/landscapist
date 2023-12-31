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
package com.skydoves.landscapist

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** ImageLoadState is a generic interface that represents image loading states. */
@Immutable
public sealed class ImageLoadState {

  /** Request not started. */
  @Immutable
  public data object None : ImageLoadState()

  /** Request is currently in progress. */
  @Immutable
  public data object Loading : ImageLoadState()

  /** Request is completed successfully and ready to use an [ImageBitmap]. */
  @Immutable
  public data class Success(val data: Any?, val dataSource: DataSource) : ImageLoadState()

  /** Request failed. */
  @Immutable
  public data class Failure(val data: Any?, val reason: Throwable?) : ImageLoadState()
}
