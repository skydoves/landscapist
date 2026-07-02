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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class KtorImageFetcherTest {

  /**
   * Fetches on a real dispatcher so Ktor's [io.ktor.client.plugins.HttpTimeout] uses wall-clock
   * time. Under runTest's virtual clock the timeout delay would otherwise be advanced and fire
   * before the (instant) mock response arrives.
   */
  private suspend fun fetchImage(
    engine: MockEngine,
    config: NetworkConfig,
    url: String,
  ): FetchResult = withContext(Dispatchers.Default) {
    val client = HttpClient(engine) { configureForImageLoading(config) }
    KtorImageFetcher(client, config).fetch(ImageRequest(model = url))
  }

  /**
   * Regression test for https://github.com/skydoves/landscapist/issues/859.
   *
   * The endpoint only serves the image once a cookie set by its own redirect is present. Without a
   * cookie store the redirect target keeps redirecting, exhausting the send-count limit and failing
   * with SendCountExceedException. Installing [io.ktor.client.plugins.cookies.HttpCookies] retains
   * the cookie across the redirect hop, matching browser behavior, so the image resolves.
   */
  @Test
  fun cookieGatedRedirectResolvesInsteadOfLooping() = runTest {
    val imageBytes = byteArrayOf(0x1, 0x2, 0x3, 0x4)
    val engine = MockEngine { request ->
      if (request.headers[HttpHeaders.Cookie]?.contains("session=granted") == true) {
        respond(
          content = imageBytes,
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, "image/png"),
        )
      } else {
        respond(
          content = "",
          status = HttpStatusCode.Found,
          headers = headersOf(
            HttpHeaders.Location to listOf("https://cdn.example.com/image.png"),
            HttpHeaders.SetCookie to listOf("session=granted; Path=/"),
          ),
        )
      }
    }

    val result = fetchImage(engine, NetworkConfig(), "https://cdn.example.com/image.png")

    assertTrue(result is FetchResult.Success, "expected success but was $result")
    assertContentEquals(imageBytes, result.data)
  }

  /** A redirect chain no longer than [NetworkConfig.maxRedirects] hops is followed to success. */
  @Test
  fun redirectChainWithinMaxRedirectsSucceeds() = runTest {
    val imageBytes = byteArrayOf(0x5, 0x6)
    // maxRedirects = 2 permits: /1 -> /2 -> /ok (two redirect hops).
    val engine = MockEngine { request ->
      when (request.url.encodedPath) {
        "/1" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://h/2"))
        "/2" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://h/ok"))
        else -> respond(
          content = imageBytes,
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, "image/png"),
        )
      }
    }

    val result = fetchImage(engine, NetworkConfig(maxRedirects = 2), "https://h/1")

    assertTrue(result is FetchResult.Success, "expected success but was $result")
    assertContentEquals(imageBytes, result.data)
  }

  /** A redirect chain longer than [NetworkConfig.maxRedirects] fails instead of looping forever. */
  @Test
  fun redirectChainBeyondMaxRedirectsFails() = runTest {
    // Always redirect: the send-count limit must stop it rather than looping indefinitely.
    val engine = MockEngine {
      respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://h/loop"))
    }

    val result = fetchImage(engine, NetworkConfig(maxRedirects = 2), "https://h/start")

    assertTrue(result is FetchResult.Error, "expected error but was $result")
    assertTrue(
      result.throwable is SendCountExceedException,
      "expected SendCountExceedException but was ${result.throwable}",
    )
  }
}
