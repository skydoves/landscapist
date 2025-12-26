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
import com.skydoves.landscapist.core.model.DataSource

/**
 * Interface for fetching image data from various sources.
 */
public interface ImageFetcher {
  /**
   * Fetches image data based on the request.
   *
   * @param request The image request.
   * @return The fetch result containing raw image data or an error.
   */
  public suspend fun fetch(request: ImageRequest): FetchResult

  /**
   * Returns true if this fetcher can handle the given model.
   *
   * @param model The image model.
   * @return true if this fetcher can handle the model.
   */
  public fun canHandle(model: Any?): Boolean
}

/**
 * Result of a fetch operation.
 */
public sealed class FetchResult {
  /**
   * Successful fetch containing raw image data.
   *
   * @property data The raw image data as a byte array.
   * @property mimeType The MIME type of the image, if known.
   * @property dataSource The source from which the data was fetched.
   */
  public data class Success(
    val data: ByteArray,
    val mimeType: String? = null,
    val dataSource: DataSource = DataSource.NETWORK,
  ) : FetchResult() {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false

      other as Success

      if (!data.contentEquals(other.data)) return false
      if (mimeType != other.mimeType) return false
      if (dataSource != other.dataSource) return false

      return true
    }

    override fun hashCode(): Int {
      var result = data.contentHashCode()
      result = 31 * result + (mimeType?.hashCode() ?: 0)
      result = 31 * result + dataSource.hashCode()
      return result
    }
  }

  /**
   * Failed fetch with an error.
   *
   * @property throwable The exception that caused the failure.
   */
  public data class Error(
    val throwable: Throwable,
  ) : FetchResult()

  /**
   * Pre-decoded image that skips the decode step.
   *
   * Use this for image types that are already decoded (e.g., Bitmap, Drawable).
   *
   * @property image The decoded image (e.g., Bitmap or Drawable on Android).
   * @property width The width of the image in pixels.
   * @property height The height of the image in pixels.
   * @property dataSource The source from which the data was fetched.
   */
  public data class Decoded(
    val image: Any,
    val width: Int,
    val height: Int,
    val dataSource: DataSource = DataSource.INLINE,
  ) : FetchResult()
}
