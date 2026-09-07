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
package com.skydoves.landscapist

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the state machine every backend runs through [ImageLoad], with a stand in for the
 * backend's own request flow.
 */
@OptIn(ExperimentalTestApi::class)
class ImageLoadTest {

  private val success = ImageLoadState.Success(data = "bitmap", dataSource = DataSource.MEMORY)

  /** Drives [ImageLoad] with [upstream] and records every state it renders, without repeats. */
  private class Recorder {
    val upstream = MutableSharedFlow<ImageLoadState>(extraBufferCapacity = 16)
    val rendered = mutableListOf<ImageLoadState>()

    val latest: ImageLoadState?
      get() = rendered.lastOrNull()

    fun record(state: ImageLoadState) {
      if (rendered.lastOrNull() != state) rendered += state
    }
  }

  private fun ComposeUiTest.render(
    recorder: Recorder,
    request: suspend () -> Flow<ImageLoadState> = { recorder.upstream },
  ) {
    setContent {
      ImageLoad(
        recomposeKey = "https://example.com/image.jpg",
        executeImageRequest = request,
        modifier = Modifier.size(100.dp),
        imageOptions = ImageOptions(),
      ) { state ->
        recorder.record(state)
      }
    }
    // The collector starts in a LaunchedEffect, so nothing can be emitted until it subscribes.
    waitUntil { recorder.upstream.subscriptionCount.value > 0 }
  }

  private fun ComposeUiTest.emit(recorder: Recorder, state: ImageLoadState) {
    assertTrue(recorder.upstream.tryEmit(state), "the upstream buffer is full")
    waitForIdle()
  }

  @Test
  fun `states reach the content in order`() = runComposeUiTest {
    val recorder = Recorder()
    render(recorder)

    emit(recorder, ImageLoadState.Loading)
    emit(recorder, success)

    assertEquals(
      listOf(ImageLoadState.None, ImageLoadState.Loading, success),
      recorder.rendered,
    )
  }

  @Test
  fun `a loading state after a success does not blank the image`() = runComposeUiTest {
    val recorder = Recorder()
    render(recorder)

    emit(recorder, ImageLoadState.Loading)
    emit(recorder, success)
    // A backend restarting its request, as Glide does when a target is reused.
    emit(recorder, ImageLoadState.Loading)

    assertEquals(success, recorder.latest, "the resolved image must stay on screen")
  }

  @Test
  fun `a released bitmap does clear the image`() = runComposeUiTest {
    val recorder = Recorder()
    render(recorder)

    emit(recorder, success)
    // Glide reports this from onLoadCleared: the bitmap is gone and must stop being drawn.
    emit(recorder, ImageLoadState.None)

    assertEquals(ImageLoadState.None, recorder.latest)
  }

  @Test
  fun `a failure keeps the cause`() = runComposeUiTest {
    val recorder = Recorder()
    val cause = IllegalStateException("network is down")
    render(recorder)

    emit(recorder, ImageLoadState.Failure(data = null, reason = cause))

    val failure = assertIs<ImageLoadState.Failure>(recorder.latest)
    assertEquals(cause, failure.reason)
  }

  @Test
  fun `a throwing request surfaces its cause`() = runComposeUiTest {
    val recorder = Recorder()
    val cause = IllegalStateException("could not build the request")

    setContent {
      ImageLoad(
        recomposeKey = "https://example.com/image.jpg",
        executeImageRequest = { throw cause },
        modifier = Modifier.size(100.dp),
        imageOptions = ImageOptions(),
      ) { state ->
        recorder.record(state)
      }
    }
    waitForIdle()

    val failure = assertIs<ImageLoadState.Failure>(recorder.latest)
    assertEquals(cause, failure.reason, "the cause used to be dropped on the floor")
  }
}
