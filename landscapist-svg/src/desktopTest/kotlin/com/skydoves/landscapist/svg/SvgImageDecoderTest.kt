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
package com.skydoves.landscapist.svg

import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * These assert the rendered pixels on purpose. An earlier attempt at this feature produced a
 * painter that drew nothing at all, and a test that only checked the result's type passed anyway.
 */
class SvgImageDecoderTest {

  /** A solid purple rectangle filling a 2:1 view box. */
  private val solidSvg = """
    <?xml version="1.0" encoding="UTF-8"?>
    <svg xmlns="http://www.w3.org/2000/svg" width="48" height="24" viewBox="0 0 48 24">
      <rect width="48" height="24" fill="#6750A4"/>
    </svg>
  """.trimIndent()

  /** The same shape drawn as a path, the form that broke the previous implementation. */
  private val pathSvg = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
      <path d="M0 0 H10 V10 H0 Z" fill="#FF0000"/>
    </svg>
  """.trimIndent()

  private class RecordingDecoder : ImageDecoder {
    var calls: Int = 0
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult {
      calls++
      return DecodeResult.Success(bitmap = "raster", width = 1, height = 1)
    }
  }

  private val delegate = RecordingDecoder()
  private val decoder = SvgImageDecoder(delegate)

  private suspend fun decode(
    markup: String,
    mimeType: String? = null,
    targetWidth: Int? = 200,
    targetHeight: Int? = 100,
  ) = decoder.decode(
    data = markup.encodeToByteArray(),
    mimeType = mimeType,
    targetWidth = targetWidth,
    targetHeight = targetHeight,
    config = LandscapistConfig(),
  )

  private fun DecodeResult.bitmap(): Bitmap {
    return assertIs<Bitmap>(assertIs<DecodeResult.Success>(this).bitmap)
  }

  @Test
  fun `a rect renders its fill colour at the requested size`() = runTest {
    val result = decode(solidSvg)

    val bitmap = result.bitmap()
    assertEquals(200, bitmap.width)
    assertEquals(100, bitmap.height)
    // 0xFF6750A4, read back through skia's ARGB accessor.
    assertEquals(0xFF6750A4.toInt(), bitmap.getColor(100, 50))
  }

  @Test
  fun `a path renders too`() = runTest {
    val bitmap = decode(pathSvg, targetWidth = 40, targetHeight = 40).bitmap()

    assertEquals(0xFFFF0000.toInt(), bitmap.getColor(20, 20))
  }

  @Test
  fun `the reported size matches the bitmap`() = runTest {
    val success = assertIs<DecodeResult.Success>(decode(solidSvg))

    assertEquals(200, success.width)
    assertEquals(100, success.height)
    assertEquals(0, delegate.calls, "the delegate must not see svg markup")
  }

  @Test
  fun `an unmeasured request falls back to the declared size`() = runTest {
    val bitmap = decode(solidSvg, targetWidth = null, targetHeight = null).bitmap()

    assertEquals(48, bitmap.width)
    assertEquals(24, bitmap.height)
  }

  @Test
  fun `a correct content type is honoured`() = runTest {
    val bitmap = decode(solidSvg, mimeType = "image/svg+xml").bitmap()

    assertEquals(0xFF6750A4.toInt(), bitmap.getColor(100, 50))
  }

  @Test
  fun `broken markup is a decode error rather than an empty success`() = runTest {
    val result = decode("<svg><rect width=</svg>")

    assertIs<DecodeResult.Error>(result)
    assertEquals(0, delegate.calls)
  }

  @Test
  fun `everything else goes to the delegate`() = runTest {
    val result = assertIs<DecodeResult.Success>(decode("not markup at all", mimeType = "image/png"))

    assertEquals("raster", result.bitmap)
    assertEquals(1, delegate.calls)
  }

  @Test
  fun `a huge request is clamped to the configured maximum`() = runTest {
    val result = decoder.decode(
      data = solidSvg.encodeToByteArray(),
      mimeType = null,
      targetWidth = 100_000,
      targetHeight = 100_000,
      config = LandscapistConfig(maxBitmapSize = 256),
    )

    val bitmap = result.bitmap()
    assertTrue(bitmap.width <= 256 && bitmap.height <= 256, "was ${bitmap.width}x${bitmap.height}")
  }
}
