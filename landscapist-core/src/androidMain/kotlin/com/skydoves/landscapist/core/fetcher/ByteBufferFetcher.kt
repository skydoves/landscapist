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

import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import java.nio.ByteBuffer

/**
 * Fetcher for [ByteBuffer] image models.
 *
 * The byte buffer is converted to a ByteArray for decoding.
 */
public class ByteBufferFetcher : ImageFetcher {

  override fun canHandle(model: Any?): Boolean {
    return model is ByteBuffer
  }

  override suspend fun fetch(request: ImageRequest): FetchResult {
    val buffer = request.model as? ByteBuffer
      ?: return FetchResult.Error(IllegalArgumentException("Model is not a ByteBuffer"))

    if (!buffer.hasRemaining()) {
      return FetchResult.Error(IllegalArgumentException("ByteBuffer is empty"))
    }

    // Convert ByteBuffer to ByteArray
    val bytes = ByteArray(buffer.remaining())
    buffer.duplicate().get(bytes)

    return FetchResult.Success(
      data = bytes,
      mimeType = null,
      dataSource = DataSource.INLINE,
    )
  }
}
