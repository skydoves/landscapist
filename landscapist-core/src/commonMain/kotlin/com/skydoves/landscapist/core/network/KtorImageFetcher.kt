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
package com.skydoves.landscapist.core.network

import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.NetworkConfig
import com.skydoves.landscapist.core.model.DataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implementation of [ImageFetcher] using Ktor HTTP client.
 *
 * @property httpClient The Ktor HTTP client to use for requests.
 * @property networkConfig Network configuration settings.
 */
public class KtorImageFetcher(
  private val httpClient: HttpClient,
  private val networkConfig: NetworkConfig,
) : ImageFetcher {

  override suspend fun fetch(request: ImageRequest): FetchResult {
    val url = when (val model = request.model) {
      is String -> model
      null -> return FetchResult.Error(
        IllegalArgumentException("Image model is null"),
      )
      else -> model.toString()
    }

    // Check if it's a network URL
    if (!isNetworkUrl(url)) {
      return FetchResult.Error(
        IllegalArgumentException("Not a network URL: $url"),
      )
    }

    return try {
      val response = httpClient.get(url) {
        headers {
          append(HttpHeaders.UserAgent, networkConfig.userAgent)
          networkConfig.defaultHeaders.forEach { (name, value) ->
            append(name, value)
          }
          request.headers.forEach { (name, value) ->
            append(name, value)
          }
        }
      }

      if (!response.status.isSuccess()) {
        return FetchResult.Error(
          HttpException(response.status.value, response.status.description),
        )
      }

      val bytes = response.bodyAsBytes()
      val contentType = response.contentType()?.toString()

      FetchResult.Success(
        data = bytes,
        mimeType = contentType,
        dataSource = DataSource.NETWORK,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      FetchResult.Error(e)
    }
  }

  override fun canHandle(model: Any?): Boolean {
    val url = when (model) {
      is String -> model
      null -> return false
      else -> model.toString()
    }
    return isNetworkUrl(url)
  }

  private fun isNetworkUrl(url: String): Boolean {
    return url.startsWith("http://", ignoreCase = true) ||
      url.startsWith("https://", ignoreCase = true)
  }

  public companion object {
    /**
     * Creates a new [KtorImageFetcher] with default configuration.
     *
     * @param networkConfig Network configuration settings.
     * @return A new [KtorImageFetcher] instance.
     */
    public fun create(networkConfig: NetworkConfig = NetworkConfig()): KtorImageFetcher {
      val httpClient = HttpClient {
        install(HttpTimeout) {
          connectTimeoutMillis = networkConfig.connectTimeout.inWholeMilliseconds
          requestTimeoutMillis = networkConfig.readTimeout.inWholeMilliseconds
        }
        followRedirects = networkConfig.followRedirects
      }
      return KtorImageFetcher(httpClient, networkConfig)
    }
  }
}

/**
 * Exception for HTTP errors.
 *
 * @property code The HTTP status code.
 * @property statusMessage The HTTP status message.
 */
public class HttpException(
  public val code: Int,
  public val statusMessage: String,
) : Exception("HTTP $code: $statusMessage")
