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
package com.skydoves.landscapist.fresco

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder

/**
 * Local containing the preferred [ImageRequest] for providing the same instance
 * in our composable hierarchy.
 */
public val LocalFrescoImageRequest: ProvidableCompositionLocal<ImageRequestBuilder?> =
  staticCompositionLocalOf { null }

/** A provider for taking the local instances related to the `FrescoImage`. */
internal object LocalFrescoProvider {

  /** Returns the current or default [ImageRequest] for the `FrescoImage` parameter. */
  @Composable
  fun getFrescoImageRequest(imageUrl: String?): ImageRequestBuilder {
    return LocalFrescoImageRequest.current
      ?: ImageRequestBuilder.newBuilderWithSource(Uri.parse(imageUrl ?: ""))
  }
}
