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
package com.skydoves.landscapist.core.interceptor

import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.event.currentTimeMillis
import com.skydoves.landscapist.core.model.ImageResult

/**
 * Observes, modifies, and potentially short-circuits image loading requests.
 *
 * Interceptors form a chain where each interceptor can:
 * - Modify the request before passing it to the next interceptor
 * - Modify the result after receiving it from the next interceptor
 * - Short-circuit the chain by returning a result without calling the next interceptor
 *
 * This is similar to OkHttp's interceptor pattern but adapted for image loading.
 */
public interface Interceptor {

  /**
   * Intercepts an image loading request.
   *
   * @param chain The interceptor chain to continue processing.
   * @return The result of the image loading operation.
   */
  public suspend fun intercept(chain: Chain): ImageResult

  /**
   * Represents the chain of interceptors.
   */
  public interface Chain {
    /**
     * The current request being processed.
     */
    public val request: ImageRequest

    /**
     * Proceeds with the given request to the next interceptor in the chain.
     *
     * @param request The request to process.
     * @return The result from the remaining interceptors.
     */
    public suspend fun proceed(request: ImageRequest): ImageResult
  }
}

/**
 * An interceptor that logs requests and results.
 */
public class LoggingInterceptor(
  private val tag: String = "Landscapist",
  private val logger: (String) -> Unit = { println(it) },
) : Interceptor {

  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    val request = chain.request
    logger("[$tag] Loading: ${request.model}")

    val startTime = currentTimeMillis()
    val result = chain.proceed(request)
    val duration = currentTimeMillis() - startTime

    when (result) {
      is ImageResult.Success ->
        logger("[$tag] Success: ${request.model} from ${result.dataSource} (${duration}ms)")
      is ImageResult.Failure -> {
        val error = result.throwable?.message ?: result.message ?: "unknown"
        logger("[$tag] Failure: ${request.model} - $error (${duration}ms)")
      }
      is ImageResult.Loading ->
        logger("[$tag] Loading: ${request.model}")
    }

    return result
  }
}

/**
 * An interceptor that adds default headers to requests.
 */
public class HeaderInterceptor(
  private val headers: Map<String, String>,
) : Interceptor {

  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    val request = chain.request
    val newRequest = ImageRequest(
      model = request.model,
      memoryCachePolicy = request.memoryCachePolicy,
      diskCachePolicy = request.diskCachePolicy,
      headers = headers + request.headers, // Request headers override defaults
      transformations = request.transformations,
      targetWidth = request.targetWidth,
      targetHeight = request.targetHeight,
      tag = request.tag,
    )
    return chain.proceed(newRequest)
  }
}

/**
 * An interceptor that modifies the request URL.
 */
public class UrlRewriteInterceptor(
  private val rewriter: (String) -> String,
) : Interceptor {

  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    val request = chain.request
    val model = request.model

    val newModel = if (model is String) {
      rewriter(model)
    } else {
      model
    }

    val newRequest = ImageRequest(
      model = newModel,
      memoryCachePolicy = request.memoryCachePolicy,
      diskCachePolicy = request.diskCachePolicy,
      headers = request.headers,
      transformations = request.transformations,
      targetWidth = request.targetWidth,
      targetHeight = request.targetHeight,
      tag = request.tag,
    )
    return chain.proceed(newRequest)
  }
}

/**
 * An interceptor that retries failed requests.
 */
public class RetryInterceptor(
  private val maxRetries: Int = 3,
  private val retryOnError: (Throwable?) -> Boolean = { true },
) : Interceptor {

  override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
    var lastResult: ImageResult = ImageResult.Loading
    var attempts = 0

    while (attempts <= maxRetries) {
      lastResult = chain.proceed(chain.request)

      when (lastResult) {
        is ImageResult.Success -> return lastResult
        is ImageResult.Failure -> {
          if (!retryOnError(lastResult.throwable) || attempts >= maxRetries) {
            return lastResult
          }
          attempts++
        }
        is ImageResult.Loading -> return lastResult
      }
    }

    return lastResult
  }
}

/**
 * Internal implementation of the interceptor chain.
 */
internal class RealInterceptorChain(
  private val interceptors: List<Interceptor>,
  private val index: Int,
  override val request: ImageRequest,
  private val coreLoader: suspend (ImageRequest) -> ImageResult,
) : Interceptor.Chain {

  override suspend fun proceed(request: ImageRequest): ImageResult {
    return if (index < interceptors.size) {
      val next = RealInterceptorChain(interceptors, index + 1, request, coreLoader)
      interceptors[index].intercept(next)
    } else {
      coreLoader(request)
    }
  }
}
