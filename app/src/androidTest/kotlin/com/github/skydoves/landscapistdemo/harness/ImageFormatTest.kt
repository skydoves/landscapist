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

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.skydoves.landscapist.core.BitmapConfig
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
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

/** Every format the Android decoder branches on, served as real bytes over a real socket. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ImageFormatTest {

  private lateinit var server: LocalImageServer

  @Before fun start() { server = LocalImageServer() }

  @After fun stop() { server.close() }

  private fun loader(config: LandscapistConfig = LandscapistConfig()): Landscapist =
    Landscapist.builder().config(config).noDiskCache().build()

  private fun load(
    loader: Landscapist,
    url: String,
    width: Int? = null,
    height: Int? = null,
  ): ImageResult = runBlocking {
    loader.load(
      ImageRequest.builder()
        .model(url)
        .diskCachePolicy(CachePolicy.DISABLED)
        .apply { if (width != null && height != null) size(width, height) }
        .build(),
    ).first { it is ImageResult.Success || it is ImageResult.Failure }
  }

  private class Still(val name: String, val contentType: String, val bytes: ByteArray) {
    val path: String get() = "/$name"
  }

  /** The lossy file with alpha is the only one libwebp writes a VP8X chunk for. */
  private fun stills(side: Int): List<Still> = listOf(
    Still("jpeg", "image/jpeg", ImageFixtures.photo(side, side)),
    Still("png", "image/png", ImageFixtures.photo(side, side, Bitmap.CompressFormat.PNG)),
    Still("webp-lossy", "image/webp", ImageFixtures.webp(side, side)),
    Still("webp-lossy-alpha", "image/webp", ImageFixtures.webp(side, side, transparent = true)),
    Still("webp-lossless", "image/webp", ImageFixtures.webp(side, side, lossless = true)),
  )

  @Test
  fun everyStillFormatDecodesToTheSizeItDeclares() {
    val loader = loader()

    for (still in stills(SOURCE)) {
      server.serve(still.path, still.bytes, contentType = still.contentType)

      val result = load(loader, server.url(still.path))

      assertTrue("${still.name} did not load: $result", result is ImageResult.Success)
      val success = result as ImageResult.Success
      assertEquals("${still.name} width", SOURCE, success.originalWidth)
      assertEquals("${still.name} height", SOURCE, success.originalHeight)
      // A still format must not come back from the animated branch, which returns a drawable.
      assertTrue("${still.name} decoded to ${success.data.javaClass.name}", success.data is Bitmap)
    }
  }

  @Test
  fun everyStillFormatIsDownsampledToTheRequestedSize() {
    val loader = loader()

    for (still in stills(SOURCE)) {
      server.serve(still.path, still.bytes, contentType = still.contentType)

      val result = load(loader, server.url(still.path), TARGET, TARGET)

      assertTrue("${still.name} did not load: $result", result is ImageResult.Success)
      val success = result as ImageResult.Success
      assertEquals("${still.name} width", TARGET, success.originalWidth)
      assertEquals("${still.name} height", TARGET, success.originalHeight)
    }
  }

  @Test
  fun aWebpKeepsItsAlphaWhereAJpegIsFlattenedToRgb565() {
    // Hardware bitmaps are off so that RGB_565, the format that would drop the alpha, is the one
    // the decoder has to refuse for a webp.
    val loader = loader(
      LandscapistConfig(
        allowRgb565 = true,
        bitmapConfig = BitmapConfig(allowHardware = false),
      ),
    )
    server.serve("/opaque.jpg", ImageFixtures.photo(SOURCE, SOURCE), contentType = "image/jpeg")
    server.serve(
      "/alpha.webp",
      ImageFixtures.webp(SOURCE, SOURCE, lossless = true, transparent = true),
      contentType = "image/webp",
    )

    val control = load(loader, server.url("/opaque.jpg"))
    val result = load(loader, server.url("/alpha.webp"))

    assertTrue("the jpeg did not load: $control", control is ImageResult.Success)
    assertEquals(
      "565 was never reached, so the webp below proves nothing",
      Bitmap.Config.RGB_565,
      ((control as ImageResult.Success).data as Bitmap).config,
    )
    assertTrue("the webp did not load: $result", result is ImageResult.Success)
    val bitmap = (result as ImageResult.Success).data as Bitmap
    assertEquals("a webp must never be decoded as 565", Bitmap.Config.ARGB_8888, bitmap.config)
    assertTrue("the webp lost its alpha channel", bitmap.hasAlpha())
    assertEquals("the transparent quadrant came back opaque", 0, Color.alpha(bitmap.getPixel(0, 0)))
  }

  @Test
  fun anAnimatedGifDecodesToTheAnimatedDrawable() {
    val loader = loader()
    val side = ImageFixtures.ANIMATED_GIF_SIDE
    server.serve("/animated.gif", ImageFixtures.animatedGif(), contentType = "image/gif")

    val result = load(loader, server.url("/animated.gif"))

    assertTrue("the gif did not load: $result", result is ImageResult.Success)
    val success = result as ImageResult.Success
    assertEquals("gif width", side, success.originalWidth)
    assertEquals("gif height", side, success.originalHeight)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      // The animated branch hands back a started drawable rather than a bitmap.
      assertTrue(
        "an animated gif decoded to ${success.data.javaClass.name}",
        success.data is AnimatedImageDrawable,
      )
      assertTrue("the drawable was not started", (success.data as AnimatedImageDrawable).isRunning)
    } else {
      assertTrue(
        "expected a first frame bitmap: ${success.data.javaClass.name}",
        success.data is Bitmap,
      )
    }
  }

  @Test
  fun anAnimatedGifIsDownsampledToTheRequestedSize() {
    val loader = loader()
    val half = ImageFixtures.ANIMATED_GIF_SIDE / 2
    server.serve("/small.gif", ImageFixtures.animatedGif(), contentType = "image/gif")

    val result = load(loader, server.url("/small.gif"), half, half)

    assertTrue("the gif did not load: $result", result is ImageResult.Success)
    val success = result as ImageResult.Success
    assertEquals("gif width", half, success.originalWidth)
    assertEquals("gif height", half, success.originalHeight)
  }

  private companion object {
    /** A source and a target that sample by exactly four, so the decoded size is exact. */
    const val SOURCE = 64
    const val TARGET = 16
  }
}
