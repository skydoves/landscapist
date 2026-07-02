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
package com.skydoves.landscapist.zoomable.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.zoomable.ZoomableConfig
import com.skydoves.landscapist.zoomable.ZoomableState
import com.skydoves.landscapist.zoomable.rememberZoomableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavior tests for [zoomGestures], covering the tap-callback fix for
 * https://github.com/skydoves/landscapist/issues/863 while guarding the existing gestures.
 */
@OptIn(ExperimentalTestApi::class)
class ZoomGesturesTest {

  /** Regression: with the default config a double-tap still zooms the content in. */
  @Test
  fun doubleTap_zoomsIn_withDefaultConfig() = runComposeUiTest {
    lateinit var state: ZoomableState
    setContent {
      state = rememberZoomableState()
      Box(
        modifier = Modifier
          .size(200.dp)
          .testTag(TARGET)
          .zoomGestures(state = state, config = ZoomableConfig()),
      )
    }

    assertFalse(state.isZoomed)
    onNodeWithTag(TARGET).performTouchInput { doubleClick() }
    waitUntil(timeoutMillis = TIMEOUT) { state.isZoomed }

    assertTrue(state.isZoomed)
  }

  /** Regression: a second double-tap while zoomed resets the zoom. */
  @Test
  fun doubleTap_whenZoomed_resetsZoom() = runComposeUiTest {
    lateinit var state: ZoomableState
    setContent {
      state = rememberZoomableState()
      Box(
        modifier = Modifier
          .size(200.dp)
          .testTag(TARGET)
          .zoomGestures(state = state, config = ZoomableConfig()),
      )
    }

    onNodeWithTag(TARGET).performTouchInput { doubleClick() }
    waitUntil(timeoutMillis = TIMEOUT) { state.isZoomed }

    onNodeWithTag(TARGET).performTouchInput { doubleClick() }
    waitUntil(timeoutMillis = TIMEOUT) { !state.isZoomed }

    assertFalse(state.isZoomed)
  }

  /** New behavior (#863): a single tap invokes the onTap callback. */
  @Test
  fun singleTap_invokesOnTapCallback() = runComposeUiTest {
    var taps = 0
    setContent {
      val state = rememberZoomableState()
      Box(
        modifier = Modifier
          .size(200.dp)
          .testTag(TARGET)
          .zoomGestures(state = state, config = ZoomableConfig(), onTap = { taps++ }),
      )
    }

    onNodeWithTag(TARGET).performTouchInput { click() }
    waitUntil(timeoutMillis = TIMEOUT) { taps == 1 }

    assertEquals(1, taps)
  }

  /**
   * New behavior (#863): onTap is the supported way to react to taps. A parent clickable does not
   * fire while tap handling is active (the detector consumes the down), so onTap replaces it.
   */
  @Test
  fun singleTap_invokesOnTap_notParentClickable() = runComposeUiTest {
    var taps = 0
    var parentClicks = 0
    setContent {
      val state = rememberZoomableState()
      Box(
        modifier = Modifier
          .size(200.dp)
          .testTag(PARENT)
          .clickable { parentClicks++ },
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .zoomGestures(state = state, config = ZoomableConfig(), onTap = { taps++ }),
        )
      }
    }

    onNodeWithTag(PARENT).performTouchInput { click() }
    waitUntil(timeoutMillis = TIMEOUT) { taps == 1 }

    assertEquals(1, taps)
    assertEquals(0, parentClicks)
  }

  /**
   * Regression: when both double-tap zoom and onTap are disabled, no tap detector is installed, so
   * a single tap stays unconsumed and reaches the parent's clickable.
   */
  @Test
  fun singleTap_reachesParent_whenTapHandlingDisabled() = runComposeUiTest {
    var parentClicks = 0
    val config = ZoomableConfig(enableDoubleTapZoom = false)
    setContent {
      val state = rememberZoomableState(config = config)
      Box(
        modifier = Modifier
          .size(200.dp)
          .testTag(PARENT)
          .clickable { parentClicks++ },
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .zoomGestures(state = state, config = config),
        )
      }
    }

    onNodeWithTag(PARENT).performTouchInput { click() }
    waitUntil(timeoutMillis = TIMEOUT) { parentClicks == 1 }

    assertEquals(1, parentClicks)
  }

  private companion object {
    const val TARGET = "target"
    const val PARENT = "parent"
    const val TIMEOUT = 5_000L
  }
}
