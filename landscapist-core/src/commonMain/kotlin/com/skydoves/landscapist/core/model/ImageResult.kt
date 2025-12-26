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
package com.skydoves.landscapist.core.model

/**
 * Represents the result of an image loading operation.
 */
public sealed class ImageResult {
  /**
   * The image is currently being loaded.
   */
  public data object Loading : ImageResult()

  /**
   * The image was loaded successfully.
   *
   * @property data The loaded image data as a platform-specific bitmap.
   * @property dataSource The source from which the image was loaded.
   * @property originalWidth The original width of the image before any transformations.
   * @property originalHeight The original height of the image before any transformations.
   * @property rawData The raw image bytes for sub-sampling support, if available.
   * @property diskCachePath The disk cache file path, if the image was loaded from disk.
   * @property isIntermediate Whether this is an intermediate result during progressive loading.
   * @property progress The loading progress (0.0 to 1.0) for progressive loading.
   */
  public data class Success(
    val data: Any,
    val dataSource: DataSource,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val rawData: ByteArray? = null,
    val diskCachePath: String? = null,
    val isIntermediate: Boolean = false,
    val progress: Float = 1f,
  ) : ImageResult() {

    /** Returns true if this is the final (non-intermediate) result. */
    val isFinal: Boolean get() = !isIntermediate

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false

      other as Success

      if (data != other.data) return false
      if (dataSource != other.dataSource) return false
      if (originalWidth != other.originalWidth) return false
      if (originalHeight != other.originalHeight) return false
      if (rawData != null) {
        if (other.rawData == null) return false
        if (!rawData.contentEquals(other.rawData)) return false
      } else if (other.rawData != null) return false
      if (diskCachePath != other.diskCachePath) return false
      if (isIntermediate != other.isIntermediate) return false
      if (progress != other.progress) return false

      return true
    }

    override fun hashCode(): Int {
      var result = data.hashCode()
      result = 31 * result + dataSource.hashCode()
      result = 31 * result + originalWidth
      result = 31 * result + originalHeight
      result = 31 * result + (rawData?.contentHashCode() ?: 0)
      result = 31 * result + (diskCachePath?.hashCode() ?: 0)
      result = 31 * result + isIntermediate.hashCode()
      result = 31 * result + progress.hashCode()
      return result
    }
  }

  /**
   * The image failed to load.
   *
   * @property throwable The exception that caused the failure, if any.
   * @property message An optional error message.
   */
  public data class Failure(
    val throwable: Throwable? = null,
    val message: String? = null,
  ) : ImageResult()
}
