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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class, DelicateCoilApi::class)
class CoilImageLoaderProviderTest {

  private val context = PlatformContext.INSTANCE

  @AfterTest
  fun resetSingleton() {
    SingletonImageLoader.reset()
  }

  /**
   * Apps register their loader with `SingletonImageLoader.setSafe { ... }` at startup. `setSafe`
   * only protects an already created loader, so installing landscapist's own factory on the first
   * composition used to replace the app's factory, dropping its components and interceptors.
   */
  @Test
  fun `a loader factory registered by the app is used`() {
    val appLoader = ImageLoader.Builder(context).build()
    SingletonImageLoader.setSafe { appLoader }

    var used: ImageLoader? = null
    runComposeUiTest {
      setContent { used = LocalCoilProvider.getCoilImageLoader() }
    }

    assertSame(appLoader, used)
  }

  @Test
  fun `the local image loader wins over the singleton`() {
    val localLoader = ImageLoader.Builder(context).build()
    SingletonImageLoader.setSafe { ImageLoader.Builder(context).build() }

    var used: ImageLoader? = null
    runComposeUiTest {
      setContent {
        CompositionLocalProvider(LocalCoilImageLoader provides localLoader) {
          used = LocalCoilProvider.getCoilImageLoader()
        }
      }
    }

    assertSame(localLoader, used)
  }

  /** Without any registration, Coil's default singleton still loads network images on its own. */
  @Test
  fun `the default loader fetches network images without extra components`() {
    val png = pngBytes()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
      createContext("/image.png") { exchange ->
        exchange.responseHeaders.add("Content-Type", "image/png")
        exchange.sendResponseHeaders(200, png.size.toLong())
        exchange.responseBody.use { it.write(png) }
      }
      start()
    }
    try {
      var loader: ImageLoader? = null
      runComposeUiTest {
        setContent { loader = LocalCoilProvider.getCoilImageLoader() }
      }
      val url = "http://127.0.0.1:${server.address.port}/image.png"
      val result = runBlocking { loader!!.execute(ImageRequest.Builder(context).data(url).build()) }

      assertIs<SuccessResult>(result)
    } finally {
      server.stop(0)
    }
  }

  private fun pngBytes(): ByteArray {
    val bitmap = Bitmap().apply { allocN32Pixels(8, 8) }
    return Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
  }
}
