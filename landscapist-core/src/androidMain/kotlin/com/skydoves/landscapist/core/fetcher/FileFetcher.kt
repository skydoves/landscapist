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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Fetcher for [File] image models.
 *
 * Reads the file contents and returns them as raw bytes for decoding.
 */
public class FileFetcher : ImageFetcher {

  override fun canHandle(model: Any?): Boolean {
    return model is File
  }

  override suspend fun fetch(request: ImageRequest): FetchResult {
    val file = request.model as? File
      ?: return FetchResult.Error(IllegalArgumentException("Model is not a File"))

    return withContext(Dispatchers.IO) {
      try {
        if (!file.exists()) {
          return@withContext FetchResult.Error(
            FileNotFoundException("File does not exist: ${file.absolutePath}"),
          )
        }

        if (!file.canRead()) {
          return@withContext FetchResult.Error(
            SecurityException("Cannot read file: ${file.absolutePath}"),
          )
        }

        val bytes = file.readBytes()

        if (bytes.isEmpty()) {
          return@withContext FetchResult.Error(
            IllegalStateException("File is empty: ${file.absolutePath}"),
          )
        }

        val mimeType = guessMimeType(file.name)

        FetchResult.Success(
          data = bytes,
          mimeType = mimeType,
          dataSource = DataSource.LOCAL,
        )
      } catch (e: Exception) {
        FetchResult.Error(e)
      }
    }
  }

  private fun guessMimeType(fileName: String): String? {
    return when {
      fileName.endsWith(".jpg", ignoreCase = true) ||
        fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
      fileName.endsWith(".png", ignoreCase = true) -> "image/png"
      fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
      fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
      fileName.endsWith(".bmp", ignoreCase = true) -> "image/bmp"
      fileName.endsWith(".heic", ignoreCase = true) ||
        fileName.endsWith(".heif", ignoreCase = true) -> "image/heif"
      else -> null
    }
  }
}
