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
package com.skydoves.landscapist.core.network

import com.skydoves.landscapist.core.ImageRequest

/**
 * A composite [ImageFetcher] that dispatches to multiple fetchers based on their [canHandle] method.
 *
 * Fetchers are checked in order, and the first fetcher that can handle the model is used.
 * If no fetcher can handle the model, an error is returned.
 *
 * @property fetchers The list of fetchers to delegate to, in priority order.
 */
public class CompositeFetcher(
  private val fetchers: List<ImageFetcher>,
) : ImageFetcher {

  /**
   * Returns true if any of the contained fetchers can handle the given model.
   */
  override fun canHandle(model: Any?): Boolean {
    return fetchers.any { it.canHandle(model) }
  }

  /**
   * Fetches the image by delegating to the first fetcher that can handle the model.
   *
   * @param request The image request.
   * @return The fetch result from the appropriate fetcher.
   */
  override suspend fun fetch(request: ImageRequest): FetchResult {
    val fetcher = fetchers.firstOrNull { it.canHandle(request.model) }
      ?: return FetchResult.Error(
        IllegalArgumentException(
          "No fetcher found that can handle model: " +
            "${request.model?.let { it::class.simpleName } ?: "null"}",
        ),
      )
    return fetcher.fetch(request)
  }

  public companion object {
    /**
     * Creates a [CompositeFetcher] from the given fetchers.
     *
     * @param fetchers The fetchers to include, in priority order.
     * @return A new [CompositeFetcher] instance.
     */
    public fun create(vararg fetchers: ImageFetcher): CompositeFetcher {
      return CompositeFetcher(fetchers.toList())
    }
  }
}
