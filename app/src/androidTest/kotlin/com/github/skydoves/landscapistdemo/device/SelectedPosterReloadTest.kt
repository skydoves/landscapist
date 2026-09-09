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
package com.github.skydoves.landscapistdemo.device

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.skydoves.landscapistdemo.harness.ImageFixtures
import com.github.skydoves.landscapistdemo.harness.LocalImageServer
import com.kmpalette.palette.graphics.Palette
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.ImageComponent
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.core.builder
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import com.skydoves.landscapist.image.LandscapistImageState
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.palette.rememberPaletteState
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import com.skydoves.landscapist.zoomable.rememberZoomableState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * The demo app's detail screen, which reports loading twice on a cold image: the shimmer flashes
 * again and the reveal runs a second time.
 *
 * Its shape is what makes it worth pinning. A palette plugin hands a colour back to the screen, the
 * screen keeps it in state and reads it, so the arrival of the image recomposes the composable that
 * built the plugins. Everything below counts what that recomposition costs.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SelectedPosterReloadTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  private val components = mutableListOf<ImageComponent>()
  private val successes = AtomicInteger()
  private val reveals = AtomicInteger()
  private val palettes = AtomicInteger()
  private val states = java.util.Collections.synchronizedList(mutableListOf<String>())

  @Before
  fun start() {
    server = LocalImageServer()
    server.serve(
      "/poster.png",
      ImageFixtures.solid(480, 640, android.graphics.Color.RED, Bitmap.CompressFormat.PNG),
      delayMs = 250,
    )
  }

  @After
  fun stop() = server.close()

  /** The demo screen: a scrolling column, a row of thumbnails of the same posters, the poster. */
  /** Bumped after the image settles, so the screen composes again for a reason of its own. */
  private val tick = androidx.compose.runtime.mutableIntStateOf(0)

  @Composable
  private fun Screen(url: String, landscapist: Landscapist, plugins: Set<String>) {
    var palette by rememberPaletteState()
    Column(Modifier.verticalScroll(rememberScrollState())) {
      // The same url again at 50dp, which is what the demo's poster strip does.
      LazyRow {
        items(4) {
          LandscapistImage(
            imageModel = { url },
            landscapist = landscapist,
            modifier = Modifier.size(50.dp),
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
            component = rememberImageComponent { +CrossfadePlugin() },
          )
        }
      }

      SelectedPoster(url, landscapist, plugins) { palette = it }

      // Real content, so the column actually grows when the palette lands.
      palette?.let {
        Column {
          repeat(6) { Text("swatch $it", modifier = Modifier.padding(8.dp)) }
        }
      }
    }
  }

  @Composable
  private fun SelectedPoster(
    url: String,
    landscapist: Landscapist,
    plugins: Set<String>,
    onPaletteUpdated: (Palette) -> Unit,
  ) {
    // Read here rather than in the screen above: this composable is skippable and takes nothing
    // that changes, so a tick outside it would be skipped and nothing about rebuilding measured.
    val unrelated = tick.intValue
    check(unrelated >= 0)
    val zoomableState = rememberZoomableState(
      config = ZoomableConfig(enableSubSampling = true, maxZoom = 40f, doubleTapZoom = 20f),
      resetKey = url,
    )
    val component = rememberImageComponent {
      if ("shimmer" in plugins) {
        +ShimmerPlugin(
          Shimmer.Resonate(baseColor = Color.White, highlightColor = Color.LightGray),
        )
      }
      if ("zoomable" in plugins) +ZoomablePlugin(state = zoomableState)
      if ("palette" in plugins) {
        +PalettePlugin {
          palettes.incrementAndGet()
          onPaletteUpdated.invoke(it)
        }
      }
      if ("reveal" in plugins) {
        +CircularRevealPlugin(onFinishListener = { reveals.incrementAndGet() })
      }
    }
    components += component
    LandscapistImage(
      imageModel = { url },
      landscapist = landscapist,
      modifier = Modifier.aspectRatio(0.75f),
      component = component,
      onImageStateChanged = {
        states += when (it) {
          is LandscapistImageState.Success -> "Success(${sizeOf(it.data)})"
          else -> it::class.simpleName.orEmpty()
        }
        if (it is LandscapistImageState.Success) successes.incrementAndGet()
      },
    )
  }

  /** The pixel size of whatever a Success carried, so a thumbnail standing in is visible. */
  private fun sizeOf(data: Any?): String = when (data) {
    is android.graphics.Bitmap -> "${data.width}x${data.height}"
    is androidx.compose.ui.graphics.ImageBitmap -> "${data.width}x${data.height}"
    is android.graphics.drawable.BitmapDrawable ->
      "${data.bitmap.width}x${data.bitmap.height}"
    else -> data?.let { it::class.simpleName }.orEmpty()
  }

  /** Runs the screen with [plugins] installed and reports what the load cost. */
  private fun run(plugins: Set<String>): String {
    components.clear()
    successes.set(0)
    reveals.set(0)
    palettes.set(0)
    states.clear()
    val url = server.url("/poster.png")
    val context =
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    val landscapist = Landscapist.Companion.builder(context).build()
    kotlinx.coroutines.runBlocking { landscapist.clearCaches() }

    tick.intValue = 0
    compose.setContent { Screen(url, landscapist, plugins) }
    compose.waitUntil(10_000) { successes.get() > 0 }
    compose.waitForIdle()
    // A recomposition the plugins had no part in, which is what a palette landing or a sibling
    // changing does on the real screen.
    compose.runOnUiThread { tick.intValue = 1 }
    compose.waitForIdle()

    return "components=${components.size} distinct=${components.distinct().size} " +
      "successes=${successes.get()} reveals=${reveals.get()} palettes=${palettes.get()} " +
      "fetches=${server.hitCount("/poster.png")} states=${states.toList()}"
  }

  @Test
  fun shimmerAlone() = report("shimmer", setOf("shimmer"))

  @Test
  fun shimmerAndZoomable() = report("shimmer+zoomable", setOf("shimmer", "zoomable"))

  @Test
  fun shimmerZoomableAndPalette() =
    report("shimmer+zoomable+palette", setOf("shimmer", "zoomable", "palette"))

  @Test
  fun everyPluginTheDemoScreenInstalls() =
    report("all four", setOf("shimmer", "zoomable", "palette", "reveal"))

  @Test
  fun paletteAlone() = report("palette", setOf("palette"))

  @Test
  fun revealAlone() = report("reveal", setOf("reveal"))

  private fun report(label: String, plugins: Set<String>) {
    val line = run(plugins)
    println("SELECTEDPOSTER $label -> $line")
    // Without this the check below is a tautology: one composition gives one component, and one
    // component is always one distinct component however the plugins compare.
    assertTrue(
      "$label: the screen composed once, so nothing about rebuilding was measured [$line]",
      components.size > 1,
    )
    assertEquals(
      "$label: the component was rebuilt, so the image restarted [$line]",
      1,
      components.distinct().size,
    )
    // The strip and the poster ask for one url at two sizes. Two decodes, one download.
    assertEquals(
      "$label: the same url was downloaded once per size [$line]",
      1,
      server.hitCount("/poster.png"),
    )
    assertEquals("$label: the image resolved more than once [$line]", 1, successes.get())
    if ("reveal" in plugins) {
      assertEquals("$label: the reveal was reported more than once [$line]", 1, reveals.get())
    }
    if ("palette" in plugins) {
      assertEquals("$label: the palette was reported more than once [$line]", 1, palettes.get())
    }
  }
}
