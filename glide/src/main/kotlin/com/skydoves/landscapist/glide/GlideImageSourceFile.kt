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
package com.skydoves.landscapist.glide

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.bumptech.glide.Glide
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
 * - [String] (network URL): Downloads to Glide's disk cache and returns the cached file
 *
 * For network images, this downloads the image using Glide's downloadOnly() API,
 * which caches the original source bytes to disk.
 *
 * @param context The Android context.
 * @param imageModel The image model (URL, URI, File, etc.)
 * @param dataSource The data source from Glide (DISK, NETWORK, etc.)
 * @return The source file if available, null otherwise.
 */
internal suspend fun getImageSourceFile(
  context: Context,
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
          // For network images, download to disk cache and return the cached file
          downloadToDiskCache(context, imageModel)
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
 * Downloads an image URL to Glide's disk cache and returns the cached file.
 *
 * This uses Glide's downloadOnly() API which downloads the original source bytes
 * without any transformations, making it suitable for region decoding.
 */
private fun downloadToDiskCache(context: Context, url: String): File? {
  return try {
    Glide.with(context)
      .downloadOnly()
      .load(url)
      .submit()
      .get() // This blocks, but we're on Dispatchers.IO
  } catch (e: Exception) {
    null
  }
}

/**
 * Composable that remembers and retrieves the image source file.
 *
 * @param context The Android context.
 * @param imageModel The image model.
 * @param dataSource The data source from Glide.
 * @return The source file if available.
 */
@Composable
internal fun rememberImageSourceFile(
  context: Context,
  imageModel: Any?,
  dataSource: DataSource?,
): State<File?> = produceState(initialValue = null, imageModel, dataSource) {
  value = getImageSourceFile(context, imageModel, dataSource)
}
