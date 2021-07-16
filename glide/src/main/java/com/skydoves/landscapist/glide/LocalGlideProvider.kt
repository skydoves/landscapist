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

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestOptions

/**
 * Local containing the preferred [RequestOptions] for providing the same instance
 * in our composable hierarchy.
 */
public val LocalGlideRequestOptions: ProvidableCompositionLocal<RequestOptions?> =
  staticCompositionLocalOf { null }

/**
 * Local containing the preferred [RequestBuilder] for providing the same instance
 * in our composable hierarchy.
 */
public val LocalGlideRequestBuilder: ProvidableCompositionLocal<RequestBuilder<Bitmap>?> =
  staticCompositionLocalOf { null }

/** A provider for taking the local instances related to the `GlideImage`. */
internal object LocalGlideProvider {

  /** Returns the current or default [RequestOptions] for the `GlideImage` parameter. */
  @Composable
  fun getGlideRequestOptions(): RequestOptions {
    return LocalGlideRequestOptions.current ?: RequestOptions()
  }

  /** Returns the current or default [RequestBuilder] for the `GlideImage` parameter. */
  @Composable
  fun getGlideRequestBuilder(imageModel: Any): RequestBuilder<Bitmap> {
    return LocalGlideRequestBuilder.current
      ?: Glide
        .with(LocalView.current)
        .asBitmap()
        .load(imageModel)
  }
}
