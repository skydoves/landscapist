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
package com.skydoves.landscapist.core.fetcher

import android.graphics.drawable.Drawable
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher

/**
 * Fetcher for [Drawable] image models.
 *
 * Since drawables are already decoded, this fetcher returns [FetchResult.Decoded]
 * to skip the decode step in the loading pipeline.
 */
public class DrawableFetcher : ImageFetcher {

  override fun canHandle(model: Any?): Boolean {
    return model is Drawable
  }

  override suspend fun fetch(request: ImageRequest): FetchResult {
    val drawable = request.model as? Drawable
      ?: return FetchResult.Error(IllegalArgumentException("Model is not a Drawable"))

    return FetchResult.Decoded(
      image = drawable,
      width = drawable.intrinsicWidth.coerceAtLeast(0),
      height = drawable.intrinsicHeight.coerceAtLeast(0),
      dataSource = DataSource.INLINE,
    )
  }
}
