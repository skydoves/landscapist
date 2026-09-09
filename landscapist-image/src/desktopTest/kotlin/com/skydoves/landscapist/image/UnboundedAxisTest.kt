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
package com.skydoves.landscapist.image

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the node measures to on an axis the parent left open.
 *
 * A feed row bounds the width and leaves the height to the image, so the node picks that height
 * itself. Two things it has to get right: the content scale has a say in it, and the answer has to
 * be a height Compose can hold.
 */
@OptIn(ExperimentalTestApi::class)
class UnboundedAxisTest {

  private val url = "https://example.com/photo.png"

  private fun loaderFor(width: Int, height: Int): Landscapist {
    val loader = Landscapist.builder()
      .noDiskCache()
      .fetcher(
        object : ImageFetcher {
          override fun canHandle(model: Any?): Boolean = true
          override suspend fun fetch(request: ImageRequest): FetchResult =
            FetchResult.Success(data = byteArrayOf(1), mimeType = "image/png")
        },
      )
      .decoder(
        object : ImageDecoder {
          override suspend fun decode(
            data: ByteArray,
            mimeType: String?,
            targetWidth: Int?,
            targetHeight: Int?,
            config: LandscapistConfig,
          ): DecodeResult =
            DecodeResult.Success(ImageBitmap(width, height), width, height)
        },
      )
      .build()
    runBlocking {
      loader.load(
        ImageRequest.builder().model(url).diskCachePolicy(CachePolicy.DISABLED).build(),
      ).first { it is ImageResult.Success }
    }
    return loader
  }

  /** Measures the image in a 200 wide column that scrolls, so its height is its own to choose. */
  private fun heightInScrollingColumn(
    imageWidth: Int,
    imageHeight: Int,
    scale: ContentScale,
  ): IntSize {
    val loader = loaderFor(imageWidth, imageHeight)
    var size: IntSize? = null
    runComposeUiTest {
      setContent {
        Column(Modifier.size(200.dp).verticalScroll(rememberScrollState())) {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            modifier = Modifier.fillMaxWidth().onGloballyPositioned { size = it.size },
            imageOptions = ImageOptions(contentScale = scale),
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
    return assertNotNull(size, "the composable never laid out")
  }

  @Test
  fun `a scale that fills the row takes the height the image shape asks for`() {
    for (scale in listOf(ContentScale.Crop, ContentScale.Fit, ContentScale.FillWidth)) {
      assertEquals(IntSize(200, 100), heightInScrollingColumn(80, 40, scale), "wrong for $scale")
    }
  }

  @Test
  fun `a scale that does not upscale takes the height the image already has`() {
    // None draws the image at its own size, so a row sized to the shape of an image it is not
    // going to stretch is 60 pixels of empty space under a 40 pixel image.
    assertEquals(IntSize(200, 40), heightInScrollingColumn(80, 40, ContentScale.None))
    assertEquals(IntSize(200, 40), heightInScrollingColumn(80, 40, ContentScale.Inside))
  }

  @Test
  fun `Inside still shrinks an image too large for the row`() {
    assertEquals(IntSize(200, 100), heightInScrollingColumn(800, 400, ContentScale.Inside))
  }

  @Test
  fun `a hairline tall image does not bring the layout down`() {
    // 200 wide at this shape asks for a height of four million, which Constraints cannot hold.
    val size = heightInScrollingColumn(1, 20_000, ContentScale.Crop)
    assertEquals(200, size.width)
    assertTrue(size.height > 0, "the image was given no height at all")
  }
}
