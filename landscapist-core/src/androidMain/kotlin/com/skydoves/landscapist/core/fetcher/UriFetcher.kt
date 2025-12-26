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

import android.content.ContentResolver
import android.net.Uri
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.model.DataSource
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.core.network.KtorImageFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetcher for [Uri] image models.
 *
 * Supports the following URI schemes:
 * - `content://` - Content provider URIs
 * - `file://` - Local file URIs
 * - `android.resource://` - Android resource URIs
 * - `http://` and `https://` - Network URIs (delegated to [KtorImageFetcher])
 *
 * @param networkFetcher Optional fetcher for network URIs. If null, network URIs will fail.
 */
public class UriFetcher(
  private val networkFetcher: ImageFetcher? = null,
) : ImageFetcher {

  override fun canHandle(model: Any?): Boolean {
    return model is Uri
  }

  override suspend fun fetch(request: ImageRequest): FetchResult {
    val uri = request.model as? Uri
      ?: return FetchResult.Error(IllegalArgumentException("Model is not a Uri"))

    return when (uri.scheme?.lowercase()) {
      ContentResolver.SCHEME_CONTENT -> fetchContentUri(uri)
      ContentResolver.SCHEME_FILE -> fetchFileUri(uri)
      ContentResolver.SCHEME_ANDROID_RESOURCE -> fetchResourceUri(uri)
      "http", "https" -> fetchNetworkUri(request)
      else -> FetchResult.Error(
        IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}"),
      )
    }
  }

  private suspend fun fetchContentUri(uri: Uri): FetchResult {
    return withContext(Dispatchers.IO) {
      try {
        val context = AndroidContextProvider.get()
        val inputStream = context.contentResolver.openInputStream(uri)
          ?: return@withContext FetchResult.Error(
            IllegalStateException("Cannot open input stream for URI: $uri"),
          )

        val bytes = inputStream.use { it.readBytes() }
        val mimeType = context.contentResolver.getType(uri)

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

  private suspend fun fetchFileUri(uri: Uri): FetchResult {
    return withContext(Dispatchers.IO) {
      try {
        val path = uri.path
          ?: return@withContext FetchResult.Error(
            IllegalArgumentException("File URI has no path: $uri"),
          )

        val file = java.io.File(path)
        if (!file.exists()) {
          return@withContext FetchResult.Error(
            java.io.FileNotFoundException("File does not exist: $path"),
          )
        }

        if (!file.canRead()) {
          return@withContext FetchResult.Error(
            SecurityException("Cannot read file: $path"),
          )
        }

        val bytes = file.readBytes()
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

  private suspend fun fetchResourceUri(uri: Uri): FetchResult {
    return withContext(Dispatchers.IO) {
      try {
        val context = AndroidContextProvider.get()

        // Parse resource URI: android.resource://[package]/[res_type]/[res_name]
        // or android.resource://[package]/[res_id]
        val pathSegments = uri.pathSegments
        val resourceId = when {
          pathSegments.size == 1 -> {
            // android.resource://package/resId
            pathSegments[0].toIntOrNull()
              ?: return@withContext FetchResult.Error(
                IllegalArgumentException("Invalid resource ID in URI: $uri"),
              )
          }
          pathSegments.size == 2 -> {
            // android.resource://package/type/name
            val packageName = uri.authority ?: context.packageName
            val type = pathSegments[0]
            val name = pathSegments[1]
            context.resources.getIdentifier(name, type, packageName).takeIf { it != 0 }
              ?: return@withContext FetchResult.Error(
                IllegalArgumentException("Resource not found: $uri"),
              )
          }
          else -> return@withContext FetchResult.Error(
            IllegalArgumentException("Invalid resource URI format: $uri"),
          )
        }

        val inputStream = context.resources.openRawResource(resourceId)
        val bytes = inputStream.use { it.readBytes() }

        FetchResult.Success(
          data = bytes,
          mimeType = null,
          dataSource = DataSource.RESOURCE,
        )
      } catch (e: Exception) {
        FetchResult.Error(e)
      }
    }
  }

  private suspend fun fetchNetworkUri(request: ImageRequest): FetchResult {
    val fetcher = networkFetcher
      ?: return FetchResult.Error(
        IllegalStateException("No network fetcher available for URI: ${request.model}"),
      )

    // Create a new request with the URI as a string for the network fetcher
    val uri = request.model as Uri
    val builder = ImageRequest.builder()
      .model(uri.toString())
      .memoryCachePolicy(request.memoryCachePolicy)
      .diskCachePolicy(request.diskCachePolicy)
      .headers(request.headers)
      .priority(request.priority)
      .tag(request.tag)
      .progressiveEnabled(request.progressiveEnabled)

    // Only set size if both dimensions are specified
    val width = request.targetWidth
    val height = request.targetHeight
    if (width != null && height != null) {
      builder.size(width, height)
    }

    val networkRequest = builder.build()

    return fetcher.fetch(networkRequest)
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
