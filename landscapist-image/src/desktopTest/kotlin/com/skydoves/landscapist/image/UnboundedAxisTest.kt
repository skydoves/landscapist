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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.ImageRequest
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.LandscapistConfig
import com.skydoves.landscapist.core.decoder.DecodeResult
import com.skydoves.landscapist.core.decoder.ImageDecoder
import com.skydoves.landscapist.core.model.CachePolicy
import com.skydoves.landscapist.core.model.ImageResult
import com.skydoves.landscapist.core.network.FetchResult
import com.skydoves.landscapist.core.network.ImageFetcher
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
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

  /**
   * The same slot with a plugin installed, which is the composed path rather than the node.
   *
   * Two ways of resolving the open axis in the same composable have to agree, or installing a
   * shimmer silently changes the size an image takes.
   */
  private fun heightInScrollingColumnWithPlugin(
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
            component = rememberImageComponent { +CrossfadePlugin(duration = 50) },
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
    return assertNotNull(size, "the composable never laid out")
  }

  @Test
  fun `a plugin does not change the height an open axis resolves to`() {
    for (scale in listOf(ContentScale.Crop, ContentScale.Fit, ContentScale.FillWidth)) {
      assertEquals(
        heightInScrollingColumn(80, 40, scale),
        heightInScrollingColumnWithPlugin(80, 40, scale),
        "the two paths disagree for $scale",
      )
    }
  }

  @Test
  fun `a plugin does not change the height a scale that does not upscale resolves to`() {
    for (scale in listOf(ContentScale.None, ContentScale.Inside)) {
      assertEquals(
        heightInScrollingColumn(80, 40, scale),
        heightInScrollingColumnWithPlugin(80, 40, scale),
        "the two paths disagree for $scale",
      )
    }
  }

  /** Measures the image in a 200 tall row that scrolls, so its width is its own to choose. */
  private fun widthInScrollingRow(
    withPlugin: Boolean,
    imageWidth: Int = 80,
    imageHeight: Int = 40,
    scale: ContentScale = ContentScale.Crop,
  ): IntSize {
    val loader = loaderFor(imageWidth, imageHeight)
    var size: IntSize? = null
    runComposeUiTest {
      setContent {
        Row(Modifier.size(200.dp).horizontalScroll(rememberScrollState())) {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            modifier = Modifier.fillMaxHeight().onGloballyPositioned { size = it.size },
            imageOptions = ImageOptions(contentScale = scale),
            component = if (withPlugin) {
              rememberImageComponent { +CrossfadePlugin(duration = 50) }
            } else {
              rememberImageComponent {}
            },
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
    return assertNotNull(size, "the composable never laid out")
  }

  @Test
  fun `an unbounded width follows the image aspect ratio`() {
    // 80x40 at 200 tall is 400x200, which is the mirror of the column case.
    assertEquals(IntSize(400, 200), widthInScrollingRow(withPlugin = false))
  }

  @Test
  fun `an unbounded width follows the image aspect ratio with a plugin`() {
    assertEquals(IntSize(400, 200), widthInScrollingRow(withPlugin = true))
  }

  @Test
  fun `on an open width a scale that does not upscale takes the width the image has`() {
    // The mirror of the column case, and the half nothing covered: with Crop the generic fallback
    // happens to answer the same, so only a scale that refuses to upscale tells the two apart.
    for (scale in listOf(ContentScale.None, ContentScale.Inside)) {
      assertEquals(
        IntSize(80, 200),
        widthInScrollingRow(withPlugin = false, scale = scale),
        "wrong for $scale",
      )
    }
  }

  @Test
  fun `on an open width Inside still shrinks an image too large for the row`() {
    assertEquals(
      IntSize(400, 200),
      widthInScrollingRow(
        withPlugin = false,
        imageWidth = 800,
        imageHeight = 400,
        scale = ContentScale.Inside,
      ),
    )
  }

  @Test
  fun `a hairline wide image does not bring the layout down`() {
    val size = widthInScrollingRow(
      withPlugin = false,
      imageWidth = 20_000,
      imageHeight = 1,
      scale = ContentScale.Crop,
    )
    assertEquals(200, size.height)
    assertTrue(size.width > 0, "the image was given no width at all")
  }

  /**
   * A plugin set that has to compose its own content, so the container is not painting.
   *
   * Shimmer stacks two states, which takes the fade off the container, and with it the
   * [androidx.compose.ui.Modifier.paint] that was resolving the open axis. What is left is the
   * auto aspect ratio, and an axis it does not cover has nothing sizing it at all.
   */
  private fun sizeWithComposedContent(
    modifier: Modifier,
    scrolls: @Composable (@Composable () -> Unit) -> Unit,
  ): IntSize {
    val loader = loaderFor(80, 40)
    var size: IntSize? = null
    runComposeUiTest {
      setContent {
        scrolls {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            modifier = modifier.onGloballyPositioned { size = it.size },
            component = rememberImageComponent {
              +ShimmerPlugin(Shimmer.Fade(baseColor = Color.Gray, highlightColor = Color.White))
              +CrossfadePlugin(duration = 50)
            },
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
    return assertNotNull(size, "the composable never laid out")
  }

  @Test
  fun `an open height resolves when the content is composed rather than painted`() {
    val size = sizeWithComposedContent(Modifier.fillMaxWidth()) { content ->
      Column(Modifier.size(200.dp).verticalScroll(rememberScrollState())) { content() }
    }
    assertEquals(IntSize(200, 100), size)
  }

  @Test
  fun `an open width resolves when the content is composed rather than painted`() {
    val size = sizeWithComposedContent(Modifier.fillMaxHeight()) { content ->
      Row(Modifier.size(200.dp).horizontalScroll(rememberScrollState())) { content() }
    }
    assertEquals(IntSize(400, 200), size)
  }
}
