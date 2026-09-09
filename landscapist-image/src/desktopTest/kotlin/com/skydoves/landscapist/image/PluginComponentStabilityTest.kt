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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.skydoves.landscapist.animation.circular.CircularRevealPlugin
import com.skydoves.landscapist.components.ImagePluginComponent
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.palette.PalettePlugin
import com.skydoves.landscapist.placeholder.blurhash.BlurHashPlugin
import com.skydoves.landscapist.placeholder.placeholder.PlaceholderPlugin
import com.skydoves.landscapist.placeholder.progressive.ProgressiveLoadingPlugin
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import com.skydoves.landscapist.placeholder.thumbhash.ThumbHashPlugin
import com.skydoves.landscapist.placeholder.thumbnail.ThumbnailPlugin
import com.skydoves.landscapist.plugins.ImagePlugin
import com.skydoves.landscapist.zoomable.ZoomablePlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whether an image installed with a plugin can still skip.
 *
 * `rememberImageComponent` keys its remember on the plugins, so a plugin the runtime cannot compare
 * is a new value on every composition and the component is rebuilt around it. Images take the
 * component as a stable parameter, so that is an image recomposed every time anything above it is,
 * for a plugin set that never changed. Every plugin this library ships has to be comparable for
 * that not to happen.
 */
@OptIn(ExperimentalTestApi::class)
class PluginComponentStabilityTest {

  private var seen = mutableListOf<ImagePluginComponent>()

  /** Stands in for an image: skippable, and takes the component the way the real ones do. */
  @Composable
  private fun Downstream(component: ImagePluginComponent) {
    seen += component
  }

  /** Recomposes the caller once for a reason the plugins had no part in. */
  private fun recomposeOnceWith(plugin: () -> ImagePlugin) {
    seen = mutableListOf()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        val unrelated = tick
        check(unrelated >= 0)
        Downstream(rememberImageComponent { +plugin() })
      }
      waitForIdle()
      tick = 1
      waitForIdle()
    }
  }

  /**
   * The same, with the plugins written where a caller writes them.
   *
   * A plugin built inside a plain lambda handed to the harness is built outside any composable, so
   * the compiler cannot memoise a listener passed to it and the measurement would be the harness's
   * rather than the library's.
   */
  private fun recomposeOnceComposing(block: @Composable ImagePluginComponent.() -> Unit) {
    seen = mutableListOf()
    runComposeUiTest {
      var tick by mutableStateOf(0)
      setContent {
        val unrelated = tick
        check(unrelated >= 0)
        Downstream(rememberImageComponent(block))
      }
      waitForIdle()
      tick = 1
      waitForIdle()
    }
  }

  private fun assertSkips(name: String, plugin: () -> ImagePlugin) {
    recomposeOnceWith(plugin)
    assertEquals(
      1,
      seen.size,
      "$name made the image recompose ${seen.size} times for a change that was not its own",
    )
  }

  @Test
  fun `every plugin this library ships lets the image skip`() {
    assertSkips("CrossfadePlugin") { CrossfadePlugin(duration = 300) }
    assertSkips("ShimmerPlugin") { ShimmerPlugin() }
    assertSkips("ThumbnailPlugin") { ThumbnailPlugin() }
    assertSkips("ProgressiveLoadingPlugin") { ProgressiveLoadingPlugin() }
    assertSkips("ThumbHashPlugin") { ThumbHashPlugin(byteArrayOf(1, 2, 3, 4)) }
    assertSkips("BlurHashPlugin") { BlurHashPlugin(blurHash = "LEHV6nWB2yk8pyo0adR*") }
    assertSkips("CircularRevealPlugin") { CircularRevealPlugin() }
    assertSkips("ZoomablePlugin") { ZoomablePlugin() }
    assertSkips("PlaceholderPlugin.Loading") { PlaceholderPlugin.Loading(source = null) }
    assertSkips("PlaceholderPlugin.Failure") { PlaceholderPlugin.Failure(source = null) }
    assertSkips("PalettePlugin") { PalettePlugin() }
  }

  @Test
  fun `a plugin carrying a listener the docs demonstrate lets the image skip`() {
    // Every listener this library takes is a fun interface, so a plugin holding one compares it by
    // identity, and whether the lambda a caller writes survives as one instance is the compiler's
    // to decide. This is the shape both the palette and the reveal are documented with: a lambda
    // over remembered state, which is stable, so the conversion is memoised.
    val palette = mutableStateOf<Int?>(null)
    recomposeOnceComposing { +PalettePlugin { palette.value = it.hashCode() } }
    assertEquals(
      1,
      seen.size,
      "the palette listener the docs demonstrate cost the image its skipping, ${seen.size} times",
    )

    val finished = mutableStateOf(false)
    recomposeOnceComposing { +CircularRevealPlugin(onFinishListener = { finished.value = true }) }
    assertEquals(
      1,
      seen.size,
      "the reveal listener the docs demonstrate cost the image its skipping, ${seen.size} times",
    )
  }

  @Test
  fun `a listener closing over a mutable local costs the image its skipping`() {
    // Not a supported way to write one, pinned so the cost is visible. A local captured this way
    // lives in a box the runtime cannot compare, so the plugin is a new value every composition.
    var counted = 0
    recomposeOnceComposing { +CircularRevealPlugin(onFinishListener = { counted++ }) }
    assertTrue(
      seen.size > 1,
      "a listener over a mutable local skipped, so the guard above proves less than it says",
    )
  }

  @Test
  fun `a plugin the runtime cannot compare is the case this guards`() {
    // Not a supported way to write a plugin, pinned so the cost of writing one is visible.
    class Incomparable : ImagePlugin.PainterPlugin {
      @Composable
      override fun compose(
        imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
        painter: androidx.compose.ui.graphics.painter.Painter,
      ): androidx.compose.ui.graphics.painter.Painter = painter
    }

    recomposeOnceWith { Incomparable() }
    assertTrue(
      seen.size > 1,
      "the guard above proves nothing if an incomparable plugin also skips",
    )
  }

  @Test
  fun `a reconfigured plugin still reaches the image`() {
    seen = mutableListOf()
    runComposeUiTest {
      var size by mutableStateOf(15)
      setContent {
        val current = size
        Downstream(rememberImageComponent { +ThumbnailPlugin(IntSizeOf(current)) })
      }
      waitForIdle()
      size = 40
      waitForIdle()
    }
    assertEquals(
      ThumbnailPlugin(IntSizeOf(40)),
      seen.last().plugins.single(),
      "the image kept the size the thumbnail was first built with",
    )
  }

  private fun IntSizeOf(side: Int) = androidx.compose.ui.unit.IntSize(side, side)
}
