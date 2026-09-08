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
package com.skydoves.landscapist.core.decoder

import com.skydoves.landscapist.core.LandscapistConfig
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLClassLoader
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Skia and the ImageIO fallback have to agree on the type, size and pixels they produce. */
class DesktopDecoderTest {

  private val config = LandscapistConfig()
  private val decoder = DesktopImageDecoder()

  private fun encode(
    format: String,
    width: Int,
    height: Int,
    type: Int = BufferedImage.TYPE_INT_RGB,
    paint: (Int, Int) -> Int,
  ): ByteArray {
    val image = BufferedImage(width, height, type)
    for (y in 0 until height) {
      for (x in 0 until width) {
        image.setRGB(x, y, paint(x, y))
      }
    }
    return ByteArrayOutputStream().use { out ->
      ImageIO.write(image, format, out)
      out.toByteArray()
    }
  }

  private fun solidPng(width: Int, height: Int, argb: Int): ByteArray =
    encode("png", width, height, BufferedImage.TYPE_INT_ARGB) { _, _ -> argb }

  /** Smooth enough that a JPEG round trip stays close to the colour it started from. */
  private fun gradientJpeg(width: Int, height: Int): ByteArray =
    encode("jpg", width, height) { x, y ->
      val r = 40 + (x * 180 / width)
      val g = 40 + (y * 180 / height)
      (r shl 16) or (g shl 8) or 0x40
    }

  private suspend fun decode(
    data: ByteArray,
    targetWidth: Int?,
    targetHeight: Int?,
  ): DecodeResult.Success = assertIs(
    decoder.decode(data, null, targetWidth, targetHeight, config),
    "the decoder failed on bytes it should be able to read",
  )

  @Test
  fun `skia really is the path being taken, so these tests mean something`() {
    assertTrue(SkiaJvmDecoder.isUsable(), "skiko's native library did not load")
    assertNotNull(
      SkiaJvmDecoder.decode(solidPng(4, 2, 0xFFFF0000.toInt()), 4, 2, config.maxBitmapSize),
      "skiko is not on the test classpath, so the Skia decode path is never exercised",
    )
  }

  @Test
  fun `a consumer with no skiko gets false from the probe rather than an Error`() {
    // Naming the Skia helper without skiko fails with an Error, so the probe must catch Throwable.
    val withoutSkiko = URLClassLoader(
      System.getProperty("java.class.path")
        .split(File.pathSeparator)
        .filterNot { it.contains("skiko") }
        .map { File(it).toURI().toURL() }
        .toTypedArray(),
      ClassLoader.getPlatformClassLoader(),
    )
    withoutSkiko.use { loader ->
      val hazard = assertFails {
        loader.loadClass("com.skydoves.landscapist.core.decoder.SkiaJvmDecoder")
          .getField("INSTANCE").get(null)
      }
      assertIs<Error>(hazard, "the failure this guards against is not an Error after all")

      val fileClass = loader.loadClass(
        "com.skydoves.landscapist.core.decoder.ImageDecoder_desktopKt",
      )
      val probe = fileClass.declaredMethods.single { it.name.startsWith("getSkiaAvailable") }
      probe.isAccessible = true
      assertEquals(false, probe.invoke(null), "the probe should answer, not throw")
    }
  }

  @Test
  fun `a decoded image is still a BufferedImage`() = runTest {
    val result = decode(solidPng(4, 2, 0xFFFF0000.toInt()), 4, 2)
    val image = assertIs<BufferedImage>(result.bitmap)
    assertEquals(4, image.width)
    assertEquals(2, image.height)
    assertEquals(4, result.width)
    assertEquals(2, result.height)
  }

