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
package com.skydoves.landscapist.core

import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.transformation.Transformation

/**
 * Represents a request for loading an image.
 *
 * @property model The image source (URL string, Uri, File, etc.)
 * @property memoryCachePolicy The policy for memory caching.
 * @property diskCachePolicy The policy for disk caching.
 * @property headers Additional HTTP headers for the request.
 * @property transformations List of transformations to apply.
 * @property targetWidth Target width for the loaded image.
 * @property targetHeight Target height for the loaded image.
 */
public data class ImageRequest(
  val model: Any?,
  val memoryCachePolicy: CachePolicy = CachePolicy.ENABLED,
  val diskCachePolicy: CachePolicy = CachePolicy.ENABLED,
  val headers: Map<String, String> = emptyMap(),
  val transformations: List<Transformation> = emptyList(),
  val targetWidth: Int? = null,
  val targetHeight: Int? = null,
) {
  /**
   * Builder for creating [ImageRequest] instances.
   */
  public class Builder {
    private var model: Any? = null
    private var memoryCachePolicy: CachePolicy = CachePolicy.ENABLED
    private var diskCachePolicy: CachePolicy = CachePolicy.ENABLED
    private var headers: MutableMap<String, String> = mutableMapOf()
    private var transformations: MutableList<Transformation> = mutableListOf()
    private var targetWidth: Int? = null
    private var targetHeight: Int? = null

    /** Sets the image source. */
    public fun model(model: Any?): Builder = apply { this.model = model }

    /** Sets the memory cache policy. */
    public fun memoryCachePolicy(policy: CachePolicy): Builder = apply {
      this.memoryCachePolicy = policy
    }

    /** Sets the disk cache policy. */
    public fun diskCachePolicy(policy: CachePolicy): Builder = apply {
      this.diskCachePolicy = policy
    }

    /** Adds an HTTP header. */
    public fun addHeader(name: String, value: String): Builder = apply {
      this.headers[name] = value
    }

    /** Sets all HTTP headers. */
    public fun headers(headers: Map<String, String>): Builder = apply {
      this.headers.clear()
      this.headers.putAll(headers)
    }

    /** Adds a transformation. */
    public fun addTransformation(transformation: Transformation): Builder = apply {
      this.transformations.add(transformation)
    }

    /** Sets all transformations. */
    public fun transformations(transformations: List<Transformation>): Builder = apply {
      this.transformations.clear()
      this.transformations.addAll(transformations)
    }

    /** Sets the target size. */
    public fun size(width: Int, height: Int): Builder = apply {
      this.targetWidth = width
      this.targetHeight = height
    }

    /** Builds the [ImageRequest]. */
    public fun build(): ImageRequest = ImageRequest(
      model = model,
      memoryCachePolicy = memoryCachePolicy,
      diskCachePolicy = diskCachePolicy,
      headers = headers.toMap(),
      transformations = transformations.toList(),
      targetWidth = targetWidth,
      targetHeight = targetHeight,
    )
  }

  public companion object {
    /** Creates a new [Builder] instance. */
    public fun builder(): Builder = Builder()
  }
}
