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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The image can be drawn on the container node, so these pin the layout callers still see. */
@OptIn(ExperimentalTestApi::class)
class LandscapistImageLayoutTest {

  private val url = "https://example.com/photo.png"
  private val imageWidth = 80
  private val imageHeight = 40

  private inner class StubFetcher : ImageFetcher {
    override fun canHandle(model: Any?): Boolean = true
    override suspend fun fetch(request: ImageRequest): FetchResult =
      FetchResult.Success(data = byteArrayOf(1, 2, 3, 4), mimeType = "image/png")
  }

  private inner class StubDecoder : ImageDecoder {
    override suspend fun decode(
      data: ByteArray,
      mimeType: String?,
      targetWidth: Int?,
      targetHeight: Int?,
      config: LandscapistConfig,
    ): DecodeResult = DecodeResult.Success(
      bitmap = ImageBitmap(imageWidth, imageHeight),
      width = imageWidth,
      height = imageHeight,
    )
  }

  private fun warmLoader(): Landscapist {
    val loader = Landscapist.builder().noDiskCache().fetcher(
      StubFetcher(),
    ).decoder(StubDecoder()).build()
    runBlocking {
      loader.load(
        ImageRequest.builder().model(url).diskCachePolicy(CachePolicy.DISABLED).build(),
      ).first { it is ImageResult.Success }
    }
    return loader
  }

  /** The size [LandscapistImage] reports to its parent. */
  private fun measuredSize(content: @Composable (Modifier) -> Unit): IntSize {
    var size: IntSize? = null
    runComposeUiTest {
      setContent {
        Box {
          content(Modifier.onGloballyPositioned { size = it.size })
        }
      }
    }
    return assertNotNull(size, "the composable never laid out")
  }

  @Test
  fun `a size modifier is honoured`() {
    val loader = warmLoader()

    val size = measuredSize { probe ->
      LandscapistImage(
        imageModel = { url },
        landscapist = loader,
        modifier = Modifier.size(120.dp).then(probe),
        requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
      )
    }

    assertEquals(120, size.width)
    assertEquals(120, size.height)
  }

  /** Lays the image out inside a fixed 200x200 parent and reports the size it took. */
  private fun sizeInside200Box(
    imageModifier: Modifier = Modifier,
    options: ImageOptions = ImageOptions(),
  ): IntSize {
    val loader = warmLoader()
    var size: IntSize? = null
    runComposeUiTest {
      setContent {
        Box(Modifier.size(200.dp)) {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            modifier = imageModifier.onGloballyPositioned { size = it.size },
            imageOptions = options,
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
    return assertNotNull(size, "the composable never laid out")
  }

  @Test
  fun `with no size modifier the image fills its parent`() {
    // The painter's intrinsic size must not reach the layout: 80x40 still fills a 200x200 parent.
    assertEquals(IntSize(200, 200), sizeInside200Box())
  }

  @Test
  fun `content scale does not change the size taken`() {
    for (scale in listOf(ContentScale.Crop, ContentScale.None, ContentScale.Inside)) {
      assertEquals(
        IntSize(200, 200),
        sizeInside200Box(options = ImageOptions(contentScale = scale)),
        "wrong size for $scale",
      )
    }
  }

  @Test
  fun `fillMaxWidth alone still fills the parent`() {
    assertEquals(IntSize(200, 200), sizeInside200Box(imageModifier = Modifier.fillMaxWidth()))
  }

  @Test
  fun `an unbounded height follows the image aspect ratio`() {
    val loader = warmLoader()
    var size: IntSize? = null
    runComposeUiTest {
      setContent {
        Column(Modifier.size(200.dp).verticalScroll(rememberScrollState())) {
          LandscapistImage(
            imageModel = { url },
            landscapist = loader,
            modifier = Modifier.fillMaxWidth().onGloballyPositioned { size = it.size },
            requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          )
        }
      }
    }
    // 80x40 at 200 wide is 200x100, which is what a child Image would have measured.
    assertEquals(IntSize(200, 100), assertNotNull(size))
  }

  @Test
  fun `a success slot still receives the painter`() {
    val loader = warmLoader()
    var painter: Painter? = null

    runComposeUiTest {
      setContent {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.size(120.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          success = { _, loaded -> painter = loaded },
        )
      }
    }

    assertNotNull(painter, "the success slot was skipped")
  }

  @Test
  fun `the state callback still fires with a success`() {
    val loader = warmLoader()
    val states = mutableListOf<LandscapistImageState>()

    runComposeUiTest {
      setContent {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.size(120.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = { states += it },
        )
      }
    }

    assertTrue(
      states.any { it is LandscapistImageState.Success },
      "no success reached onImageStateChanged, saw $states",
    )
    assertTrue(
      states.none { it is LandscapistImageState.None },
      "None has never been surfaced to callers, saw $states",
    )
  }

  @Test
  fun `a crossfade plugin still gets to compose the content`() {
    val loader = warmLoader()
    var painter: Painter? = null

    runComposeUiTest {
      setContent {
        LandscapistImage(
          imageModel = { url },
          landscapist = loader,
          modifier = Modifier.size(120.dp),
          component = rememberImageComponent { +CrossfadePlugin(duration = 200) },
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          success = { _, loaded -> painter = loaded },
        )
      }
    }

    assertNotNull(painter, "the crossfade path dropped the content")
  }

  @Test
  fun `a changed model loads the new image`() {
    // The node runs the load itself, so it has to notice the request it was rebuilt with.
    val loader = Landscapist.builder()
      .noDiskCache()
      .fetcher(
        object : ImageFetcher {
          override fun canHandle(model: Any?): Boolean = true
          override suspend fun fetch(request: ImageRequest): FetchResult {
            val digit = request.model.toString().substringAfterLast('-')[0].digitToInt()
            return FetchResult.Success(byteArrayOf(digit.toByte()), mimeType = "image/png")
          }
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
          ): DecodeResult {
            val side = data[0].toInt() * 10
            return DecodeResult.Success(ImageBitmap(side, side), side, side)
          }
        },
      )
      .build()
    val first = "https://example.com/photo-1.png"
    val second = "https://example.com/photo-2.png"
    runBlocking {
      for (model in listOf(first, second)) {
        loader.load(
          ImageRequest.builder().model(model).diskCachePolicy(CachePolicy.DISABLED).build(),
        ).first { it is ImageResult.Success }
      }
    }

    val widths = mutableListOf<Int>()
    runComposeUiTest {
      var model by mutableStateOf(first)
      setContent {
        LandscapistImage(
          imageModel = { model },
          landscapist = loader,
          modifier = Modifier.size(120.dp),
          requestBuilder = { diskCachePolicy(CachePolicy.DISABLED) },
          onImageStateChanged = {
            if (it is LandscapistImageState.Success) widths += it.originalWidth
          },
        )
      }
      waitForIdle()
      model = second
      waitForIdle()
    }

    assertTrue(widths.contains(10), "the first model never loaded, saw $widths")
    assertTrue(widths.contains(20), "the second model never loaded, saw $widths")
  }
}
