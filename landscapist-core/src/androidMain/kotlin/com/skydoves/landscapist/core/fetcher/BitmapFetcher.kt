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

import android.graphics.Bitmap
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher

/**
 * Fetcher for [Bitmap] image models.
 *
 * Since bitmaps are already decoded, this fetcher returns [FetchResult.Decoded]
 * to skip the decode step in the loading pipeline.
 */
public class BitmapFetcher : ImageFetcher {

  override fun canHandle(model: Any?): Boolean {
    return model is Bitmap
  }

  override suspend fun fetch(request: ImageRequest): FetchResult {
    val bitmap = request.model as? Bitmap
      ?: return FetchResult.Error(IllegalArgumentException("Model is not a Bitmap"))

    if (bitmap.isRecycled) {
      return FetchResult.Error(IllegalStateException("Bitmap is recycled"))
    }

    return FetchResult.Decoded(
      image = bitmap,
      width = bitmap.width,
      height = bitmap.height,
      dataSource = DataSource.INLINE,
    )
  }
}
