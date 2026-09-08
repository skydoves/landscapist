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
package com.github.skydoves.landscapistdemo.plugins

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.transformation.blur.BlurTransformationPlugin
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/**
 * [BlurTransformationPlugin], rendered on a device and read back a pixel at a time.
 *
 * A blur cannot be checked against a reference image: the toolkit is native code and the result
 * depends on the decode, so what is asserted here is the one thing a blur is for. Neighbouring
 * pixels of a blurred image are closer together than they were before, further apart at a small
 * radius than at a large one, and an image the plugin quietly left alone has exactly the detail of
 * the one it was made from.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BlurTransformationPluginTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  @Before
  fun start() {
    server = LocalImageServer()
  }

  @After
  fun stop() {
    server.close()
  }

  /** Detail for the blur to remove: flat quadrants, and noise a blur flattens. */
  private fun servePhoto(gate: CountDownLatch? = null): String {
    server.serve("/photo.jpg", ImageFixtures.photo(240, 240), gate = gate)
    return server.url("/photo.jpg")
  }

  @Test
  fun aBlurredImageHasLessDetailThanTheImageItWasMadeFrom() {
    val url = servePhoto()
    val loader = pluginLoader()
    val plainState = StateRecorder()
    val blurredState = StateRecorder()

    // Both images are drawn by the same path, one carrying a painter plugin that hands the painter
    // straight back. An image with no plugin at all is drawn by its own layout node instead, and a
    // difference between the two paths would be read here as a difference the blur made.
    compose.setContent {
      Column {
        PluginImage(
          url = url,
          loader = loader,
          component = component(IdentityPainterPlugin),
          tag = PLAIN_TAG,
          onState = plainState::record,
        )
        PluginImage(
          url = url,
          loader = loader,
          component = component(BlurTransformationPlugin(radius = 20)),
          tag = BLURRED_TAG,
          onState = blurredState::record,
        )
      }
    }

    compose.waitUntil(LOAD_TIMEOUT_MS) { plainState.isSuccess && blurredState.isSuccess }

    val plain = compose.onNodeWithTag(PLAIN_TAG).pixels().localContrast()
    val blurred = compose.onNodeWithTag(BLURRED_TAG).pixels().localContrast()

    assertTrue(
      "the unblurred image has no detail to lose, so nothing below can be shown: $plain",
      plain > 0.01f,
    )
    assertTrue(
      "the blur did not smooth the image: detail was $plain unblurred and $blurred blurred",
      blurred < plain * 0.6f,
    )
  }

  @Test
  fun changingTheRadiusChangesTheBlur() {
    // The blur once kept the first result it produced and ignored every later radius, which on
    // screen is a plugin that appears to work and never responds to being configured.
    val url = servePhoto()
    val loader = pluginLoader()
    val state = StateRecorder()
    var radius by mutableStateOf(1)

    compose.setContent {
      PluginImage(
        url = url,
        loader = loader,
        component = component(BlurTransformationPlugin(radius = radius)),
        onState = state::record,
      )
    }

    compose.waitUntil(LOAD_TIMEOUT_MS) { state.isSuccess }
    val gentle = compose.onNodeWithTag(IMAGE_TAG).pixels().localContrast()

    compose.runOnIdle { radius = 21 }
    compose.waitForIdle()
    val heavy = compose.onNodeWithTag(IMAGE_TAG).pixels().localContrast()

    assertTrue(
      "a radius of 1 already flattened the image, so a larger one cannot be shown to do more: " +
        "$gentle",
      gentle > 0.01f,
    )
    assertTrue(
      "the larger radius did not blur any further, so the first result was kept: detail was " +
        "$gentle at radius 1 and $heavy at radius 21",
      heavy < gentle * 0.6f,
    )
  }

  @Test
  fun aBlurStillRunsWithALoadingPlaceholderInstalled() {
    // Two kinds of plugin at once. Which drawing path an image takes is decided from the plugins
    // installed on it, so a painter plugin that works alone is not yet a painter plugin that works
    // next to one that composes content of its own.
    val gate = CountDownLatch(1)
    val url = servePhoto(gate)
    val loader = pluginLoader()
    val placeholder = ImageFixtures
      .solid(24, 24, Color.Blue.toArgb(), Bitmap.CompressFormat.PNG)
      .decodeToImageBitmap()
    val plainState = StateRecorder()
    val blurredState = StateRecorder()

    compose.setContent {
      Column {
        PluginImage(
          url = url,
          loader = loader,
          component = component(IdentityPainterPlugin),
          tag = PLAIN_TAG,
          onState = plainState::record,
        )
        PluginImage(
          url = url,
          loader = loader,
          component = component(
            BlurTransformationPlugin(radius = 20),
            PlaceholderPlugin.Loading(placeholder),
          ),
          tag = BLURRED_TAG,
          onState = blurredState::record,
        )
      }
    }

    compose.waitForIdle()
    assertColourNear(
      "the placeholder did not render next to a painter plugin.",
      Color.Blue,
      compose.onNodeWithTag(BLURRED_TAG).pixels().centreColour(),
    )
    assertColourNear(
      "the image without a placeholder drew something while the response was held.",
      backdrop,
      compose.onNodeWithTag(PLAIN_TAG).pixels().centreColour(),
    )

    gate.countDown()
    compose.waitUntil(LOAD_TIMEOUT_MS) { plainState.isSuccess && blurredState.isSuccess }

    val plain = compose.onNodeWithTag(PLAIN_TAG).pixels().localContrast()
    val blurred = compose.onNodeWithTag(BLURRED_TAG).pixels().localContrast()

    assertColourFar(
      "the placeholder is still on screen after the image arrived.",
      Color.Blue,
      compose.onNodeWithTag(BLURRED_TAG).pixels().centreColour(),
    )
    assertTrue(
      "the blur stopped running once a loading plugin was installed beside it: detail was " +
        "$plain unblurred and $blurred blurred",
      blurred < plain * 0.6f,
    )
  }

  @Test
  fun aRadiusOfTwentyFourStillRenders() {
    // The plugin splits a radius into passes with `(radius + 1) % 25` and `(radius + 1) / 25`, so
    // a radius of 24 asks the toolkit for a pass of radius 0, which it refuses. 24, 49 and 74 are
    // all inside the documented range and all take the composition down with them.
    val url = servePhoto()
    val loader = pluginLoader()
    val state = StateRecorder()

    compose.setContent {
      PluginImage(
        url = url,
        loader = loader,
        component = component(BlurTransformationPlugin(radius = 24)),
        onState = state::record,
      )
    }

    compose.waitUntil(LOAD_TIMEOUT_MS) { state.isSuccess }

    assertColourFar(
      "a radius of 24 drew nothing.",
      backdrop,
      compose.onNodeWithTag(IMAGE_TAG).pixels().centreColour(),
    )
  }

  private companion object {
    private const val PLAIN_TAG = "plainImage"
    private const val BLURRED_TAG = "blurredImage"
  }
}

/** A painter plugin that changes nothing, so a control is drawn by the path a blur is drawn by. */
private object IdentityPainterPlugin : ImagePlugin.PainterPlugin {

  @Composable
  override fun compose(imageBitmap: ImageBitmap, painter: Painter): Painter = painter
}
