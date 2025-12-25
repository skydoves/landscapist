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
package com.skydoves.landscapist.coil3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.PlatformContext
import com.skydoves.landscapist.DataSource
import com.skydoves.landscapist.ProvideImageSourceBytes
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.use

/**
 * Skia implementation that provides the image source bytes for sub-sampling support.
 *
 * This retrieves the image bytes from Coil3's disk cache and provides them via
 * [LocalImageSourceBytes] to enable sub-sampling in the zoomable plugin.
 */
@Composable
internal actual fun ProvideCoilSourceFile(
  imageLoader: ImageLoader,
  imageModel: Any?,
  dataSource: DataSource?,
  content: @Composable () -> Unit,
) {
  val context = platformContext
  var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }

  LaunchedEffect(imageModel, dataSource) {
    sourceBytes = getImageSourceBytes(context, imageLoader, imageModel)
  }

  ProvideImageSourceBytes(bytes = sourceBytes) {
    content()
  }
}

/**
 * Attempts to get the source bytes for an image model from Coil3's disk cache.
 *
 * @param context The platform context.
 * @param imageLoader The Coil3 ImageLoader with disk cache.
 * @param imageModel The image model (URL, etc.)
 * @return The image bytes if available, null otherwise.
 */
private suspend fun getImageSourceBytes(
  context: PlatformContext,
  imageLoader: ImageLoader,
  imageModel: Any?,
): ByteArray? = withContext(Dispatchers.Default) {
  when (imageModel) {
    is String -> {
      when {
        imageModel.startsWith("http://") || imageModel.startsWith("https://") -> {
          getFromDiskCache(context, imageLoader, imageModel)
        }
        else -> null
      }
    }
    else -> null
  }
}

/**
 * Gets image bytes from Coil3's disk cache or by fetching directly.
 *
 * This tries multiple approaches to get the image bytes:
 * 1. First tries to find it in disk cache using the URL as key
 * 2. If not found, fetches the image and reads from the result
 */
private suspend fun getFromDiskCache(
  context: PlatformContext,
  imageLoader: ImageLoader,
  url: String,
): ByteArray? {
  return try {
    val diskCache = imageLoader.diskCache

    // Try disk cache first with URL as key
    if (diskCache != null) {
      val snapshot = diskCache.openSnapshot(url)
      if (snapshot != null) {
        val bytes = snapshot.use { snap ->
          diskCache.fileSystem.read(snap.data) {
            readByteArray()
          }
        }
        if (bytes != null && bytes.isNotEmpty()) {
          return bytes
        }
      }
    }

    // If not in cache, fetch the image bytes directly using ktor
    fetchImageBytes(url)
  } catch (e: Exception) {
    null
  }
}

/**
 * Fetches image bytes directly from the URL.
 */
private suspend fun fetchImageBytes(url: String): ByteArray? {
  return try {
    val client = io.ktor.client.HttpClient()
    val response = client.get(url)
    val bytes = response.body<ByteArray>()
    client.close()
    bytes
  } catch (e: Exception) {
    null
  }
}
