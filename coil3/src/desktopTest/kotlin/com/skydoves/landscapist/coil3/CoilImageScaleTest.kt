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

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.size.Scale
import com.skydoves.landscapist.ImageOptions
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class CoilImageScaleTest {

  private fun pngBytes(width: Int, height: Int): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    return ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "png", out)
      out.toByteArray()
    }
  }

  @Test
  fun cropDecodesWithFillScaleInsteadOfFit() = runComposeUiTest {
    val bytes = pngBytes(width = 400, height = 300)
    val imageLoader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
    var decodedWidth = -1
    var decodedHeight = -1

    setContent {
      CoilImage(
        imageModel = { bytes },
        modifier = Modifier.size(132.dp),
        imageLoader = { imageLoader },
        imageOptions = ImageOptions(contentScale = ContentScale.Crop),
        onImageStateChanged = { state ->
          if (state is CoilImageState.Success) {
            decodedWidth = state.image?.width ?: -1
            decodedHeight = state.image?.height ?: -1
          }
        },
      )
    }

    waitUntil(timeoutMillis = 5_000) { decodedWidth > 0 }
    // A 400x300 source in a 132x132 Crop box should decode with Scale.FILL to 176x132,
    // not 132x99 (which Scale.FIT produces).
    assertEquals(176, decodedWidth)
    assertEquals(132, decodedHeight)
  }

  @Test
  fun fitAndInsideMapToScaleFitWhileOtherContentScalesMapToScaleFill() = runComposeUiTest {
    val context = PlatformContext.INSTANCE
    val baseRequest = ImageRequest.Builder(context).data("https://example.com/a.png").build()
    val results = mutableMapOf<ContentScale, Scale>()

    val cases = listOf(
      ContentScale.Fit to Scale.FIT,
      ContentScale.Inside to Scale.FIT,
      ContentScale.Crop to Scale.FILL,
      ContentScale.FillBounds to Scale.FILL,
      ContentScale.FillWidth to Scale.FILL,
      ContentScale.FillHeight to Scale.FILL,
      ContentScale.None to Scale.FILL,
    )

    setContent {
      cases.forEach { (contentScale, _) ->
        val resolved = rememberRequestWithConstraints(
          request = baseRequest,
          imageOptions = ImageOptions(contentScale = contentScale),
        )
        results[contentScale] = resolved.scale
      }
    }

    waitForIdle()
    cases.forEach { (contentScale, expectedScale) ->
      assertEquals(expectedScale, results[contentScale], "Unexpected scale for $contentScale")
    }
  }

  @Test
  fun explicitRequestScaleIsPreserved() = runComposeUiTest {
    val context = PlatformContext.INSTANCE
    val requestWithExplicitScale = ImageRequest.Builder(context)
      .data("https://example.com/a.png")
      .scale(Scale.FIT)
      .build()
    var resolved: ImageRequest? = null

    setContent {
      resolved = rememberRequestWithConstraints(
        request = requestWithExplicitScale,
        imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      )
    }

    waitForIdle()
    val actual = assertNotNull(resolved)
    assertEquals(Scale.FIT, actual.scale)
  }

  @Test
  fun explicitRequestSizeStillMapsUndefinedScaleFromContentScale() = runComposeUiTest {
    val context = PlatformContext.INSTANCE
    val requestWithExplicitSize = ImageRequest.Builder(context)
      .data("https://example.com/a.png")
      .size(132, 132)
      .build()
    var resolved: ImageRequest? = null

    setContent {
      resolved = rememberRequestWithConstraints(
        request = requestWithExplicitSize,
        imageOptions = ImageOptions(contentScale = ContentScale.Crop),
      )
    }

    waitForIdle()
    val actual = assertNotNull(resolved)
    assertEquals(Scale.FILL, actual.scale)
  }
}
