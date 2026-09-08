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
package com.github.skydoves.landscapistdemo.harness

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class HarnessSmokeTest {

  private lateinit var server: LocalImageServer

  @Before fun start() { server = LocalImageServer() }

  @After fun stop() { server.close() }

  private fun loader(): Landscapist = Landscapist.builder().noDiskCache().build()

  private fun load(loader: Landscapist, url: String, width: Int?, height: Int?): ImageResult =
    runBlocking {
      loader.load(
        ImageRequest.builder()
          .model(url)
          .diskCachePolicy(CachePolicy.DISABLED)
          .apply { if (width != null && height != null) size(width, height) }
          .build(),
      ).first { it is ImageResult.Success || it is ImageResult.Failure }
    }

  @Test
  fun realBytesOverRealHttpDecodeToABitmap() {
    server.serve("/photo.jpg", ImageFixtures.photo(400, 300))

    val result = load(loader(), server.url("/photo.jpg"), 200, 150)

    assertTrue("load failed: $result", result is ImageResult.Success)
    val success = result as ImageResult.Success
    assertTrue("decoded at ${success.originalWidth}px", success.originalWidth in 1..200)
    assertEquals(1, server.hitCount("/photo.jpg"))
  }

  @Test
  fun aCookieNoParserAcceptsDoesNotTakeTheImageDown() {
    // The malformed cookie unsplash.com sends.
    server.serve(
      "/cookie.jpg",
      ImageFixtures.photo(120, 120),
      headers = listOf("Set-Cookie: arpa_context=%7B%7D; path=/; max-age=7.0; HttpOnly; secure"),
    )

    val result = load(loader(), server.url("/cookie.jpg"), 60, 60)

    assertTrue("a malformed cookie failed the image: $result", result is ImageResult.Success)
  }

  @Test
  fun aRedirectIsFollowedAndItsCookieIsCarried() {
    server.redirect(
      "/start.jpg",
      server.url("/target.jpg"),
      headers = listOf("Set-Cookie: session=granted; Path=/"),
    )
    server.serve("/target.jpg", ImageFixtures.photo(80, 80))

    val result = load(loader(), server.url("/start.jpg"), 40, 40)

    assertTrue("the redirect did not resolve: $result", result is ImageResult.Success)
    val target = server.requests.last { it.path == "/target.jpg" }
    assertTrue(
      "the cookie was not carried to the redirect target, headers were ${target.headers}",
      target.header("Cookie")?.contains("session=granted") == true,
    )
  }

  @Test
  fun bytesNoDecoderCanReadFailRatherThanHang() {
    server.serve("/garbage.jpg", ImageFixtures.garbage())

    val result = load(loader(), server.url("/garbage.jpg"), 40, 40)

    assertTrue("undecodable bytes did not fail: $result", result is ImageResult.Failure)
  }
}
