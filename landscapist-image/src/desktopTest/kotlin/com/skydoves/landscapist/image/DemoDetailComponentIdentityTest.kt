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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.ImagePluginComponent
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import com.skydoves.landscapist.zoomable.ZoomableState
import com.skydoves.landscapist.zoomable.rememberZoomableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Stands in for `com.skydoves.landscapist.palette.PaletteLoadedListener`, which the desktop test
 * source set has no dependency on. Same shape: a `fun interface` with a single method, so the
 * trailing lambda at the call site is a SAM conversion.
 */
fun interface StandInPaletteLoadedListener {
  fun onPaletteLoaded(palette: Int)
}

/**
 * Stands in for `com.skydoves.landscapist.palette.PalettePlugin`: an `@Immutable data class` whose
 * last constructor property is a nullable `fun interface`, so `StandInPalettePlugin { ... }` is the
 * same SAM-converted trailing lambda as `PalettePlugin { ... }` and lands in the same position of
 * the generated `equals`.
 */
@Immutable
data class StandInPalettePlugin(
  private val imageModel: Any? = null,
  private val useCache: Boolean = true,
  private val interceptor: Any? = null,
  private val paletteLoadedListener: StandInPaletteLoadedListener? = null,
) : ImagePlugin.SuccessStatePlugin {

  @Composable
  override fun compose(
    modifier: Modifier,
    imageModel: Any?,
    imageOptions: ImageOptions,
    imageBitmap: ImageBitmap?,
  ): ImagePlugin = this
}

/**
 * When the component the demo's detail screen builds keeps its identity, and when it does not.
 *
 * `rememberImageComponent` keys its remember on the plugin list, so a component that is replaced is
 * an image that cannot skip. This walks the demo's own plugin set, one plugin at a time and then
 * all four together, through the three things the screen actually does: recompose for an unrelated
 * reason, change the model when a poster is tapped, and take a palette back from the plugin.
 *
 * Two things are counted for each. [built] is recorded in the caller, which never skips, so its
 * size says how many times the component was rebuilt and its identity count says how many distinct
 * components came out. [reached] is recorded inside [Downstream], which is skippable and takes the
 * component the way an image does, so its size is the number of times an image would have
 * recomposed.
 */
@OptIn(ExperimentalTestApi::class)
class DemoDetailComponentIdentityTest {

  /** Every component `rememberImageComponent` handed back, one per composition of the caller. */
  private var built = mutableListOf<ImagePluginComponent>()

  /** Every component that got past the skippable boundary, one per recomposition of the image. */
  private var reached = mutableListOf<ImagePluginComponent>()

  private fun reset() {
    built = mutableListOf()
    reached = mutableListOf()
  }

  /** Stands in for an image: skippable, and takes the component the way the real ones do. */
  @Composable
  private fun Downstream(component: ImagePluginComponent) {
    reached += component
  }

  /** How many distinct components came out, counted as identity switches. */
  private fun List<ImagePluginComponent>.identities(): Int {
    var count = 0
    var last: ImagePluginComponent? = null
    for (component in this) {
      if (last !== component) {
        count++
        last = component
      }
    }
    return count
  }

  /** Names the first plugin that stopped comparing equal, so a failure says which one it was. */
  private fun List<ImagePluginComponent>.firstDifference(): String {
    for (index in 1 until size) {
      val before = this[index - 1].plugins
      val after = this[index].plugins
      if (before == after) continue
      if (before.size != after.size) return "the plugin count went ${before.size} -> ${after.size}"
      for (i in before.indices) {
        if (before[i] != after[i]) {
          return "${before[i]::class.simpleName} at index $i compared unequal to itself"
        }
      }
    }
    return "no plugin compared unequal"
  }

