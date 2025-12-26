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

import com.skydoves.landscapist.core.NetworkConfig
import com.skydoves.landscapist.core.network.CompositeFetcher
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.core.network.KtorImageFetcher

/**
 * Factory for creating Android-specific image fetchers.
 *
 * Provides a [CompositeFetcher] that can handle various Android image model types:
 * - [android.graphics.Bitmap]
 * - [android.graphics.drawable.Drawable]
 * - [ByteArray]
 * - [java.nio.ByteBuffer]
 * - [java.io.File]
 * - [android.net.Uri] (content://, file://, android.resource://, http://, https://)
 * - [DrawableResModel] (for R.drawable.* resources)
 * - [Int] drawable resource IDs (e.g., R.drawable.image)
 * - [String] URLs (http://, https://)
 */
public object AndroidFetchers {

  /**
   * Creates a default [CompositeFetcher] with all Android-specific fetchers.
   *
   * @param networkConfig Configuration for network requests.
   * @return A composite fetcher that can handle all supported Android model types.
   */
  public fun createDefault(networkConfig: NetworkConfig = NetworkConfig()): CompositeFetcher {
    val networkFetcher = KtorImageFetcher.create(networkConfig)
    return createDefault(networkFetcher)
  }

  /**
   * Creates a default [CompositeFetcher] with all Android-specific fetchers.
   *
   * @param networkFetcher The fetcher to use for network requests.
   * @return A composite fetcher that can handle all supported Android model types.
   */
  public fun createDefault(networkFetcher: ImageFetcher): CompositeFetcher {
    return CompositeFetcher(
      listOf(
        // Pre-decoded types (highest priority - no decoding needed)
        BitmapFetcher(),
        DrawableFetcher(),

        // Raw byte types
        ByteArrayFetcher(),
        ByteBufferFetcher(),

        // Local file types
        FileFetcher(),

        // URI types (handles content://, file://, android.resource://, http://, https://)
        UriFetcher(networkFetcher = networkFetcher),

        // Drawable resource IDs (wrapped in DrawableResModel)
        DrawableResFetcher(),

        // Raw Int resource IDs (e.g., R.drawable.image)
        IntResourceFetcher(),

        // Network fetcher for String URLs (lowest priority)
        networkFetcher,
      ),
    )
  }

  /**
   * Creates a [CompositeFetcher] with custom fetchers.
   *
   * @param fetchers The fetchers to include, in priority order.
   * @return A composite fetcher with the provided fetchers.
   */
  public fun create(vararg fetchers: ImageFetcher): CompositeFetcher {
    return CompositeFetcher(fetchers.toList())
  }
}
