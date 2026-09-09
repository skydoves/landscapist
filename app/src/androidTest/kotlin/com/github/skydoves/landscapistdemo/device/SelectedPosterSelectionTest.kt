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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * What the demo's detail image does when a different poster is selected, which is the moment the
 * flash is reported and the moment [SelectedPosterReloadTest] never reaches: that one holds a
 * single url for the life of the composition, so nothing there ever changes the model.
 *
 * The screen is the demo's: a scrolling column, a strip of 50dp thumbnails of the same urls, the
 * detail image at [Modifier.aspectRatio] 0.75 carrying the four plugins, then the palette driven
 * content underneath. Selecting writes the url the way the demo's tap does.
 *
 * Measured before the fix, both by writing the state and by tapping the thumbnail:
 *
 * ```
 * A| detail Loading
 * A| detail Success #1 pixels=960x1280 source=NETWORK
 * A| thumb a.png Success 240x320
 * A| thumb b.png Success 240x320
 * B| detail Success #2 pixels=240x320 source=MEMORY   <- the strip's thumbnail, drawn at 1080x1440
 * B| detail Success #3 pixels=960x1280 source=DISK    <- the real image, a second decode
 * ```
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SelectedPosterSelectionTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var server: LocalImageServer

  /** Everything that happened to the detail image, in the order it happened. */
  private val timeline = Collections.synchronizedList(mutableListOf<String>())
  private val phase = AtomicReference("A")
  private val startedAt = System.currentTimeMillis()

  private val components = Collections.synchronizedList(mutableListOf<ImageComponent>())
  private val detailSuccesses = AtomicInteger()
  private val reveals = AtomicInteger()
  private val palettes = AtomicInteger()

  private fun log(line: String) {
    timeline += "${phase.get()}|${System.currentTimeMillis() - startedAt}ms| $line"
  }

  @Before
  fun start() {
    server = LocalImageServer()
    // 960x1280, so what a 50dp slot decodes cannot be confused with what the detail slot decodes.
    server.serve("/a.png", poster(android.graphics.Color.RED), delayMs = 150)
    server.serve("/b.png", poster(android.graphics.Color.GREEN), delayMs = 150)
  }

  private fun poster(color: Int): ByteArray =
    ImageFixtures.solid(960, 1280, color, Bitmap.CompressFormat.PNG)

  @After
  fun stop() = server.close()

  @Composable
  private fun Screen(
    selected: MutableState<String>,
    urls: List<String>,
    landscapist: Landscapist,
  ) {
    var palette by rememberPaletteState()
    Column(Modifier.verticalScroll(rememberScrollState())) {
      LazyRow {
        items(urls) { url ->
          Card(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
            LandscapistImage(
              imageModel = { url },
              landscapist = landscapist,
              modifier = Modifier
                .size(50.dp)
                .testTag("thumb:${url.substringAfterLast('/')}")
                .clickable { selected.value = url },
              imageOptions = ImageOptions(contentScale = ContentScale.Crop),
              component = rememberImageComponent { +CrossfadePlugin() },
              onImageStateChanged = {
                if (it is LandscapistImageState.Success) {
                  log("thumb ${url.substringAfterLast('/')} Success ${sizeOf(it.data)}")
                }
              },
            )
          }
        }
      }

      SelectedPoster(selected.value, landscapist) { palette = it }

      // The demo composes real content under the image once a palette lands.
      palette?.let {
        Column {
          repeat(6) { i -> Text("swatch $i", modifier = Modifier.padding(8.dp)) }
        }
      }
    }
  }

  @Composable
  private fun SelectedPoster(
    url: String,
    landscapist: Landscapist,
    onPaletteUpdated: (Palette) -> Unit,
  ) {
    val zoomableState = rememberZoomableState(
      config = ZoomableConfig(enableSubSampling = true, maxZoom = 40f, doubleTapZoom = 20f),
      resetKey = url,
    )
    val component = rememberImageComponent {
      +ShimmerPlugin(
        Shimmer.Resonate(baseColor = Color.White, highlightColor = Color.LightGray),
      )
      +ZoomablePlugin(state = zoomableState)
      +PalettePlugin {
        palettes.incrementAndGet()
        log("palette #${palettes.get()}")
        onPaletteUpdated.invoke(it)
      }
      +CircularRevealPlugin(
        onFinishListener = {
          reveals.incrementAndGet()
          log("reveal finished #${reveals.get()}")
        },
      )
    }
    components += component
    log("compose component=${id(component)} url=${url.substringAfterLast('/')}")

    LandscapistImage(
      imageModel = { url },
      landscapist = landscapist,
      modifier = Modifier
        .aspectRatio(0.75f)
        .testTag("detail")
        .layout { measurable, constraints ->
          log("measure ${constraints.maxWidth}x${constraints.maxHeight}")
          val placeable = measurable.measure(constraints)
          layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        },
      component = component,
      onImageStateChanged = { state ->
        when (state) {
          is LandscapistImageState.Success -> {
            detailSuccesses.incrementAndGet()
            log(
              "detail Success #${detailSuccesses.get()} pixels=${sizeOf(state.data)} " +
                "original=${state.originalWidth}x${state.originalHeight} " +
                "source=${state.dataSource}",
            )
          }

          else -> log("detail ${state::class.simpleName}")
        }
      },
    )
  }

  private fun sizeOf(data: Any?): String = when (data) {
    is Bitmap -> "${data.width}x${data.height}"
    is androidx.compose.ui.graphics.ImageBitmap -> "${data.width}x${data.height}"
    is android.graphics.drawable.BitmapDrawable -> "${data.bitmap.width}x${data.bitmap.height}"
    else -> data?.let { it::class.simpleName }.orEmpty()
  }

  private fun id(value: Any?) = Integer.toHexString(System.identityHashCode(value))

  private fun newLandscapist(withDiskCache: Boolean = true): Landscapist {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val builder = Landscapist.builder(context)
    if (!withDiskCache) builder.noDiskCache()
    val landscapist = builder.build()
    runBlocking { landscapist.clearCaches() }
    return landscapist
  }

  private fun dump(label: String) {
    println("SELECTION $label ----------------------------------------")
    timeline.toList().forEachIndexed { index, line -> println("SELECTION $label [$index] $line") }
    println(
      "SELECTION $label totals: detailSuccesses=${detailSuccesses.get()} " +
        "reveals=${reveals.get()} palettes=${palettes.get()} " +
        "components=${components.size} distinct=${components.distinct().size} " +
        "fetch(/a.png)=${server.hitCount("/a.png")} fetch(/b.png)=${server.hitCount("/b.png")}",
    )
  }

  private fun linesIn(which: String, marker: String): List<String> = timeline.toList()
    .filter { it.startsWith("$which|") && it.contains(marker) }

  /** Composes the screen on poster A and waits until it and both thumbnails have settled. */
  private fun settleOnA(
    landscapist: Landscapist,
    selected: MutableState<String>,
    urls: List<String>,
  ) {
    compose.setContent { Screen(selected, urls, landscapist) }
    compose.waitUntil(15_000) {
      detailSuccesses.get() >= 1 &&
        timeline.toList().count { it.contains("thumb") && it.contains("Success") } >= 2
    }
    compose.waitForIdle()
    log("--- settled on A ---")
    phase.set("B")
  }

  /**
   * Everything the detail image does when a second poster is selected.
   *
   * The strip above it has already loaded that url at 50dp, so this is the case the report
   * describes: a poster shown at detail size for the first time, with its thumbnail in memory.
   */
  private fun assertOneLoadPerSelection(
    label: String,
    select: (MutableState<String>, String) -> Unit,
  ) {
    val landscapist = newLandscapist()
    val urlA = server.url("/a.png")
    val urlB = server.url("/b.png")
    val selected = mutableStateOf(urlA)

    settleOnA(landscapist, selected, listOf(urlA, urlB))
    select(selected, urlB)

    // Every chance to report a second success for B before anything is claimed about it.
    runCatching { compose.waitUntil(8_000) { linesIn("B", "detail Success").size >= 2 } }
    compose.waitForIdle()

    dump(label)

    val successes = linesIn("B", "detail Success")
    assertEquals(
      "$label: selecting a poster resolved the detail image more than once, so what was drawn " +
        "first is replaced in front of the user: $successes",
      1,
      successes.size,
    )
    assertTrue(
      "$label: the detail image was handed the strip's thumbnail to draw at detail size: " +
        "${successes.firstOrNull()}",
      successes.first().contains("pixels=960x1280"),
    )
    assertEquals(
      "$label: the palette ran once per image drawn, so it ran twice: ${linesIn("B", "palette")}",
      1,
      linesIn("B", "palette").size,
    )
    assertEquals("$label: /b.png was downloaded more than once", 1, server.hitCount("/b.png"))
  }

  /** The write the demo's tap performs, without the tap. */
  @Test
  fun selectingASecondPosterLoadsItOnce() =
    assertOneLoadPerSelection("programmatic") { selected, url ->
      compose.runOnUiThread { selected.value = url }
    }

  /** The same, driven by a real tap on the thumbnail, so nothing about the write is invented. */
  @Test
  fun tappingASecondThumbnailLoadsItOnce() =
    assertOneLoadPerSelection("tap") { _, _ ->
      compose.onNodeWithTag("thumb:b.png").performClick()
    }

  /**
   * The same selection on the path that owns its layout node.
   *
   * With no plugins and no content of its own, [LandscapistImage] measures, draws and runs its load
   * in one node rather than composing anything inside, and that node peeks the cache too. Nothing
   * about the demo screen is special here: the strip loads the urls small, the slot below shows one
   * of them large.
   */
  @Test
  fun selectingASecondPosterOnTheNodePathLoadsItOnce() {
    val landscapist = newLandscapist()
    val urlA = server.url("/a.png")
    val urlB = server.url("/b.png")
    val selected = mutableStateOf(urlA)

    compose.setContent { NodeScreen(selected, listOf(urlA, urlB), landscapist) }
    compose.waitUntil(15_000) {
      detailSuccesses.get() >= 1 &&
        timeline.toList().count { it.contains("thumb") && it.contains("Success") } >= 2
    }
    compose.waitForIdle()
    log("--- settled on A ---")
    phase.set("B")

    compose.runOnUiThread { selected.value = urlB }
    runCatching { compose.waitUntil(8_000) { linesIn("B", "detail Success").size >= 2 } }
    compose.waitForIdle()

    dump("node")

    val successes = linesIn("B", "detail Success")
    assertEquals(
      "node path: selecting a poster resolved the detail image more than once: $successes",
      1,
      successes.size,
    )
    assertTrue(
      "node path: the detail slot was handed the strip's thumbnail to draw: " +
        "${successes.firstOrNull()}",
      successes.first().contains("pixels=960x1280"),
    )
  }

  /** The demo's shape with every plugin removed, which is what puts an image on the node path. */
  @Composable
  private fun NodeScreen(
    selected: MutableState<String>,
    urls: List<String>,
    landscapist: Landscapist,
  ) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
      LazyRow {
        items(urls) { url ->
          LandscapistImage(
            imageModel = { url },
            landscapist = landscapist,
            modifier = Modifier
              .padding(8.dp)
              .size(50.dp),
            imageOptions = ImageOptions(contentScale = ContentScale.Crop),
            onImageStateChanged = {
              if (it is LandscapistImageState.Success) {
                log("thumb ${url.substringAfterLast('/')} Success ${sizeOf(it.data)}")
              }
            },
          )
        }
      }

      LandscapistImage(
        imageModel = { selected.value },
        landscapist = landscapist,
        modifier = Modifier.aspectRatio(0.75f),
        onImageStateChanged = { state ->
          when (state) {
            is LandscapistImageState.Success -> {
              detailSuccesses.incrementAndGet()
              log(
                "detail Success #${detailSuccesses.get()} pixels=${sizeOf(state.data)} " +
                  "original=${state.originalWidth}x${state.originalHeight} " +
                  "source=${state.dataSource}",
              )
            }

            else -> log("detail ${state::class.simpleName}")
          }
        },
      )
    }
  }

  /**
   * Whether the circular reveal replays, which is what "flash" means as opposed to "reload".
   *
   * The second load is held open long enough for a 350ms reveal to finish twice, so an interrupted
   * first reveal cannot hide inside the second. The disk cache is off so the detail image's own
   * load has to go back to the server rather than resolving from disk before the reveal is over.
   */
  @Test
  fun theRevealDoesNotReplayWhenAPosterIsSelected() {
    val landscapist = newLandscapist(withDiskCache = false)
    val urlA = server.url("/a.png")
    val urlB = server.url("/b.png")
    val selected = mutableStateOf(urlA)

    settleOnA(landscapist, selected, listOf(urlA, urlB))

    // From here /b.png answers slowly, so whatever is drawn in the meantime is drawn on its own.
    server.serve("/b.png", poster(android.graphics.Color.GREEN), delayMs = 2_000)

    compose.runOnUiThread { selected.value = urlB }
    runCatching { compose.waitUntil(15_000) { linesIn("B", "detail Success").size >= 2 } }
    compose.waitForIdle()

    dump("gated")

    val revealsInB = linesIn("B", "reveal finished")
    assertEquals(
      "the reveal animation ran once per image the detail slot was handed, so selecting a " +
        "poster replays it: $revealsInB",
      1,
      revealsInB.size,
    )
  }
}