  /** The component was rebuilt more than once, came out the same every time, and the image skipped. */
  private fun assertKept(name: String) {
    println(
      "MEASURED | $name | builds=${built.size} distinctComponents=${built.identities()} " +
        "imageCompositions=${reached.size}",
    )
    assertTrue(
      built.size > 1,
      "$name only built the component ${built.size} time(s), so nothing was measured",
    )
    assertEquals(
      1,
      built.identities(),
      "$name replaced the component ${built.identities()} times over ${built.size} builds: " +
        built.firstDifference(),
    )
    assertEquals(
      1,
      reached.size,
      "$name recomposed the image ${reached.size} times over ${built.size} builds",
    )
  }

  // ---------------------------------------------------------------------------------------------
  // 1. the SAM-converted listener
  // ---------------------------------------------------------------------------------------------

  /**
   * The demo's `+PalettePlugin { onPaletteUpdated.invoke(it) }`: a SAM-converted trailing lambda
   * closing over a function-typed parameter of the enclosing composable.
   */
  @Composable
  private fun SamOverParameter(tick: Int, onPaletteUpdated: (Int) -> Unit) {
    check(tick >= 0)
    val component = rememberImageComponent { +StandInPalettePlugin { onPaletteUpdated.invoke(it) } }
    built += component
    Downstream(component)
  }

