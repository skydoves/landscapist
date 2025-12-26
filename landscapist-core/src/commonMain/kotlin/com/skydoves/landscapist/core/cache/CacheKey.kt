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
package com.skydoves.landscapist.core.cache

import okio.ByteString.Companion.encodeUtf8

/**
 * Represents a cache key for storing and retrieving images.
 *
 * @property url The URL or identifier of the image.
 * @property transformationKeys Keys identifying any transformations applied.
 * @property width Target width for the image, if specified.
 * @property height Target height for the image, if specified.
 */
public data class CacheKey(
  val url: String,
  val transformationKeys: List<String> = emptyList(),
  val width: Int? = null,
  val height: Int? = null,
) {
  /**
   * The key used for disk cache storage.
   * Uses SHA-256 hash of the URL with optional size and transformation suffixes.
   */
  public val diskKey: String by lazy {
    val base = url.encodeUtf8().sha256().hex()
    if (transformationKeys.isEmpty() && width == null && height == null) {
      base
    } else {
      val suffix = buildString {
        transformationKeys.forEach { append("_$it") }
        if (width != null && height != null) {
          append("_${width}x$height")
        }
      }
      "${base}_${suffix.hashCode().toString(16)}"
    }
  }

  /**
   * The key used for memory cache storage.
   * Uses the full URL with size and transformation info for faster lookups.
   */
  public val memoryKey: String by lazy {
    buildString {
      append(url)
      transformationKeys.forEach { append("_$it") }
      if (width != null && height != null) {
        append("_${width}x$height")
      }
    }
  }

  public companion object {
    /**
     * Creates a [CacheKey] from an image model.
     *
     * @param model The image model (URL string, Uri, etc.)
     * @param transformationKeys Keys for any transformations.
     * @param width Target width.
     * @param height Target height.
     */
    public fun create(
      model: Any?,
      transformationKeys: List<String> = emptyList(),
      width: Int? = null,
      height: Int? = null,
    ): CacheKey {
      val url = when (model) {
        is String -> model
        null -> ""
        else -> model.toString()
      }
      return CacheKey(
        url = url,
        transformationKeys = transformationKeys,
        width = width,
        height = height,
      )
    }
  }
}
