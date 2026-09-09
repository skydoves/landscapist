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
package com.skydoves.landscapist.animation.circular

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter

/**
 * This is an extension of the [Painter] for animating a clipping circle to reveal an image.
 * The animation has two states [CircularRevealState.None], [CircularRevealState.Finished].
 *
 * @param durationMs milli-second times from start to finish animation.
 */
@Composable
internal fun Painter.rememberCircularRevealPainter(
  durationMs: Int,
  onFinishListener: CircularRevealFinishListener? = null,
): Painter {
  // Defines a transition of `CircularRevealState`, and updates the transition when the provided state changes.
  val transitionState = remember {
    MutableTransitionState(CircularRevealState.None).apply {
      targetState = CircularRevealState.Finished
    }
  }

  // Our actual transition, which reads our transitionState
  val transition = rememberTransition(transitionState, label = null)

  val radius: Float by transition.animateFloat(
    transitionSpec = { tween(durationMillis = durationMs) },
    label = "FloatAnimation",
  ) { state ->
    when (state) {
      CircularRevealState.None -> 0f
      CircularRevealState.Finished -> 1f
    }
  }

  // Reported when the transition settles rather than from inside the lambda above. That lambda
  // says what the radius is for a state, and Compose asks it for every state it needs a value
  // for, on every recomposition and on every frame, so a listener called from there fired more
  // than twenty times for one reveal.
  val currentListener by rememberUpdatedState(onFinishListener)
  val finished = transitionState.isIdle &&
    transitionState.currentState == CircularRevealState.Finished
  LaunchedEffect(finished) {
    if (finished) currentListener?.onFinish()
  }

  return remember(this) {
    CircularRevealPainter(painter = this)
  }.also {
    it.radius = radius
  }
}

/**
 * CircularRevealState is state of transition for clipping circle to reveal an image
 * depending on its state.
 */
internal enum class CircularRevealState {
  /** animation is not started. */
  None,

  /** animation is finished. */
  Finished,
}