  @Test
  fun `a SAM-converted listener over a parameter keeps the component`() {
    reset()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        val sink = remember { { _: Int -> } }
        SamOverParameter(tick = tick, onPaletteUpdated = sink)
      }
      waitForIdle()
      tick = 1
      waitForIdle()
      tick = 2
      waitForIdle()
    }
    assertKept("a SAM-converted listener over a parameter")
  }

  /**
   * The same shape with a `fun interface` and a plugin that both come from another module, so the
   * answer above is not an artefact of the listener being declared next to the test.
   */
  @Composable
  private fun SamOverParameterCrossModule(tick: Int, onFinished: () -> Unit) {
    check(tick >= 0)
    val component = rememberImageComponent {
      +CircularRevealPlugin(onFinishListener = { onFinished() })
    }
    built += component
    Downstream(component)
  }

  @Test
  fun `a SAM-converted listener on a real library plugin keeps the component`() {
    reset()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        val sink = remember { { } }
        SamOverParameterCrossModule(tick = tick, onFinished = sink)
      }
      waitForIdle()
      tick = 1
      waitForIdle()
      tick = 2
      waitForIdle()
    }
    assertKept("a cross-module SAM-converted listener over a parameter")
  }

  /**
   * The boundary, so the two results above are not read as "SAM conversions are always fine". A
   * listener closing over a mutable local is captured through a box the runtime cannot compare, so
   * it is rebuilt on every composition and the component goes with it.
   */
  @Composable
  private fun SamOverMutableLocal(tick: Int) {
    check(tick >= 0)
    var finished = 0
    val component = rememberImageComponent {
      +CircularRevealPlugin(onFinishListener = { finished++ })
    }
    built += component
    Downstream(component)
  }

  @Test
  fun `a SAM-converted listener over a mutable local does replace the component`() {
    reset()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent { SamOverMutableLocal(tick = tick) }
      waitForIdle()
      tick = 1
      waitForIdle()
    }
    println(
      "MEASURED | a SAM-converted listener over a mutable local | builds=${built.size} " +
        "distinctComponents=${built.identities()} imageCompositions=${reached.size}",
    )
    assertTrue(built.size > 1, "only built ${built.size} time(s), so nothing was measured")
    assertTrue(
      built.identities() > 1,
      "the guard proves nothing if an uncomparable listener also keeps the component",
    )
    assertTrue(
      reached.size > 1,
      "the image skipped even though the component was replaced, so the probe is not skippable",
    )
  }

  // ---------------------------------------------------------------------------------------------
  // 2. the zoomable state across a model change
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `rememberZoomableState hands back the same state when only resetKey changes`() {
    val states = mutableListOf<ZoomableState>()
    runComposeUiTest {
      var model by mutableStateOf("first.png")
      setContent {
        // ZoomableConfig is constructed inline here, exactly as the demo does it.
        states += rememberZoomableState(
          config = ZoomableConfig(
            enableSubSampling = true,
            maxZoom = 40f,
            doubleTapZoom = 20f,
          ),
          resetKey = model,
        )
      }
      waitForIdle()
      model = "second.png"
      waitForIdle()
      model = "third.png"
      waitForIdle()
    }
    println(
      "MEASURED | rememberZoomableState across resetKey changes | calls=${states.size} " +
        "distinctStates=${states.distinct().size} allSame=${states.all { it === states.first() }}",
    )
    assertTrue(states.size > 1, "the composable only ran once, so nothing was measured")
    for (state in states) {
      assertSame(states.first(), state, "rememberZoomableState handed back a new state")
    }
  }

  @Test
  fun `a ZoomablePlugin over that state keeps the component when the model changes`() {
    reset()
    runComposeUiTest {
      var model by mutableStateOf("first.png")
      setContent {
        val current = model
        val zoomableState = rememberZoomableState(
          config = ZoomableConfig(
            enableSubSampling = true,
            maxZoom = 40f,
            doubleTapZoom = 20f,
          ),
          resetKey = current,
        )
        val component = rememberImageComponent { +ZoomablePlugin(state = zoomableState) }
        built += component
        Downstream(component)
      }
      waitForIdle()
      model = "second.png"
      waitForIdle()
      model = "third.png"
      waitForIdle()
    }
    assertKept("the zoomable plugin, when the model changed")
  }

  // ---------------------------------------------------------------------------------------------
  // 3. the shimmer that reads isSystemInDarkTheme
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `a shimmer built from isSystemInDarkTheme keeps the component`() {
    reset()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        val unrelated = tick
        check(unrelated >= 0)
        val component = rememberImageComponent {
          +ShimmerPlugin(
            Shimmer.Resonate(
              baseColor = if (isSystemInDarkTheme()) Color(0xFF12131A) else Color.White,
              highlightColor = Color.LightGray,
            ),
          )
        }
        built += component
        Downstream(component)
      }
      waitForIdle()
      tick = 1
      waitForIdle()
      tick = 2
      waitForIdle()
    }
    assertKept("the shimmer over isSystemInDarkTheme")
  }

  // ---------------------------------------------------------------------------------------------
  // 4. the call-site lambda over a remembered state delegate
  // ---------------------------------------------------------------------------------------------

  private var callbacks = mutableListOf<(Int) -> Unit>()

  @Test
  fun `the call-site palette lambda over a state delegate is memoized`() {
    callbacks = mutableListOf()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        // `var palette by rememberPaletteState()` in the demo. rememberPaletteState is
        // remember(value) { mutableStateOf(...) } over a null value, so this is the same state.
        var palette by remember { mutableStateOf<Int?>(null) }
        val unrelated = tick
        check(unrelated >= 0)
        val onPaletteUpdated: (Int) -> Unit = { palette = it }
        callbacks += onPaletteUpdated
        // `palette?.let { PosterInformation(poster, it) }`: the sibling that makes writing a
        // palette recompose this whole composable.
        palette?.let { check(it >= 0) }
      }
      waitForIdle()
      tick = 1
      waitForIdle()
      // What the plugin does when a palette is produced.
      callbacks.last().invoke(7)
      waitForIdle()
      callbacks.last().invoke(9)
      waitForIdle()
    }
    println(
      "MEASURED | call-site palette lambda | records=${callbacks.size} " +
        "allSame=${callbacks.all { it === callbacks.first() }}",
    )
    assertTrue(
      callbacks.size > 2,
      "the lambda was only recorded ${callbacks.size} time(s), so nothing was measured",
    )
    for (callback in callbacks) {
      assertSame(callbacks.first(), callback, "the call-site palette lambda was rebuilt")
    }
  }

  // ---------------------------------------------------------------------------------------------
  // the whole demo set
  // ---------------------------------------------------------------------------------------------

  /** The demo's `SelectedPoster`, as close as the desktop source set can get to it. */
  @Composable
  private fun SelectedPosterLike(tick: Int, model: String, onPaletteUpdated: (Int) -> Unit) {
    check(tick >= 0)
    val zoomableState = rememberZoomableState(
      config = ZoomableConfig(
        enableSubSampling = true,
        maxZoom = 40f,
        doubleTapZoom = 20f,
      ),
      resetKey = model,
    )

    val component = rememberImageComponent {
      +ShimmerPlugin(
        Shimmer.Resonate(
          baseColor = if (isSystemInDarkTheme()) Color(0xFF12131A) else Color.White,
          highlightColor = Color.LightGray,
        ),
      )

      +ZoomablePlugin(state = zoomableState)

      +StandInPalettePlugin { onPaletteUpdated.invoke(it) }
      +CircularRevealPlugin()
    }
    built += component
    Downstream(component)
  }

  @Test
  fun `the demo plugin set keeps the component through an unrelated recomposition`() {
    reset()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        var palette by remember { mutableStateOf<Int?>(null) }
        SelectedPosterLike(tick = tick, model = "first.png", onPaletteUpdated = { palette = it })
        palette?.let { check(it >= 0) }
      }
      waitForIdle()
      tick = 1
      waitForIdle()
      tick = 2
      waitForIdle()
    }
    assertKept("the demo set, over an unrelated recomposition")
  }

  @Test
  fun `the demo plugin set keeps the component when the poster changes`() {
    reset()
    runComposeUiTest {
      var model by mutableStateOf("first.png")
      setContent {
        var palette by remember { mutableStateOf<Int?>(null) }
        SelectedPosterLike(tick = 0, model = model, onPaletteUpdated = { palette = it })
        palette?.let { check(it >= 0) }
      }
      waitForIdle()
      model = "second.png"
      waitForIdle()
      model = "third.png"
      waitForIdle()
    }
    assertKept("the demo set, when the poster changed")
  }

  @Test
  fun `the demo plugin set keeps the component when a palette arrives`() {
    // The loop worth ruling out: the palette listener writes state the screen reads, so every
    // palette recomposes the screen. A listener rebuilt on that recomposition replaces the
    // component, recomposes the image, produces another palette, and the screen never settles.
    reset()
    val sinks = mutableListOf<(Int) -> Unit>()
    runComposeUiTest {
      setContent {
        var palette by remember { mutableStateOf<Int?>(null) }
        val onPaletteUpdated: (Int) -> Unit = { palette = it }
        sinks += onPaletteUpdated
        SelectedPosterLike(tick = 0, model = "first.png", onPaletteUpdated = onPaletteUpdated)
        palette?.let { check(it >= 0) }
      }
      waitForIdle()
      sinks.last().invoke(1)
      waitForIdle()
      sinks.last().invoke(2)
      waitForIdle()
    }
    println(
      "MEASURED | the demo set, when a palette arrived | screenCompositions=${sinks.size} " +
        "builds=${built.size} distinctComponents=${built.identities()} " +
        "imageCompositions=${reached.size}",
    )
    assertTrue(
      sinks.size > 1,
      "the screen never recomposed when a palette arrived, so nothing was measured",
    )
    assertEquals(
      1,
      built.identities(),
      "a palette arriving replaced the component ${built.identities()} times over " +
        "${built.size} builds: ${built.firstDifference()}",
    )
    assertEquals(
      1,
      reached.size,
      "a palette arriving recomposed the image ${reached.size} times",
    )
    // Stronger than the component surviving: the detail composable did not run at all, because
    // every parameter it takes, the memoized palette lambda included, compared equal.
    assertEquals(
      1,
      built.size,
      "the detail composable ran ${built.size} times when a palette arrived",
    )
  }
}