  @Test
  fun `colour survives the round trip, alpha included`() = runTest {
    val translucentGreen = 0x8000FF00.toInt()
    val image = assertIs<BufferedImage>(decode(solidPng(4, 4, translucentGreen), 4, 4).bitmap)
    for (y in 0 until 4) {
      for (x in 0 until 4) {
        val pixel = image.getRGB(x, y)
        // Premultiplying to draw and unpremultiplying to read back costs a little precision.
        assertTrue(
          abs((pixel ushr 24) - 0x80) <= 1 &&
            abs((pixel ushr 16 and 0xFF) - 0x00) <= 1 &&
            abs((pixel ushr 8 and 0xFF) - 0xFF) <= 1 &&
            abs((pixel and 0xFF) - 0x00) <= 1,
          "pixel ($x, $y) came back as ${pixel.toUInt().toString(16)}",
        )
      }
    }
  }

  @Test
  fun `an image smaller than the request is left alone`() = runTest {
    val result = decode(solidPng(8, 6, 0xFF112233.toInt()), 400, 300)
    assertEquals(8, result.width)
    assertEquals(6, result.height)
  }

  @Test
  fun `a downscale keeps the shape of the source`() = runTest {
    val result = decode(gradientJpeg(800, 400), 200, 200)
    assertEquals(200, result.width)
    assertEquals(100, result.height)
  }

  @Test
  fun `the sampled jpeg read lands on exactly the size that was asked for`() = runTest {
    // 1600x1200 down to 400x300 is one eighth, which is a size libjpeg can scale to directly.
    val result = decode(gradientJpeg(1600, 1200), 400, 300)
    assertEquals(400, result.width)
    assertEquals(300, result.height)
  }

  @Test
  fun `a jpeg size that is not an eighth still comes back exact`() = runTest {
    val result = decode(gradientJpeg(1600, 1200), 333, 250)
    assertEquals(333, result.width)
    assertEquals(249, result.height)
  }

  @Test
  fun `both paths agree on size and on colour`() = runTest {
    val jpeg = gradientJpeg(1600, 1200)
    val viaSkia = assertNotNull(SkiaJvmDecoder.decode(jpeg, 400, 300, config.maxBitmapSize))
    val viaImageIo = assertIs<DecodeResult.Success>(
      ImageIoOnlyDecoder().decode(jpeg, null, 400, 300, config),
    )

    assertEquals(viaImageIo.width, viaSkia.width)
    assertEquals(viaImageIo.height, viaSkia.height)

    val skia = viaSkia.bitmap as BufferedImage
    val imageIo = viaImageIo.bitmap as BufferedImage
    // Two different resamplers over the same gradient, so they are close rather than identical.
    var worst = 0
    for (y in 0 until skia.height step 17) {
      for (x in 0 until skia.width step 17) {
        val a = skia.getRGB(x, y)
        val b = imageIo.getRGB(x, y)
        for (shift in intArrayOf(16, 8, 0)) {
          worst = maxOf(worst, abs((a ushr shift and 0xFF) - (b ushr shift and 0xFF)))
        }
      }
    }
    assertTrue(worst <= 12, "the two decode paths disagree by $worst on a smooth gradient")
  }

  @Test
  fun `bytes that are not an image fail rather than throw`() = runTest {
    val result = decoder.decode(ByteArray(64) { it.toByte() }, null, 100, 100, config)
    assertIs<DecodeResult.Error>(result)
  }

  @Test
  fun `an empty payload fails rather than throw`() = runTest {
    assertIs<DecodeResult.Error>(decoder.decode(ByteArray(0), null, 100, 100, config))
  }

  /** The ImageIO fallback on its own, so the two paths can be compared directly. */
  private class ImageIoOnlyDecoder : ImageDecoder {
    private val delegate = DesktopImageDecoder()
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      val method = DesktopImageDecoder::class.java.getDeclaredMethod(
        "decodeSubsampled",
        ByteArray::class.java,
        Integer::class.java,
        Integer::class.java,
        LandscapistConfig::class.java,
      )
      method.isAccessible = true
      return method.invoke(delegate, data, targetWidth, targetHeight, config) as DecodeResult
    }
  }
}
