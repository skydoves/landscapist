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

import android.content.res.Resources
import androidx.core.content.ContextCompat
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetcher for [Int] image models representing drawable resource IDs (e.g., R.drawable.image).
 *
 * This allows passing drawable resource IDs directly without wrapping in [DrawableResModel]:
 * ```kotlin
 * LandscapistImage(
 *   imageModel = { R.drawable.my_image },
 *   ...
 * )
 * ```
 *
 * Note: Any non-zero Int will be attempted as a drawable resource ID.
 * If the resource doesn't exist, an error will be returned.
 */
public class IntResourceFetcher : ImageFetcher {

  override fun canHandle(model: Any?): Boolean {
    // Handle Int values that could be drawable resource IDs
    // Resource IDs are always positive integers
    return model is Int && model > 0
  }

  override suspend fun fetch(request: ImageRequest): FetchResult {
    val resId = request.model as? Int
      ?: return FetchResult.Error(IllegalArgumentException("Model is not an Int"))

    if (resId <= 0) {
      return FetchResult.Error(IllegalArgumentException("Invalid resource ID: $resId"))
    }

    return withContext(Dispatchers.IO) {
      try {
        val context = AndroidContextProvider.get()
        val drawable = ContextCompat.getDrawable(context, resId)
          ?: return@withContext FetchResult.Error(
            Resources.NotFoundException("Drawable resource not found: $resId"),
          )

        FetchResult.Decoded(
          image = drawable,
          width = drawable.intrinsicWidth.coerceAtLeast(0),
          height = drawable.intrinsicHeight.coerceAtLeast(0),
          dataSource = DataSource.RESOURCE,
        )
      } catch (e: Resources.NotFoundException) {
        FetchResult.Error(e)
      } catch (e: Exception) {
        FetchResult.Error(e)
      }
    }
  }
}
