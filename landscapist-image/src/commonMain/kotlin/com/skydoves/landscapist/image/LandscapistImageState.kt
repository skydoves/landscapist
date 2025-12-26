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
package com.skydoves.landscapist.image

import androidx.compose.runtime.Immutable
import com.skydoves.landscapist.ImageState
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.model.ImageResult

/**
 * Represents the state of image loading for [LandscapistImage].
 */
@Immutable
public sealed class LandscapistImageState : ImageState {

  /**
   * The image has not started loading.
   */
  @Immutable
  public data object None : LandscapistImageState()

  /**
   * The image is currently loading.
   */
  @Immutable
  public data object Loading : LandscapistImageState()

  /**
   * The image was loaded successfully.
   *
   * @property data The loaded image data (platform-specific bitmap).
   * @property dataSource The source from which the image was loaded.
   * @property originalWidth The original width of the image before any transformations.
   * @property originalHeight The original height of the image before any transformations.
   * @property rawData The raw image bytes for sub-sampling support.
   * @property diskCachePath The disk cache file path, if available.
   */
  @Immutable
  public data class Success(
    val data: Any?,
    val dataSource: DataSource,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val rawData: ByteArray? = null,
    val diskCachePath: String? = null,
  ) : LandscapistImageState() {
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

      return true
    }

    override fun hashCode(): Int {
      var result = data?.hashCode() ?: 0
      result = 31 * result + dataSource.hashCode()
      result = 31 * result + originalWidth
      result = 31 * result + originalHeight
      result = 31 * result + (rawData?.contentHashCode() ?: 0)
      result = 31 * result + (diskCachePath?.hashCode() ?: 0)
      return result
    }
  }

  /**
   * The image failed to load.
   *
   * @property reason The exception that caused the failure.
   */
  @Immutable
  public data class Failure(
    val reason: Throwable?,
  ) : LandscapistImageState()
}

/**
 * Converts an [ImageResult] to a [LandscapistImageState].
 */
internal fun ImageResult.toLandscapistImageState(): LandscapistImageState = when (this) {
  is ImageResult.Loading -> LandscapistImageState.Loading
  is ImageResult.Success -> LandscapistImageState.Success(
    data = data,
    dataSource = dataSource,
    originalWidth = originalWidth,
    originalHeight = originalHeight,
    rawData = rawData,
    diskCachePath = diskCachePath,
  )
  is ImageResult.Failure -> LandscapistImageState.Failure(
    reason = throwable,
  )
}
