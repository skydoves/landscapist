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
@file:OptIn(coil.annotation.ExperimentalCoilApi::class)

package com.skydoves.landscapist.coil

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.skydoves.landscapist.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Attempts to get the source file for an image model.
 *
 * This function handles various image model types:
 * - [File]: Returns the file directly
 * - [Uri] with file:// scheme: Returns the file from the URI path
 * - [Uri] with content:// scheme: Attempts to resolve the actual file path
 * - [String] starting with "file://": Returns the file from the path
 * - [String] (network URL): Attempts to get from Coil's disk cache
 *
 * @param context The Android context.
 * @param imageLoader The Coil ImageLoader with disk cache.
 * @param imageModel The image model (URL, URI, File, etc.)
 * @param dataSource The data source from Coil (DISK, NETWORK, etc.)
 * @return The source file if available, null otherwise.
 */
internal suspend fun getImageSourceFile(
  context: Context,
  imageLoader: ImageLoader,
  imageModel: Any?,
  dataSource: DataSource?,
): File? = withContext(Dispatchers.IO) {
  when (imageModel) {
    is File -> imageModel.takeIf { it.exists() && it.canRead() }

    is Uri -> resolveUriToFile(context, imageModel)

    is String -> {
      when {
        imageModel.startsWith("file://") -> {
          val path = imageModel.removePrefix("file://")
          File(path).takeIf { it.exists() && it.canRead() }
        }

        imageModel.startsWith("content://") -> {
          resolveUriToFile(context, Uri.parse(imageModel))
        }

        imageModel.startsWith("http://") || imageModel.startsWith("https://") -> {
          // For network images, try to get from disk cache or download
          getFromDiskCacheOrDownload(context, imageLoader, imageModel)
        }

        else -> {
          // Try as a file path
          File(imageModel).takeIf { it.exists() && it.canRead() }
        }
      }
    }

    else -> null
  }
}

/**
 * Resolves a URI to a file path.
 */
private fun resolveUriToFile(context: Context, uri: Uri): File? {
  return when (uri.scheme) {
    ContentResolver.SCHEME_FILE -> {
      uri.path?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
    }

    ContentResolver.SCHEME_CONTENT -> {
      // Try to get the actual file path from content URI
      try {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val path = cursor.getString(columnIndex)
            path?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
          } else {
            null
          }
        }
      } catch (e: Exception) {
        null
      }
    }

    else -> null
  }
}

/**
 * Gets an image from Coil's disk cache or downloads it.
 *
 * This uses Coil's ImageRequest API to ensure the image is in disk cache,
 * then retrieves the cached file path.
 */
private suspend fun getFromDiskCacheOrDownload(
  context: Context,
  imageLoader: ImageLoader,
  url: String,
): File? {
  return try {
    // Try to get from disk cache first
    val diskCache = imageLoader.diskCache
    val cacheKey = url // Coil uses the URL as the default cache key

    // Check if it's in disk cache
    val snapshot = diskCache?.openSnapshot(cacheKey)
    if (snapshot != null) {
      val file = snapshot.data.toFile()
      snapshot.close()
      return file.takeIf { it.exists() && it.canRead() }
    }

    // If not in cache, execute a request to download it
    val request = ImageRequest.Builder(context)
      .data(url)
      .diskCachePolicy(CachePolicy.ENABLED)
      .build()

    val result = imageLoader.execute(request)

    // Now try to get from disk cache again
    val newSnapshot = diskCache?.openSnapshot(cacheKey)
    if (newSnapshot != null) {
      val file = newSnapshot.data.toFile()
      newSnapshot.close()
      file.takeIf { it.exists() && it.canRead() }
    } else {
      null
    }
  } catch (e: Exception) {
    null
  }
}

/**
 * Composable that remembers and retrieves the image source file.
 *
 * @param context The Android context.
 * @param imageLoader The Coil ImageLoader.
 * @param imageModel The image model.
 * @param dataSource The data source from Coil.
 * @return The source file if available.
 */
@Composable
internal fun rememberImageSourceFile(
  context: Context,
  imageLoader: ImageLoader,
  imageModel: Any?,
  dataSource: DataSource?,
): State<File?> = produceState(initialValue = null, imageModel, dataSource) {
  value = getImageSourceFile(context, imageLoader, imageModel, dataSource)
}
